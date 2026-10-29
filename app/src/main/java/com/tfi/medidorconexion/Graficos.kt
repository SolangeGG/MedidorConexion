package com.tfi.medidorconexion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tfi.medidorconexion.ui.theme.AzulMarca
import com.tfi.medidorconexion.ui.theme.Marino
import com.tfi.medidorconexion.ui.theme.NaranjaMarca
import com.tfi.medidorconexion.ui.theme.TurquesaMarca
import kotlin.math.ceil
import kotlin.math.max

// Un grupo de dos barras (a y b) con su etiqueta
private data class GrupoBarras(val etiqueta: String, val a: Double, val b: Double)

// Una serie de barras: su nombre en la leyenda y los dos colores del degradé
private data class Serie(val nombre: String, val arriba: Color, val abajo: Color)

private val SERIE_DESCARGA = Serie("Descarga (Mbps)", TurquesaMarca, AzulMarca)
private val SERIE_SUBIDA = Serie("Subida (Mbps)", NaranjaMarca, Color(0xFFC0392B))

// Pantalla con los gráficos del historial (punto 7 del informe: comparaciones)
@Composable
fun PantallaGraficos(modifier: Modifier = Modifier, calidad: Calidad? = null) {
    val (estado, recargar) = cargarHistorial()

    Column(modifier = modifier.fillMaxSize()) {
        EncabezadoApp("Gráficos", "Comparaciones de tu dispositivo", calidad = calidad)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                onClick = recargar
            ) { Text("🔄  Actualizar", fontWeight = FontWeight.SemiBold) }

            when (val e = estado) {
                is EstadoHistorial.Cargando -> Text("Cargando...")
                is EstadoHistorial.Error ->
                    Text("No se pudo leer el historial: ${e.mensaje}\n(¿está el servidor andando?)")
                is EstadoHistorial.Listo ->
                    if (e.mediciones.isEmpty()) {
                        Text("Todavía no hay mediciones de este dispositivo para graficar.")
                    } else {
                        GraficosDe(e.mediciones)
                    }
            }
        }
    }
}

@Composable
private fun GraficosDe(mediciones: List<MedicionEnvio>) {
    val wifi = mediciones.filter { it.tipoConexion == "WIFI" }

    // 0. Resumen: promedios de todas las mediciones y los mejores valores
    TarjetaGrafico("📌  Resumen de tus mediciones") {
        FichasDatos(resumenDeMediciones(mediciones))
    }

    // Gráficos de los promedios: velocidad promedio contra la mejor, y latencia promedio
    val promedios = calcularPromedios(mediciones)
    if (promedios != null) {
        TarjetaGrafico("📊  Velocidad: promedio y mejor") {
            GraficoBarras(
                listOf(
                    GrupoBarras("Promedio", promedios.descarga, promedios.subida),
                    GrupoBarras("Mejor", promedios.mejorDescarga, promedios.mejorSubida)
                )
            )
        }
        TarjetaGrafico("⏱  Latencia promedio") {
            GraficoBarras(
                listOf(GrupoBarras("Promedio", promedios.ping, promedios.jitter)),
                serieA = Serie("Ping (ms)", AzulMarca, Marino),
                serieB = Serie("Jitter (ms)", Color(0xFFF2B01E), NaranjaMarca)
            )
        }
    }

    // 1. Velocidad de descarga y velocidad de enlace según el nivel de señal (solo Wi-Fi), como pide el informe
    val puntosDescarga = wifi.mapNotNull { m -> m.rssiDbm?.let { it.toDouble() to m.descargaMbps } }
    val puntosEnlace = wifi.mapNotNull { m ->
        val senal = m.rssiDbm
        val enlace = m.enlaceMbps
        if (senal != null && enlace != null) senal.toDouble() to enlace.toDouble() else null
    }
    TarjetaGrafico("📈  Descarga y enlace según la señal (Wi-Fi)") {
        if (puntosDescarga.isEmpty()) {
            Text("Todavía no hay mediciones Wi-Fi con señal.")
        } else {
            GraficoDispersion(
                listOfNotNull(
                    SerieDispersion("Descarga medida", TurquesaMarca, puntosDescarga),
                    if (puntosEnlace.isNotEmpty()) SerieDispersion("Velocidad de enlace", NaranjaMarca, puntosEnlace) else null
                )
            )
            Text(
                "${puntosDescarga.size} mediciones. Cuanto más a la derecha, mejor señal. " +
                    "El enlace es la velocidad que negocia el celular con el router; la descarga es la real.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 2. Banda de 2,4 GHz contra 5 GHz (solo Wi-Fi)
    val conBanda = wifi.filter { it.banda != null && it.banda != "Desconocida" }
    TarjetaGrafico("📊  Promedio por banda Wi-Fi") {
        GraficoBarrasPorClave(conBanda) { it.banda }
    }

    // 3. Wi-Fi contra datos móviles
    TarjetaGrafico("📊  Promedio: Wi-Fi contra datos móviles") {
        GraficoBarrasPorClave(mediciones) { if (it.tipoConexion == "WIFI") "Wi-Fi" else "Datos móviles" }
    }
}

@Composable
private fun TarjetaGrafico(titulo: String, contenido: @Composable () -> Unit) {
    Tarjeta(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            contenido()
        }
    }
}

// Una serie de puntos (señal en dBm, velocidad en Mbps) con su nombre y su color
private data class SerieDispersion(val nombre: String, val color: Color, val puntos: List<Pair<Double, Double>>)

// Puntos de una o más series: eje horizontal = señal (dBm), eje vertical = velocidad (Mbps)
@Composable
private fun GraficoDispersion(series: List<SerieDispersion>) {
    val todos = series.flatMap { it.puntos }
    val minX = todos.minOf { it.first }
    val maxX = todos.maxOf { it.first }
    val (desde, hasta) = if (maxX - minX < 5) (minX - 5) to (maxX + 5) else minX to maxX
    val maxY = max(10.0, ceil(todos.maxOf { it.second } / 10) * 10)
    val rejilla = MaterialTheme.colorScheme.outlineVariant

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Mbps (máx. ${"%.0f".format(maxY)})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val margen = 12.dp.toPx()
            val ancho = size.width - 2 * margen
            val alto = size.height - 2 * margen
            for (i in 0..4) {
                val y = margen + alto * i / 4
                drawLine(rejilla, Offset(margen, y), Offset(size.width - margen, y), strokeWidth = 1.dp.toPx())
            }
            for (serie in series) {
                for ((x, y) in serie.puntos) {
                    val centro = Offset(
                        margen + ancho * ((x - desde) / (hasta - desde)).toFloat(),
                        margen + alto * (1 - (y / maxY).toFloat())
                    )
                    // Un halo suave alrededor de cada punto
                    drawCircle(serie.color.copy(alpha = 0.25f), radius = 12.dp.toPx(), center = centro)
                    drawCircle(serie.color, radius = 6.dp.toPx(), center = centro)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${"%.0f".format(desde)} dBm", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${"%.0f".format(hasta)} dBm", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (serie in series) Leyenda(serie.nombre, serie.color)
        }
    }
}

// Agrupa las mediciones por una clave y grafica el promedio de descarga y de subida de cada grupo
@Composable
private fun GraficoBarrasPorClave(mediciones: List<MedicionEnvio>, clave: (MedicionEnvio) -> String?) {
    val descargas = promedioPor(mediciones, clave) { it.descargaMbps }
    val subidas = promedioPor(mediciones, clave) { it.subidaMbps }
    val grupos = descargas.keys.sorted().map { GrupoBarras(it, descargas.getValue(it), subidas.getValue(it)) }

    if (grupos.isEmpty()) {
        Text("Todavía no hay datos para comparar.")
    } else {
        GraficoBarras(grupos)
    }
}

@Composable
private fun GraficoBarras(
    grupos: List<GrupoBarras>,
    serieA: Serie = SERIE_DESCARGA,
    serieB: Serie = SERIE_SUBIDA
) {
    val maximo = grupos.maxOf { max(it.a, it.b) }.coerceAtLeast(1.0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        for (g in grupos) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Barra(g.a, maximo, serieA.arriba, serieA.abajo)
                    Barra(g.b, maximo, serieB.arriba, serieB.abajo)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(g.etiqueta, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Leyenda(serieA.nombre, serieA.arriba)
        Leyenda(serieB.nombre, serieB.arriba)
    }
}

private val ALTO_MAXIMO_BARRA: Dp = 140.dp

// Barra con degradé vertical; encima lleva su valor
@Composable
private fun Barra(valor: Double, maximo: Double, arriba: Color, abajo: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Con valores chicos (ms) se muestra un decimal
        Text(
            if (maximo < 100) "%.1f".format(valor) else "%.0f".format(valor),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(maxOf(2.dp, ALTO_MAXIMO_BARRA * (valor / maximo).toFloat()))
                .background(
                    Brush.verticalGradient(listOf(arriba, abajo)),
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
        )
    }
}

@Composable
private fun Leyenda(texto: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Text(texto, fontSize = 12.sp)
    }
}
