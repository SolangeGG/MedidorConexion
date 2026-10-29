# Servidor – Medidor de Conexión (TFI Comunicación de Datos 2026)

API REST en Node.js + Express con PostgreSQL. Es el otro extremo de las pruebas de red
(ping, descarga, subida) y guarda las mediciones.

## Puesta en marcha local

1. Copiar `.env.example` como `.env` y completar `PGPASSWORD`.
2. `npm.cmd install`
3. `npm.cmd run setup-db`  (crea la base `medidor_conexion` y la tabla; se puede repetir)
4. `npm.cmd start`  → http://localhost:3000

## Endpoints

| Método | Ruta | Función |
|---|---|---|
| GET | `/ping` | Responde `{"ok":true}` al instante. La app mide el tiempo de ida y vuelta. |
| GET | `/descarga?segundos=12` | Flujo de bytes aleatorios (máx. 30 s). La app cuenta bytes y corta al terminar. |
| POST | `/subida` | Recibe y descarta el cuerpo. Responde `{"bytes_recibidos": N}`. |
| POST | `/mediciones` | Guarda una medición (JSON de abajo). Responde 201 con la fila guardada. |
| GET | `/mediciones` | Historial. Filtros: `tipo_conexion`, `banda`, `operador` (parcial), `desde`, `hasta`, `limite`. |

## JSON de `POST /mediciones`

Obligatorios: `fecha_hora` (ISO 8601), `marca`, `modelo`, `tipo_conexion` (`WIFI` o `MOVIL`),
`descarga_mbps`, `subida_mbps`, `ping_ms`, `jitter_ms`. Los números van como números, no como texto.

```json
{
  "fecha_hora": "2026-10-28T21:10:00-03:00",
  "marca": "samsung",
  "modelo": "A54",
  "tipo_conexion": "WIFI",
  "latitud": -26.83,
  "longitud": -65.2,
  "descarga_mbps": 48.3,
  "subida_mbps": 12.1,
  "ping_ms": 23.4,
  "jitter_ms": 3.2,
  "ssid": "Casa",
  "banda": "5 GHz",
  "rssi_dbm": -55,
  "enlace_mbps": 433,
  "distancia_m": 4
}
```

Para `MOVIL` se envían `operador`, `red_movil`, `rsrp_dbm` y `snr_db` en lugar de los campos de Wi-Fi.
Los campos que no corresponden al tipo de conexión se ignoran y quedan en NULL.
`proveedor` no hace falta enviarlo: el servidor lo obtiene por la IP pública.

Errores de validación: 400 con `{"errores": [...]}`.

## Despliegue en Render

- Crear un PostgreSQL en Render y copiar su *Internal Database URL* a la variable `DATABASE_URL` del servicio web.
- Build command: `npm install` · Start command: `npm start`.
- Una vez, desde la consola de Render (o local con `DATABASE_URL` externa): `npm run setup-db`.
