package com.tfi.medidorconexion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tfi.medidorconexion.ui.theme.Marino

// Colores de la condición de la conexión: del amarillo al verde cuando mejora, rojo cuando es débil
fun colorDe(calidad: Calidad): Color = when (calidad) {
    Calidad.EXCELENTE -> Color(0xFF2E9E5B)
    Calidad.BUENA -> Color(0xFF8BC34A)
    Calidad.REGULAR -> Color(0xFFF2B01E)
    Calidad.DEBIL -> Color(0xFFD64545)
}

// Color para una posición de 0 a 1: pasa del rojo al amarillo y al verde a medida que sube la aguja
fun colorDeNivel(nivel: Float): Color {
    val n = nivel.coerceIn(0f, 1f)
    val (desde, hasta, tramo) = when {
        n < 0.4f -> Triple(colorDe(Calidad.DEBIL), colorDe(Calidad.REGULAR), n / 0.4f)
        n < 0.7f -> Triple(colorDe(Calidad.REGULAR), colorDe(Calidad.BUENA), (n - 0.4f) / 0.3f)
        else -> Triple(colorDe(Calidad.BUENA), colorDe(Calidad.EXCELENTE), (n - 0.7f) / 0.3f)
    }
    return lerp(desde, hasta, tramo)
}

// Hacia dónde apunta la aguja del velocímetro según la condición (sin medir, queda a mitad de camino)
fun nivelDe(calidad: Calidad?): Float = when (calidad) {
    null -> 0.7f
    Calidad.EXCELENTE -> 0.92f
    Calidad.BUENA -> 0.72f
    Calidad.REGULAR -> 0.45f
    Calidad.DEBIL -> 0.15f
}

// Semáforo del ping y del jitter: cuanto más bajos, mejor
fun colorPing(ms: Double): Color = when {
    ms < 30 -> colorDe(Calidad.EXCELENTE)
    ms < 70 -> colorDe(Calidad.REGULAR)
    else -> colorDe(Calidad.DEBIL)
}

fun colorJitter(ms: Double): Color = when {
    ms < 10 -> colorDe(Calidad.EXCELENTE)
    ms < 30 -> colorDe(Calidad.REGULAR)
    else -> colorDe(Calidad.DEBIL)
}

// Banda superior blanca: el ícono de velocímetro cambia de color y de posición según la condición
@Composable
fun EncabezadoApp(
    titulo: String,
    subtitulo: String? = null,
    calidad: Calidad? = null,
    grande: Boolean = false
) {
    val colorIcono by animateColorAsState(
        targetValue = calidad?.let { colorDe(it) } ?: Marino,
        animationSpec = tween(durationMillis = 800),
        label = "colorIcono"
    )
    val nivel by animateFloatAsState(
        targetValue = nivelDe(calidad),
        animationSpec = tween(durationMillis = 900),
        label = "nivelIcono"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = if (grande) 22.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconoVelocimetro(tamano = if (grande) 54.dp else 38.dp, color = colorIcono, nivel = nivel)
            Column {
                Text(
                    text = titulo,
                    color = Marino,
                    fontSize = if (grande) 26.sp else 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitulo != null) {
                    Text(text = subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// Tarjeta de esquinas redondeadas con un borde fino; el contenido define su propio relleno.
// Con `onClick` se puede tocar (por ejemplo, para abrir el detalle de una medición).
@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit
) {
    val forma = RoundedCornerShape(20.dp)
    val elevacion = CardDefaults.cardElevation(defaultElevation = 1.dp)
    val colores = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    val borde = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = forma,
            elevation = elevacion,
            colors = colores,
            border = borde,
            content = contenido
        )
    } else {
        Card(modifier = modifier, shape = forma, elevation = elevacion, colors = colores, border = borde, content = contenido)
    }
}

// Medidor circular: el arco, con degradé, se llena según la velocidad respecto de una escala "redonda"
@Composable
fun Velocimetro(
    valor: Double,
    titulo: String,
    colores: List<Color>,
    modifier: Modifier = Modifier
) {
    val maximo = escalaVelocimetro(valor)
    val llenado by animateFloatAsState(
        targetValue = (valor / maximo).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000),
        label = "velocimetro"
    )
    val fondo = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(148.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trazo = 16.dp.toPx()
                val lado = size.minDimension - trazo
                val origen = Offset(trazo / 2, trazo / 2)
                val tamano = Size(lado, lado)
                val estilo = Stroke(width = trazo, cap = StrokeCap.Round)
                drawArc(fondo, 150f, 240f, false, origen, tamano, style = estilo)
                // El degradé circular arranca a las 3 en punto; se gira para que empiece donde empieza el arco
                rotate(degrees = 150f, pivot = center) {
                    drawArc(
                        // El último color vuelve al primero para que el borde redondeado del inicio no cambie de color
                        brush = Brush.sweepGradient(
                            0f to colores.first(),
                            0.66f to colores.last(),
                            1f to colores.first(),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 240f * llenado,
                        useCenter = false,
                        topLeft = origen,
                        size = tamano,
                        style = estilo
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%.1f".format(valor), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Marino)
                Text("Mbps", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Marino)
    }
}

// Tarjeta con un número grande, su unidad y un punto de color que indica si el valor es bueno
@Composable
fun TarjetaMetrica(
    icono: String,
    titulo: String,
    valor: Double,
    unidad: String,
    indicador: Color,
    modifier: Modifier = Modifier
) {
    Tarjeta(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(10.dp).background(indicador, CircleShape))
                Text("$icono  $titulo", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%.1f".format(valor), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Marino)
                Text(" $unidad", fontSize = 14.sp, color = Marino, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

// Barra de rojo a verde con un marcador que indica dónde está la señal
@Composable
fun BarraSenal(nivel: Float, modifier: Modifier = Modifier) {
    val posicion by animateFloatAsState(
        targetValue = nivel,
        animationSpec = tween(durationMillis = 900),
        label = "barraSenal"
    )
    val marcador = 22.dp
    Column(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(marcador)) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                colorDe(Calidad.DEBIL),
                                colorDe(Calidad.REGULAR),
                                colorDe(Calidad.BUENA),
                                colorDe(Calidad.EXCELENTE)
                            )
                        ),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (maxWidth - marcador) * posicion)
                    .size(marcador)
                    .background(Color.White, CircleShape)
                    .border(3.dp, Marino, CircleShape)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Débil", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Excelente", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Tarjeta destacada con la condición de la conexión: ícono de color, frase y barra de señal
@Composable
fun TarjetaCondicion(calidad: Calidad, tipoConexion: String, dbm: Int, modifier: Modifier = Modifier) {
    val color = colorDe(calidad)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconoVelocimetro(tamano = 60.dp, color = color, nivel = nivelDe(calidad))
                Column {
                    Text(
                        "Conexión ${calidad.etiqueta.lowercase()}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Marino
                    )
                    Text(
                        fraseCondicion(calidad, tipoConexion),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Señal: $dbm dBm", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Marino)
                }
            }
            BarraSenal(nivelSenal(tipoConexion, dbm))
        }
    }
}

// Una ficha con ícono, título y valor
@Composable
private fun FichaDato(dato: DatoVisual, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(
            "${dato.icono}  ${dato.titulo}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(dato.valor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Marino)
    }
}

// Los datos extraídos, ordenados en fichas de a dos por fila
@Composable
fun FichasDatos(datos: List<DatoVisual>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (fila in datos.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (dato in fila) FichaDato(dato, Modifier.weight(1f).fillMaxHeight())
                if (fila.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
