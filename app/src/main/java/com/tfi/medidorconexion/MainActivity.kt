package com.tfi.medidorconexion

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tfi.medidorconexion.ui.theme.MedidorConexionTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedidorConexionTheme {
                var verHistorial by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = verHistorial) { verHistorial = false }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (verHistorial) {
                        PantallaHistorial(
                            onVolver = { verHistorial = false },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        PantallaPrincipal(
                            onVerHistorial = { verHistorial = true },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

// Recorre el procedimiento completo (conexión, contexto, ping, descarga, subida, envío)
// y devuelve el texto final; en cada etapa avisa el progreso con informar()
suspend fun medirYEnviar(
    context: Context,
    distanciaM: Double?,
    informar: (String) -> Unit
): String {
    informar("Detectando la conexión...")
    val red = leerDatosRed(context)
    if (red.tipoConexion != "WIFI" && red.tipoConexion != "MOVIL") {
        return describir(red) + "\n\nNo se puede medir sin Wi-Fi ni datos móviles."
    }
    val contexto = leerContexto(context)
    // Lo detectado se muestra desde el principio y queda en el resultado final
    val detectado = describir(red) + "\n\n" + describirContexto(contexto)
    return try {
        informar("Midiendo ping...\n\n$detectado")
        val ping = medirPing()
        informar("Midiendo descarga (unos 10 s)...\n\n$detectado")
        val descarga = medirDescarga()
        informar("Midiendo subida (unos 10 s)...\n\n$detectado")
        val subida = medirSubida()
        informar("Enviando resultados al servidor...\n\n$detectado")
        enviarMedicion(red, contexto, ping, descarga, subida, distanciaM)
        """
            Medición guardada en el servidor

            Descarga: %.2f Mbps
            Subida: %.2f Mbps
            Ping: %.1f ms (jitter %.1f ms)
        """.trimIndent().format(descarga, subida, ping.promedioMs, ping.jitterMs) +
            "\n\n" + detectado
    } catch (e: Exception) {
        "No se pudo completar la medición: ${e.message}\n(¿está el servidor andando y bien configurada la URL en Servidor.kt?)"
    }
}

// Pantalla principal: un título, tres botones (detectar, medir y enviar, historial) y el resultado
@Composable
fun PantallaPrincipal(onVerHistorial: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var resultado by remember { mutableStateOf("Tocá el botón para detectar la conexión") }
    var leyendo by remember { mutableStateOf(false) }
    var midiendo by remember { mutableStateOf(false) }
    var distancia by remember { mutableStateOf("") }
    val ocupado = leyendo || midiendo

    // Pide los permisos de ubicación y teléfono al abrir la app
    val pedirPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        pedirPermisos.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Medidor de Conexión", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "Wi-Fi y datos móviles",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 1. Detectar conexión: tal como lo hizo Solange
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !ocupado,
            onClick = {
                leyendo = true
                // La ubicación tarda un momento, por eso se lee "en segundo plano"
                scope.launch {
                    val red = leerDatosRed(context)
                    val contexto = leerContexto(context)
                    resultado = describir(red) + "\n\n" + describirContexto(contexto)
                    leyendo = false
                }
            }
        ) {
            Text(if (leyendo) "Leyendo..." else "Detectar conexión")
        }

        // 2. Medir: ping, descarga y subida; los resultados se envían al servidor
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = distancia,
            onValueChange = { distancia = it },
            label = { Text("Distancia al router en metros (solo Wi-Fi)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            enabled = !ocupado
        )
        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !ocupado,
            onClick = {
                midiendo = true
                // Las pruebas tardan unos 30 segundos, por eso se hacen "en segundo plano"
                scope.launch {
                    resultado = medirYEnviar(
                        context,
                        distancia.replace(',', '.').toDoubleOrNull()
                    ) { resultado = it }
                    midiendo = false
                }
            }
        ) {
            Text(if (midiendo) "Midiendo..." else "Medir y enviar al servidor")
        }

        // 3. Historial de mediciones guardadas
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !ocupado,
            onClick = onVerHistorial
        ) {
            Text("Ver historial")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(text = resultado, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
        }
    }
}