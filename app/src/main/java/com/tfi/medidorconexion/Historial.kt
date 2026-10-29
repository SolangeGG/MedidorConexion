package com.tfi.medidorconexion

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tfi.medidorconexion.ui.theme.AzulMarca
import com.tfi.medidorconexion.ui.theme.NaranjaMarca
import com.tfi.medidorconexion.ui.theme.TurquesaMarca
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Estado de una pantalla que lee el historial del servidor: cargando, con datos o con error
sealed interface EstadoHistorial {
    data object Cargando : EstadoHistorial
    data class Listo(val mediciones: List<MedicionEnvio>) : EstadoHistorial
    data class Error(val mensaje: String) : EstadoHistorial
}

// Lee el historial del servidor; devuelve el estado y una función para volver a cargarlo
@Composable
fun cargarHistorial(): Pair<EstadoHistorial, () -> Unit> {
    val context = LocalContext.current
    var estado by remember { mutableStateOf<EstadoHistorial>(EstadoHistorial.Cargando) }
    var recargas by remember { mutableIntStateOf(0) }

    LaunchedEffect(recargas) {
        estado = EstadoHistorial.Cargando
        estado = try {
            // Solo las mediciones de este celular, aunque el servidor guarde las de otros
            EstadoHistorial.Listo(
                soloDeEsteDispositivo(obtenerHistorial(), Build.MANUFACTURER, Build.MODEL, idDelDispositivo(context))
            )
        } catch (e: Exception) {
            EstadoHistorial.Error(e.message ?: "error desconocido")
        }
    }
    return estado to { recargas++ }
}

// El servidor devuelve la fecha en UTC (ISO 8601); se muestra en la hora local del celular
fun formatearFecha(iso: String, zona: ZoneId = ZoneId.systemDefault()): String =
    try {
        Instant.parse(iso).atZone(zona).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    } catch (e: Exception) {
        iso
    }

// Resume en un texto los datos propios de cada tipo de conexión
fun detalleConexion(m: MedicionEnvio): String =
    if (m.tipoConexion == "WIFI") {
        "Wi-Fi · ${m.ssid ?: "red desconocida"} · ${m.banda ?: "?"} · ${m.rssiDbm?.let { "$it dBm" } ?: "sin señal"}"
    } else {
        "Datos móviles · ${m.operador ?: "operador desconocido"} · ${m.redMovil ?: "?"} · ${m.rsrpDbm?.let { "$it dBm" } ?: "sin señal"}"
    }

// Lista de las mediciones guardadas en el servidor
@Composable
fun PantallaHistorial(modifier: Modifier = Modifier, calidad: Calidad? = null) {
    val (estado, recargar) = cargarHistorial()
    var seleccionada by remember { mutableStateOf<MedicionEnvio?>(null) }
    var filtro by remember { mutableStateOf(FiltroHistorial()) }
    var mostrarFiltros by remember { mutableStateOf(false) }

    // Al tocar una medición se abre su detalle en lugar de la lista
    val abierta = seleccionada
    if (abierta != null) {
        DetalleMedicion(abierta, onVolver = { seleccionada = null }, modifier = modifier)
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        EncabezadoApp("Historial", "Las mediciones de tu dispositivo", calidad = calidad)

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    onClick = recargar
                ) { Text("🔄  Actualizar", fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    onClick = { mostrarFiltros = !mostrarFiltros }
                ) { Text(if (filtro.activo) "🔎  Filtros •" else "🔎  Filtros", fontWeight = FontWeight.SemiBold) }
            }
            Text(
                text = "Tocá una medición para ver su detalle.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            when (val e = estado) {
                is EstadoHistorial.Cargando -> Text("Cargando...")
                is EstadoHistorial.Error ->
                    Text("No se pudo leer el historial: ${e.mensaje}\n(¿está el servidor andando?)")
                is EstadoHistorial.Listo ->
                    if (e.mediciones.isEmpty()) {
                        Text("Todavía no hay mediciones de este dispositivo.")
                    } else {
                        val visibles = filtrarMediciones(e.mediciones, filtro)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (mostrarFiltros) {
                                item { PanelFiltros(e.mediciones, filtro) { filtro = it } }
                            }
                            item {
                                Text(
                                    text = "Mostrando ${visibles.size} de ${e.mediciones.size} mediciones.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (visibles.isEmpty()) item { Text("No hay mediciones con esos filtros.") }
                            items(visibles) { m -> TarjetaMedicion(m) { seleccionada = m } }
                        }
                    }
            }
        }
    }
}

// Número con su unidad y un título chico, para las velocidades y el ping
@Composable
private fun Cifra(titulo: String, valor: String, unidad: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(titulo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Text(" $unidad", fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

// Detalle de una medición guardada: condición, velocímetros, ping, jitter y datos, como al medir
@Composable
fun DetalleMedicion(m: MedicionEnvio, onVolver: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onVolver)
    val resultado = resultadoDeMedicion(m)

    Column(modifier = modifier.fillMaxSize()) {
        EncabezadoApp("Detalle de la medición", formatearFecha(m.fechaHora), calidad = resultado.calidad)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                onClick = onVolver
            ) { Text("←  Volver al historial", fontWeight = FontWeight.SemiBold) }
            PanelResultado(resultado)
        }
    }
}

@Composable
private fun TarjetaMedicion(m: MedicionEnvio, onClick: () -> Unit) {
    val esWifi = m.tipoConexion == "WIFI"
    val colorTipo = if (esWifi) AzulMarca else NaranjaMarca

    Tarjeta(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Franja de color: azul para Wi-Fi, naranja para datos móviles
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(colorTipo))
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = if (esWifi) "📶  Wi-Fi" else "📱  Datos móviles",
                        fontWeight = FontWeight.Bold,
                        color = colorTipo
                    )
                    Text(
                        formatearFecha(m.fechaHora),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Cifra("⬇ Descarga", "%.1f".format(m.descargaMbps), "Mbps", TurquesaMarca)
                    Cifra("⬆ Subida", "%.1f".format(m.subidaMbps), "Mbps", NaranjaMarca)
                    Cifra("⏱ Ping", "%.0f".format(m.pingMs), "ms", MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = detalleConexion(m).substringAfter(" · "),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${m.marca} ${m.modelo}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = "Ver detalle ›", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colorTipo)
                }
            }
        }
    }
}
