package com.tfi.medidorconexion

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tfi.medidorconexion.ui.theme.Marino
import kotlinx.coroutines.launch

private val FORMA_BOTON = RoundedCornerShape(16.dp)

// Una opción grande del menú de inicio: ícono, título, descripción y una flecha
@Composable
private fun OpcionMenu(icono: String, titulo: String, descripcion: String, onClick: () -> Unit) {
    Tarjeta(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(icono, fontSize = 30.sp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(titulo, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Marino)
                Text(descripcion, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 30.sp, color = Marino)
        }
    }
}

// Lo primero que se ve después de la bienvenida: dos opciones, Detectar conexión y Medir
@Composable
fun PantallaInicio(
    modifier: Modifier = Modifier,
    calidad: Calidad? = null,
    onDetectar: () -> Unit,
    onMedir: () -> Unit
) {
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

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EncabezadoApp("Medidor de Conexión", "Wi-Fi y datos móviles", calidad = calidad)

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "¿Qué querés hacer?",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Marino,
                modifier = Modifier.padding(top = 8.dp)
            )
            OpcionMenu(
                icono = "📡",
                titulo = "Detectar conexión",
                descripcion = "Mirá los datos de tu red y de tu dispositivo.",
                onClick = onDetectar
            )
            OpcionMenu(
                icono = "🚀",
                titulo = "Medir",
                descripcion = "Calculá la subida, la bajada, el ping y el jitter, y guardá la medición.",
                onClick = onMedir
            )
        }
    }
}

// Detectar conexión: al entrar lee la red y el dispositivo y muestra solo esos datos, sin velocidades
@Composable
fun PantallaDetectar(
    modifier: Modifier = Modifier,
    calidad: Calidad? = null,
    onCalidad: (Calidad?) -> Unit,
    onVolver: () -> Unit
) {
    BackHandler(onBack = onVolver)
    val context = LocalContext.current
    var resultado by remember { mutableStateOf<ResultadoMedicion?>(null) }
    var lecturas by remember { mutableIntStateOf(0) }

    // La ubicación tarda un momento, por eso se lee "en segundo plano"
    LaunchedEffect(lecturas) {
        resultado = null
        val red = leerDatosRed(context)
        val contexto = leerContexto(context)
        val senal = if (red.tipoConexion == "WIFI") red.rssiDbm else red.rsrpDbm
        val condicion = calidadSenal(red.tipoConexion, senal)
        onCalidad(condicion)
        val hayConexion = red.tipoConexion == "WIFI" || red.tipoConexion == "MOVIL"
        resultado = ResultadoMedicion(
            mensaje = if (hayConexion) "Conexión detectada" else "No hay una conexión activa",
            exito = hayConexion,
            datos = datosExtraidos(red, contexto),
            tipoConexion = red.tipoConexion,
            calidad = condicion,
            senalDbm = senal
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        EncabezadoApp("Detectar conexión", "Datos de tu red y de tu dispositivo", calidad = calidad)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    onClick = onVolver
                ) { Text("←  Volver", fontWeight = FontWeight.SemiBold) }
                FilledTonalButton(
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = resultado != null,
                    onClick = { lecturas++ }
                ) { Text("🔄  Actualizar", fontWeight = FontWeight.SemiBold) }
            }

            val r = resultado
            if (r == null) {
                Tarjeta(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Detectando la conexión...", fontSize = 16.sp, color = Marino)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                PanelResultado(r)
            }
        }
    }
}

// Medir: se calculan la subida, la bajada, el ping y el jitter, y la distancia al router se estima con el RSSI;
// la medición se envía al servidor. Mientras mide no se muestra ningún dato, solo el avance.
@Composable
fun PantallaMedir(
    modifier: Modifier = Modifier,
    calidad: Calidad? = null,
    onCalidad: (Calidad?) -> Unit,
    onVolver: () -> Unit
) {
    BackHandler(onBack = onVolver)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var etapa by remember { mutableStateOf("") }
    var medicion by remember { mutableStateOf<ResultadoMedicion?>(null) }
    var midiendo by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        EncabezadoApp("Medir", "Subida, bajada, ping y jitter", calidad = calidad)

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
                enabled = !midiendo,
                onClick = onVolver
            ) { Text("←  Volver", fontWeight = FontWeight.SemiBold) }

            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = FORMA_BOTON,
                enabled = !midiendo,
                onClick = {
                    midiendo = true
                    medicion = null
                    // Las pruebas tardan unos 30 segundos, por eso se hacen "en segundo plano"
                    scope.launch {
                        val terminada = medirYEnviar(context) { etapa = it }
                        medicion = terminada
                        onCalidad(terminada.calidad)
                        midiendo = false
                    }
                }
            ) {
                Text(
                    if (midiendo) "Midiendo..." else "🚀  Medir y enviar al servidor",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val terminada = medicion
            when {
                midiendo -> Tarjeta(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Midiendo tu conexión", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Marino)
                        Text(etapa, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                terminada != null -> PanelResultado(terminada)
                else -> Tarjeta(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Tocá \"Medir y enviar al servidor\". Se calculan la subida, la bajada, el ping y el jitter, " +
                            "se estima la distancia al router con la señal y la medición se guarda en el servidor.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
