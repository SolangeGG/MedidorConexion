package com.tfi.medidorconexion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.CellInfo
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager

// Todos los datos de la red que vamos a registrar en cada medición
data class DatosRed(
    val tipoConexion: String,            // "WIFI", "MOVIL" o "SIN_CONEXION"
    // Solo Wi-Fi
    val nombreRed: String? = null,
    val frecuenciaMhz: Int? = null,
    val banda: String? = null,
    val rssiDbm: Int? = null,
    val velocidadEnlaceMbps: Int? = null,
    // Solo datos móviles
    val operador: String? = null,
    val redMovil: String? = null,        // "4G", "5G", etc.
    val rsrpDbm: Int? = null,
    val snrDb: Int? = null
)

// Punto de entrada: detecta el tipo de conexión y lee los datos que correspondan
fun leerDatosRed(context: Context): DatosRed {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val redActiva = connectivityManager.activeNetwork
        ?: return DatosRed(tipoConexion = "SIN_CONEXION")
    val capacidades = connectivityManager.getNetworkCapabilities(redActiva)
        ?: return DatosRed(tipoConexion = "SIN_CONEXION")

    return when {
        capacidades.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> leerDatosWifi(context)
        capacidades.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> leerDatosMovil(context)
        else -> DatosRed(tipoConexion = "OTRA")
    }
}

// Datos del Wi-Fi: nombre, frecuencia, banda, señal (RSSI) y velocidad de enlace
@Suppress("DEPRECATION")
private fun leerDatosWifi(context: Context): DatosRed {
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val info = wifiManager.connectionInfo

    // Android oculta el nombre de la red si la app no tiene permiso de ubicación
    val nombre = info.ssid.removeSurrounding("\"")

    return DatosRed(
        tipoConexion = "WIFI",
        nombreRed = if (nombre == "<unknown ssid>") null else nombre,
        frecuenciaMhz = info.frequency,
        banda = calcularBanda(info.frequency),
        rssiDbm = info.rssi,
        velocidadEnlaceMbps = info.linkSpeed
    )
}

// Convierte la frecuencia (MHz) en la banda Wi-Fi
fun calcularBanda(frecuenciaMhz: Int): String = when (frecuenciaMhz) {
    in 2400..2500 -> "2,4 GHz"
    in 4900..5900 -> "5 GHz"
    in 5925..7125 -> "6 GHz"
    else -> "Desconocida"
}

// Datos de la red móvil: operador, tipo de red, señal (RSRP) y relación señal/ruido
private fun leerDatosMovil(context: Context): DatosRed {
    val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    var rsrp: Int? = null
    var snr: Int? = null

    val senales = telephonyManager.signalStrength?.cellSignalStrengths ?: emptyList()
    for (senal in senales) {
        when (senal) {
            is CellSignalStrengthNr -> {           // 5G
                rsrp = valorValido(senal.ssRsrp)
                snr = valorValido(senal.ssSinr)
            }
            is CellSignalStrengthLte -> {          // 4G
                rsrp = valorValido(senal.rsrp)
                snr = valorValido(senal.rssnr)
            }
        }
        if (rsrp != null) break
    }

    return DatosRed(
        tipoConexion = "MOVIL",
        operador = telephonyManager.networkOperatorName.ifBlank { null },
        redMovil = leerTipoRedMovil(context, telephonyManager),
        rsrpDbm = rsrp,
        snrDb = snr
    )
}

// Traduce el tipo de red que informa Android a 2G / 3G / 4G / 5G
@SuppressLint("MissingPermission")
private fun leerTipoRedMovil(context: Context, telephonyManager: TelephonyManager): String? {
    if (!tienePermiso(context, Manifest.permission.READ_PHONE_STATE)) return null
    return when (telephonyManager.dataNetworkType) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
        else -> "Desconocida"
    }
}

// Android usa un número especial cuando un dato no está disponible
private fun valorValido(valor: Int): Int? =
    if (valor == CellInfo.UNAVAILABLE) null else valor

// Arma el texto que se muestra en pantalla
fun describir(datos: DatosRed): String = when (datos.tipoConexion) {
    "WIFI" -> """
        Conexión: Wi-Fi
        Red: ${datos.nombreRed ?: "(oculta: falta permiso de ubicación)"}
        Banda: ${datos.banda} (${datos.frecuenciaMhz} MHz)
        Señal: ${datos.rssiDbm} dBm
        Velocidad de enlace: ${datos.velocidadEnlaceMbps} Mbps
    """.trimIndent()

    "MOVIL" -> """
        Conexión: datos móviles
        Operador: ${datos.operador ?: "desconocido"}
        Tipo de red: ${datos.redMovil ?: "(falta permiso de teléfono)"}
        Señal (RSRP): ${datos.rsrpDbm?.let { "$it dBm" } ?: "no disponible"}
        Relación señal/ruido: ${datos.snrDb?.let { "$it dB" } ?: "no disponible"}
    """.trimIndent()

    "SIN_CONEXION" -> "Sin conexión a Internet"
    else -> "Otro tipo de conexión"
}