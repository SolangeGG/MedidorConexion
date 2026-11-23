package com.tfi.medidorconexion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tfi.medidorconexion.ui.theme.Marino
import kotlin.math.cos
import kotlin.math.sin

// Ícono de velocímetro: un arco con una aguja; `nivel` (0 a 1) es hacia dónde apunta la aguja
@Composable
fun IconoVelocimetro(
    tamano: Dp,
    modifier: Modifier = Modifier,
    color: Color = Marino,
    nivel: Float = 0.7f
) {
    Canvas(modifier = modifier.size(tamano)) {
        val trazo = size.minDimension * 0.1f
        val lado = size.minDimension - trazo
        val centro = Offset(size.width / 2, size.height / 2)
        drawArc(
            color = color,
            startAngle = 150f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(trazo / 2, trazo / 2),
            size = Size(lado, lado),
            style = Stroke(width = trazo, cap = StrokeCap.Round)
        )
        // La aguja recorre el mismo arco: a la izquierda es mala condición y a la derecha, excelente
        val angulo = Math.toRadians(150.0 + 240.0 * nivel.coerceIn(0f, 1f))
        val largo = lado * 0.38f
        drawLine(
            color = color,
            start = centro,
            end = Offset(
                centro.x + largo * cos(angulo).toFloat(),
                centro.y + largo * sin(angulo).toFloat()
            ),
            strokeWidth = trazo * 0.8f,
            cap = StrokeCap.Round
        )
        drawCircle(color = color, radius = trazo * 0.9f, center = centro)
    }
}

// Pantalla que se ve un momento al abrir la app: el velocímetro sube de rojo a verde sobre fondo blanco
@Composable
fun PantallaBienvenida() {
    val nivel = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        nivel.animateTo(0.92f, animationSpec = tween(durationMillis = 1600, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconoVelocimetro(tamano = 120.dp, color = colorDeNivel(nivel.value), nivel = nivel.value)
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Medidor de Conexión",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Marino
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "¿Qué tan buena es tu conexión?",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
