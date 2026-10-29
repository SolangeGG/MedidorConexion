package com.tfi.medidorconexion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Estado de la pantalla: cargando, con datos o con error
private sealed interface EstadoHistorial {
    data object Cargando : EstadoHistorial
    data class Listo(val mediciones: List<MedicionEnvio>) : EstadoHistorial
    data class Error(val mensaje: String) : EstadoHistorial
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
fun PantallaHistorial(onVolver: () -> Unit, modifier: Modifier = Modifier) {
    var estado by remember { mutableStateOf<EstadoHistorial>(EstadoHistorial.Cargando) }
    var recargas by remember { mutableStateOf(0) }

    LaunchedEffect(recargas) {
        estado = EstadoHistorial.Cargando
        estado = try {
            EstadoHistorial.Listo(obtenerHistorial())
        } catch (e: Exception) {
            EstadoHistorial.Error(e.message ?: "error desconocido")
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text("Historial de mediciones", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = onVolver) { Text("Volver") }
            FilledTonalButton(modifier = Modifier.weight(1f), onClick = { recargas++ }) { Text("Actualizar") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        when (val e = estado) {
            is EstadoHistorial.Cargando -> Text("Cargando...")
            is EstadoHistorial.Error ->
                Text("No se pudo leer el historial: ${e.mensaje}\n(¿está el servidor andando?)")
            is EstadoHistorial.Listo ->
                if (e.mediciones.isEmpty()) {
                    Text("Todavía no hay mediciones guardadas.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(e.mediciones) { m -> TarjetaMedicion(m) }
                    }
                }
        }
    }
}

@Composable
private fun TarjetaMedicion(m: MedicionEnvio) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(formatearFecha(m.fechaHora), fontWeight = FontWeight.Bold)
            Text(detalleConexion(m))
            Text("↓ %.1f Mbps   ↑ %.1f Mbps".format(m.descargaMbps, m.subidaMbps))
            Text("Ping %.0f ms · jitter %.1f ms".format(m.pingMs, m.jitterMs))
            Text("${m.marca} ${m.modelo}", fontSize = 12.sp)
        }
    }
}
