package com.tfi.medidorconexion

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tfi.medidorconexion.ui.theme.Marino
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class RangoFecha(val etiqueta: String) {
    TODAS("Todas"),
    HOY("Hoy"),
    SIETE_DIAS("7 días"),
    TREINTA_DIAS("30 días")
}

// Filtros del historial (como en el informe: tipo de conexión, banda, operador y fecha); null = sin filtrar
data class FiltroHistorial(
    val tipo: String? = null,       // "WIFI" o "MOVIL"
    val banda: String? = null,
    val operador: String? = null,
    val fecha: RangoFecha = RangoFecha.TODAS
) {
    val activo: Boolean get() = tipo != null || banda != null || operador != null || fecha != RangoFecha.TODAS
}

fun filtrarMediciones(
    mediciones: List<MedicionEnvio>,
    filtro: FiltroHistorial,
    ahora: Instant = Instant.now(),
    zona: ZoneId = ZoneId.systemDefault()
): List<MedicionEnvio> = mediciones.filter { m ->
    (filtro.tipo == null || m.tipoConexion == filtro.tipo) &&
        (filtro.banda == null || m.banda == filtro.banda) &&
        (filtro.operador == null || m.operador == filtro.operador) &&
        dentroDelRango(m.fechaHora, filtro.fecha, ahora, zona)
}

private fun dentroDelRango(iso: String, rango: RangoFecha, ahora: Instant, zona: ZoneId): Boolean {
    if (rango == RangoFecha.TODAS) return true
    val momento = try {
        Instant.parse(iso)
    } catch (e: Exception) {
        return true   // si la fecha no se puede leer, no se descarta la medición
    }
    return when (rango) {
        RangoFecha.TODAS -> true
        RangoFecha.HOY -> momento.atZone(zona).toLocalDate() == ahora.atZone(zona).toLocalDate()
        RangoFecha.SIETE_DIAS -> !momento.isBefore(ahora.minus(7, ChronoUnit.DAYS))
        RangoFecha.TREINTA_DIAS -> !momento.isBefore(ahora.minus(30, ChronoUnit.DAYS))
    }
}

// Una fila de chips para elegir una opción; el título va arriba
@Composable
private fun <T> FilaFiltro(
    titulo: String,
    opciones: List<Pair<T, String>>,
    seleccion: T,
    onElegir: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((valor, etiqueta) in opciones) {
                FilterChip(
                    selected = seleccion == valor,
                    onClick = { onElegir(valor) },
                    label = { Text(etiqueta) }
                )
            }
        }
    }
}

// Panel con los filtros; las opciones de banda y operador salen de las mediciones que hay
@Composable
fun PanelFiltros(
    mediciones: List<MedicionEnvio>,
    filtro: FiltroHistorial,
    onCambio: (FiltroHistorial) -> Unit
) {
    Tarjeta(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Filtros", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Marino)

            FilaFiltro(
                "Conexión",
                listOf<Pair<String?, String>>(null to "Todas", "WIFI" to "Wi-Fi", "MOVIL" to "Datos móviles"),
                filtro.tipo
            ) { onCambio(filtro.copy(tipo = it)) }

            val bandas = mediciones.mapNotNull { it.banda }.filter { it != "Desconocida" }.distinct().sorted()
            if (bandas.isNotEmpty()) {
                FilaFiltro(
                    "Banda",
                    listOf<Pair<String?, String>>(null to "Todas") + bandas.map { it to it },
                    filtro.banda
                ) { onCambio(filtro.copy(banda = it)) }
            }

            val operadores = mediciones.mapNotNull { it.operador }.distinct().sorted()
            if (operadores.isNotEmpty()) {
                FilaFiltro(
                    "Operador",
                    listOf<Pair<String?, String>>(null to "Todos") + operadores.map { it to it },
                    filtro.operador
                ) { onCambio(filtro.copy(operador = it)) }
            }

            FilaFiltro(
                "Fecha",
                RangoFecha.entries.map { it to it.etiqueta },
                filtro.fecha
            ) { onCambio(filtro.copy(fecha = it)) }

            if (filtro.activo) {
                TextButton(onClick = { onCambio(FiltroHistorial()) }) { Text("Limpiar filtros") }
            }
        }
    }
}
