package com.tfi.medidorconexion

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tfi.medidorconexion.ui.theme.AzulMarca
import com.tfi.medidorconexion.ui.theme.Marino
import com.tfi.medidorconexion.ui.theme.MedidorConexionTheme
import com.tfi.medidorconexion.ui.theme.NaranjaMarca
import com.tfi.medidorconexion.ui.theme.TurquesaMarca
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedidorConexionTheme {
                val pestanas = listOf("🏠" to "Inicio", "📋" to "Historial", "📊" to "Gráficos")
                var pestana by rememberSaveable { mutableIntStateOf(0) }
                // Dentro de "Inicio": 0 = menú con las dos opciones, 1 = Detectar conexión, 2 = Medir
                var inicio by rememberSaveable { mutableIntStateOf(0) }
                BackHandler(enabled = pestana != 0) { pestana = 0 }

                // Condición de la última conexión detectada o medida: colorea el ícono de los encabezados
                var calidad by remember { mutableStateOf<Calidad?>(null) }

                // Al abrir se ve un momento la bienvenida y después el menú de inicio
                var bienvenida by rememberSaveable { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(2600)
                    bienvenida = false
                }

                Crossfade(targetState = bienvenida, label = "bienvenida") { mostrarBienvenida ->
                    if (mostrarBienvenida) {
                        PantallaBienvenida()
                    } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = MaterialTheme.colorScheme.background,
                            bottomBar = {
                                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                                    pestanas.forEachIndexed { i, (icono, nombre) ->
                                        NavigationBarItem(
                                            selected = pestana == i,
                                            onClick = {
                                                if (i == 0) inicio = 0
                                                pestana = i
                                            },
                                            icon = { Text(icono, fontSize = 20.sp) },
                                            label = { Text(nombre) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedTextColor = Marino,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            // Cada pantalla dibuja su encabezado arriba: solo se respeta la barra de abajo
                            val modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                            when (pestana) {
                                0 -> when (inicio) {
                                    0 -> PantallaInicio(
                                        modifier = modifier,
                                        calidad = calidad,
                                        onDetectar = { inicio = 1 },
                                        onMedir = { inicio = 2 }
                                    )
                                    1 -> PantallaDetectar(modifier, calidad, { calidad = it }) { inicio = 0 }
                                    else -> PantallaMedir(modifier, calidad, { calidad = it }) { inicio = 0 }
                                }
                                1 -> PantallaHistorial(modifier, calidad)
                                else -> PantallaGraficos(modifier, calidad)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Lo que se midió con el servidor
data class Velocidades(
    val descargaMbps: Double,
    val subidaMbps: Double,
    val pingMs: Double,
    val jitterMs: Double
)

// Resultado de detectar o medir: un mensaje, los datos extraídos y, si se midió, las velocidades
data class ResultadoMedicion(
    val mensaje: String,
    val exito: Boolean = true,
    val datos: List<DatoVisual> = emptyList(),
    val velocidades: Velocidades? = null,
    val tipoConexion: String = "",
    val calidad: Calidad? = null,
    val senalDbm: Int? = null
)

// Recorre el procedimiento completo (conexión, contexto, ping, descarga, subida, envío)
// y devuelve el resultado; en cada etapa avisa el progreso con informar()
suspend fun medirYEnviar(
    context: Context,
    distanciaM: Double?,
    informar: (String) -> Unit
): ResultadoMedicion {
    informar("Detectando la conexión...")
    val red = leerDatosRed(context)
    if (red.tipoConexion != "WIFI" && red.tipoConexion != "MOVIL") {
        return ResultadoMedicion(
            mensaje = "No se puede medir sin Wi-Fi ni datos móviles.",
            exito = false,
            datos = datosExtraidos(red, null)
        )
    }
    val contexto = leerContexto(context)
    val datos = datosExtraidos(red, contexto)
    val senal = if (red.tipoConexion == "WIFI") red.rssiDbm else red.rsrpDbm
    val calidad = calidadSenal(red.tipoConexion, senal)
    // Mientras se mide solo se informa en qué etapa va; los resultados se muestran al terminar
    return try {
        informar("Paso 1 de 4: midiendo el ping...")
        val ping = medirPing()
        informar("Paso 2 de 4: midiendo la descarga (unos 10 s)...")
        val descarga = medirDescarga()
        informar("Paso 3 de 4: midiendo la subida (unos 10 s)...")
        val subida = medirSubida()
        informar("Paso 4 de 4: enviando los resultados al servidor...")
        enviarMedicion(red, contexto, ping, descarga, subida, distanciaM)
        ResultadoMedicion(
            mensaje = "Medición guardada en el servidor",
            datos = datos,
            velocidades = Velocidades(descarga, subida, ping.promedioMs, ping.jitterMs),
            tipoConexion = red.tipoConexion,
            calidad = calidad,
            senalDbm = senal
        )
    } catch (e: Exception) {
        ResultadoMedicion(
            mensaje = "No se pudo completar la medición: ${e.message}\n" +
                "(¿está el servidor andando y bien configurada la URL en Servidor.kt?)",
            exito = false,
            datos = datos,
            tipoConexion = red.tipoConexion,
            calidad = calidad,
            senalDbm = senal
        )
    }
}

// Resultado: mensaje, condición de la conexión, velocímetros, ping y jitter, y los datos en fichas.
// Sin velocidades (Detectar) se muestran solo la condición y los datos de la conexión y del dispositivo.
@Composable
fun PanelResultado(r: ResultadoMedicion) {
    val v = r.velocidades
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (r.exito) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = (if (r.exito) "✅  " else "⚠️  ") + r.mensaje,
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.SemiBold
            )
        }

        if (r.calidad != null && r.senalDbm != null) {
            TarjetaCondicion(r.calidad, r.tipoConexion, r.senalDbm, Modifier.fillMaxWidth())
        }

        if (v != null) {
            Tarjeta(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Velocimetro(v.descargaMbps, "⬇  Descarga", listOf(TurquesaMarca, AzulMarca))
                    Velocimetro(v.subidaMbps, "⬆  Subida", listOf(NaranjaMarca, Color(0xFFC0392B)))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TarjetaMetrica("⏱", "Ping", v.pingMs, "ms", colorPing(v.pingMs), Modifier.weight(1f))
                TarjetaMetrica("〰", "Jitter", v.jitterMs, "ms", colorJitter(v.jitterMs), Modifier.weight(1f))
            }
        }

        if (r.datos.isNotEmpty()) {
            Tarjeta(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Datos extraídos", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Marino)
                    FichasDatos(r.datos)
                }
            }
        }
    }
}
