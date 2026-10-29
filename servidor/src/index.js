require('dotenv').config();
const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const { pool } = require('./db');

const app = express();
app.set('trust proxy', true); // detrás del proxy de Render, req.ip trae la IP real del celular
app.use(cors());
app.use(express.json({ limit: '100kb' }));

// ---------- Pruebas de red ----------

// Respuesta mínima: la app mide el tiempo de ida y vuelta
app.get('/ping', (req, res) => {
  res.set('Cache-Control', 'no-store');
  res.json({ ok: true });
});

// Bloque de bytes aleatorios reutilizado: no se comprime, así que el tráfico real es el medido
const BLOQUE = crypto.randomBytes(64 * 1024);

// Flujo de datos aleatorios durante ?segundos= (por defecto 12, máximo 30);
// la app corta la conexión cuando termina de medir
app.get('/descarga', (req, res) => {
  const segundos = Math.min(Math.max(Number(req.query.segundos) || 12, 1), 30);
  const fin = Date.now() + segundos * 1000;
  res.set({ 'Content-Type': 'application/octet-stream', 'Cache-Control': 'no-store' });

  let cerrado = false;
  res.on('close', () => { cerrado = true; });

  const enviar = () => {
    while (!cerrado && Date.now() < fin) {
      if (!res.write(BLOQUE)) {          // buffer lleno: esperar a que se vacíe
        res.once('drain', enviar);
        return;
      }
    }
    if (!cerrado) res.end();
  };
  enviar();
});

const DESCARTE_INICIAL_SEG = 1; // arranque lento de TCP: no se cuenta el primer segundo

// Recibe y descarta los datos de la prueba de subida. La velocidad se mide acá, con los bytes
// que efectivamente llegaron (el celular solo sabe cuántos entregó a su conexión, no cuántos llegaron)
app.post('/subida', (req, res) => {
  let bytes = 0;            // todo lo recibido
  let bytesMedidos = 0;     // lo recibido después del primer segundo
  let inicio = null;        // llegada del primer dato
  let inicioMedicion = null;

  req.on('data', (chunk) => {
    const ahora = process.hrtime.bigint();
    bytes += chunk.length;
    if (inicio === null) inicio = ahora;
    if (inicioMedicion === null) {
      if (Number(ahora - inicio) / 1e9 >= DESCARTE_INICIAL_SEG) inicioMedicion = ahora;
    } else {
      bytesMedidos += chunk.length;
    }
  });
  req.on('end', () => {
    const segundos = inicioMedicion === null ? 0 : Number(process.hrtime.bigint() - inicioMedicion) / 1e9;
    const mbps = segundos > 0 ? (bytesMedidos * 8) / (segundos * 1e6) : 0;
    res.json({ bytes_recibidos: bytes, bytes_medidos: bytesMedidos, segundos_medidos: segundos, mbps });
  });
  req.on('error', () => res.status(400).json({ error: 'Subida interrumpida' }));
});

// ---------- Mediciones ----------

// Proveedor de Internet a partir de la IP pública (Android no lo informa en Wi-Fi)
async function proveedorPorIp(ip) {
  const limpia = (ip || '').replace('::ffff:', '');
  if (!limpia || limpia === '::1' || /^(127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(limpia)) return null;
  try {
    const r = await fetch(`http://ip-api.com/json/${limpia}?fields=status,isp`, {
      signal: AbortSignal.timeout(3000),
    });
    const d = await r.json();
    return d.status === 'success' ? d.isp : null;
  } catch {
    return null;
  }
}

const OBLIGATORIOS_NUM = ['descarga_mbps', 'subida_mbps', 'ping_ms', 'jitter_ms'];
const OPCIONALES_NUM = ['latitud', 'longitud', 'rssi_dbm', 'enlace_mbps', 'distancia_m', 'rsrp_dbm', 'snr_db'];
const OPCIONALES_TXT = ['ssid', 'banda', 'proveedor', 'operador', 'red_movil'];

function validar(b) {
  const errores = [];
  if (!b || typeof b !== 'object') return ['El cuerpo debe ser un JSON'];
  if (!b.marca) errores.push('marca es obligatoria');
  if (!b.modelo) errores.push('modelo es obligatorio');
  if (!['WIFI', 'MOVIL'].includes(b.tipo_conexion)) errores.push('tipo_conexion debe ser WIFI o MOVIL');
  if (!b.fecha_hora || Number.isNaN(Date.parse(b.fecha_hora))) errores.push('fecha_hora debe estar en formato ISO 8601');
  for (const c of OBLIGATORIOS_NUM) {
    if (typeof b[c] !== 'number' || !Number.isFinite(b[c]) || b[c] < 0) errores.push(`${c} es obligatorio y debe ser un número >= 0`);
  }
  for (const c of OPCIONALES_NUM) {
    if (b[c] != null && (typeof b[c] !== 'number' || !Number.isFinite(b[c]))) errores.push(`${c} debe ser un número`);
  }
  if (b.latitud != null && Math.abs(b.latitud) > 90) errores.push('latitud fuera de rango');
  if (b.longitud != null && Math.abs(b.longitud) > 180) errores.push('longitud fuera de rango');
  return errores;
}

app.post('/mediciones', async (req, res) => {
  const b = req.body;
  const errores = validar(b);
  if (errores.length) return res.status(400).json({ errores });

  const esWifi = b.tipo_conexion === 'WIFI';
  const datos = {
    fecha_hora: new Date(b.fecha_hora).toISOString(),
    marca: b.marca, modelo: b.modelo, tipo_conexion: b.tipo_conexion,
  };
  for (const c of [...OBLIGATORIOS_NUM, ...OPCIONALES_NUM, ...OPCIONALES_TXT]) datos[c] = b[c] ?? null;

  // Los campos que no corresponden al tipo de conexión quedan vacíos
  const soloWifi = ['ssid', 'banda', 'proveedor', 'rssi_dbm', 'enlace_mbps', 'distancia_m'];
  const soloMovil = ['operador', 'red_movil', 'rsrp_dbm', 'snr_db'];
  for (const c of esWifi ? soloMovil : soloWifi) datos[c] = null;

  if (esWifi && !datos.proveedor) datos.proveedor = await proveedorPorIp(req.ip);

  const columnas = Object.keys(datos);
  try {
    const { rows } = await pool.query(
      `INSERT INTO mediciones (${columnas.join(', ')})
       VALUES (${columnas.map((_, i) => `$${i + 1}`).join(', ')}) RETURNING *`,
      columnas.map((c) => datos[c])
    );
    res.status(201).json(rows[0]);
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'No se pudo guardar la medición' });
  }
});

// Historial, con filtros por tipo de conexión, banda, operador y fecha
app.get('/mediciones', async (req, res) => {
  const { tipo_conexion, banda, operador, desde, hasta } = req.query;
  const condiciones = [];
  const valores = [];
  const agregar = (sql, valor) => { valores.push(valor); condiciones.push(sql.replace('?', `$${valores.length}`)); };

  if (tipo_conexion) agregar('tipo_conexion = ?', String(tipo_conexion).toUpperCase());
  if (banda) agregar('banda = ?', banda);
  if (operador) agregar('operador ILIKE ?', `%${operador}%`);
  if (desde) {
    if (Number.isNaN(Date.parse(desde))) return res.status(400).json({ error: 'desde inválida' });
    agregar('fecha_hora >= ?', desde);
  }
  if (hasta) {
    if (Number.isNaN(Date.parse(hasta))) return res.status(400).json({ error: 'hasta inválida' });
    agregar('fecha_hora <= ?', hasta);
  }
  const limite = Math.min(Math.max(parseInt(req.query.limite, 10) || 100, 1), 1000);

  try {
    const { rows } = await pool.query(
      `SELECT * FROM mediciones ${condiciones.length ? 'WHERE ' + condiciones.join(' AND ') : ''}
       ORDER BY fecha_hora DESC LIMIT ${limite}`,
      valores
    );
    res.json(rows);
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'No se pudo leer el historial' });
  }
});

app.get('/', (req, res) => res.json({ servicio: 'Medidor de Conexión - API', rutas: ['/ping', '/descarga', '/subida', '/mediciones'] }));

const PUERTO = process.env.PORT || 3000;
app.listen(PUERTO, () => console.log(`Servidor escuchando en el puerto ${PUERTO}`));
