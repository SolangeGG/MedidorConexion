package com.tfi.medidorconexion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Datos generales de cada medición: dispositivo, fecha/hora y ubicación
data class ContextoMedicion(
    val marca: String,
    val modelo: String,
    val fechaHora: String,
    val latitud: Double?,
    val longitud: Double?
)

suspend fun leerContexto(context: Context): ContextoMedicion {
    val ubicacion = obtenerUbicacion(context)
    return ContextoMedicion(
        marca = Build.MANUFACTURER,
        modelo = Build.MODEL,
        fechaHora = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
        latitud = ubicacion?.latitude,
        longitud = ubicacion?.longitude
    )
}

// Pide la ubicación actual al servicio de ubicación de Google (Fused Location Provider)
@SuppressLint("MissingPermission")
private suspend fun obtenerUbicacion(context: Context): Location? {
    if (!tienePermiso(context, Manifest.permission.ACCESS_COARSE_LOCATION)) return null
    val cliente = LocationServices.getFusedLocationProviderClient(context)
    return try {
        // Alta precisión: usa el GPS (el emulador solo simula GPS)
        cliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            ?: cliente.lastLocation.await()   // si no hay, usa la última conocida
    } catch (e: Exception) {
        null
    }
}

// Devuelve true si el usuario aceptó el permiso
fun tienePermiso(context: Context, permiso: String): Boolean =
    ContextCompat.checkSelfPermission(context, permiso) == PackageManager.PERMISSION_GRANTED

fun describirContexto(c: ContextoMedicion): String {
    val ubicacion = if (c.latitud != null && c.longitud != null)
        "%.5f, %.5f".format(c.latitud, c.longitud)
    else
        "no disponible"
    return """
        Dispositivo: ${c.marca} ${c.modelo}
        Fecha y hora: ${c.fechaHora}
        Ubicación: $ubicacion
    """.trimIndent()
}