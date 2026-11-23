package com.tfi.medidorconexion

// Un dato extraído del celular o de la red, listo para mostrarse en una ficha
data class DatoVisual(val icono: String, val titulo: String, val valor: String)

// Promedios de todas las mediciones y los mejores valores de velocidad
data class Promedios(
    val descarga: Double,
    val subida: Double,
    val ping: Double,
    val jitter: Double,
    val mejorDescarga: Double,
    val mejorSubida: Double
)

fun calcularPromedios(mediciones: List<MedicionEnvio>): Promedios? {
    if (mediciones.isEmpty()) return null
    return Promedios(
        descarga = mediciones.map { it.descargaMbps }.average(),
        subida = mediciones.map { it.subidaMbps }.average(),
        ping = mediciones.map { it.pingMs }.average(),
        jitter = mediciones.map { it.jitterMs }.average(),
        mejorDescarga = mediciones.maxOf { it.descargaMbps },
        mejorSubida = mediciones.maxOf { it.subidaMbps }
    )
}

// Resumen de todas las mediciones: cantidad, promedios, señal Wi-Fi promedio y los mejores valores
fun resumenDeMediciones(mediciones: List<MedicionEnvio>): List<DatoVisual> {
    val p = calcularPromedios(mediciones) ?: return emptyList()
    val datos = mutableListOf(
        DatoVisual("📊", "Mediciones", "${mediciones.size}"),
        DatoVisual("⬇", "Descarga promedio", "%.1f Mbps".format(p.descarga)),
        DatoVisual("⬆", "Subida promedio", "%.1f Mbps".format(p.subida)),
        DatoVisual("⏱", "Ping promedio", "%.1f ms".format(p.ping)),
        DatoVisual("〰", "Jitter promedio", "%.1f ms".format(p.jitter))
    )
    val senalesWifi = mediciones.filter { it.tipoConexion == "WIFI" }.mapNotNull { it.rssiDbm }
    if (senalesWifi.isNotEmpty()) {
        datos += DatoVisual("📉", "Señal Wi-Fi promedio", "%.0f dBm".format(senalesWifi.average()))
    }
    datos += DatoVisual("🏆", "Mejor descarga", "%.1f Mbps".format(p.mejorDescarga))
    datos += DatoVisual("🚀", "Mejor subida", "%.1f Mbps".format(p.mejorSubida))
    return datos
}

// El servidor guarda las mediciones de todos los celulares; cada uno ve solo las suyas.
// Si la medición trae identificador, tiene que ser el de este celular. Las mediciones viejas, que no lo
// tienen, se reconocen por marca y modelo para no perder el historial anterior.
fun soloDeEsteDispositivo(
    mediciones: List<MedicionEnvio>,
    marca: String,
    modelo: String,
    id: String? = null
): List<MedicionEnvio> = mediciones.filter { m ->
    if (id != null && m.dispositivoId != null) {
        m.dispositivoId == id
    } else {
        m.marca.equals(marca, ignoreCase = true) && m.modelo.equals(modelo, ignoreCase = true)
    }
}

// Las mismas fichas, pero a partir de una medición guardada en el servidor
fun datosDeMedicion(m: MedicionEnvio): List<DatoVisual> {
    val datos = mutableListOf<DatoVisual>()
    if (m.tipoConexion == "WIFI") {
        datos += DatoVisual("🌐", "Conexión", "Wi-Fi")
        datos += DatoVisual("📶", "Red", m.ssid ?: "Oculta")
        datos += DatoVisual("📡", "Banda", m.banda ?: "?")
        datos += DatoVisual("📉", "Señal", m.rssiDbm?.let { "$it dBm" } ?: "No disponible")
        datos += DatoVisual("🔗", "Velocidad de enlace", m.enlaceMbps?.let { "$it Mbps" } ?: "No disponible")
        if (m.distanciaM != null) datos += DatoVisual("📏", "Distancia estimada al router", "%.1f m".format(m.distanciaM))
        if (m.proveedor != null) datos += DatoVisual("🏢", "Proveedor", m.proveedor)
    } else {
        datos += DatoVisual("🌐", "Conexión", "Datos móviles")
        datos += DatoVisual("🏢", "Operador", m.operador ?: "Desconocido")
        datos += DatoVisual("📱", "Tipo de red", m.redMovil ?: "Desconocida")
        datos += DatoVisual("📉", "Señal (RSRP)", m.rsrpDbm?.let { "$it dBm" } ?: "No disponible")
        datos += DatoVisual("🎚", "Señal/ruido", m.snrDb?.let { "$it dB" } ?: "No disponible")
    }
    datos += DatoVisual("📲", "Dispositivo", "${m.marca} ${m.modelo}")
    datos += DatoVisual("🕒", "Fecha y hora", formatearFecha(m.fechaHora))
    val ubicacion = if (m.latitud != null && m.longitud != null)
        "%.5f, %.5f".format(m.latitud, m.longitud)
    else
        "No disponible"
    datos += DatoVisual("📍", "Ubicación", ubicacion)
    return datos
}

// El resultado de una medición guardada, para mostrarlo con el mismo panel que al medir
fun resultadoDeMedicion(m: MedicionEnvio): ResultadoMedicion {
    val senal = if (m.tipoConexion == "WIFI") m.rssiDbm else m.rsrpDbm
    return ResultadoMedicion(
        mensaje = "Medición del ${formatearFecha(m.fechaHora)}",
        datos = datosDeMedicion(m),
        velocidades = Velocidades(m.descargaMbps, m.subidaMbps, m.pingMs, m.jitterMs),
        tipoConexion = m.tipoConexion,
        calidad = calidadSenal(m.tipoConexion, senal),
        senalDbm = senal
    )
}

// Ordena en fichas lo que se leyó de la red y del dispositivo (la ubicación y la fecha pueden faltar)
fun datosExtraidos(red: DatosRed, contexto: ContextoMedicion?): List<DatoVisual> {
    val datos = mutableListOf<DatoVisual>()
    when (red.tipoConexion) {
        "WIFI" -> {
            datos += DatoVisual("🌐", "Conexión", "Wi-Fi")
            datos += DatoVisual("📶", "Red", red.nombreRed ?: "Oculta (falta permiso)")
            datos += DatoVisual("📡", "Banda", red.banda ?: "?")
            datos += DatoVisual("〰", "Frecuencia", red.frecuenciaMhz?.let { "$it MHz" } ?: "?")
            datos += DatoVisual("📉", "Señal", red.rssiDbm?.let { "$it dBm" } ?: "No disponible")
            datos += DatoVisual("🔗", "Velocidad de enlace", red.velocidadEnlaceMbps?.let { "$it Mbps" } ?: "No disponible")
        }
        "MOVIL" -> {
            datos += DatoVisual("🌐", "Conexión", "Datos móviles")
            datos += DatoVisual("🏢", "Operador", red.operador ?: "Desconocido")
            datos += DatoVisual("📱", "Tipo de red", red.redMovil ?: "Falta permiso de teléfono")
            datos += DatoVisual("📉", "Señal (RSRP)", red.rsrpDbm?.let { "$it dBm" } ?: "No disponible")
            datos += DatoVisual("🎚", "Señal/ruido", red.snrDb?.let { "$it dB" } ?: "No disponible")
        }
        "SIN_CONEXION" -> datos += DatoVisual("🚫", "Conexión", "Sin conexión a Internet")
        else -> datos += DatoVisual("🌐", "Conexión", "Otro tipo de conexión")
    }
    if (contexto != null) {
        datos += DatoVisual("📲", "Dispositivo", "${contexto.marca} ${contexto.modelo}")
        datos += DatoVisual("🕒", "Fecha y hora", contexto.fechaHora)
        val ubicacion = if (contexto.latitud != null && contexto.longitud != null)
            "%.5f, %.5f".format(contexto.latitud, contexto.longitud)
        else
            "No disponible"
        datos += DatoVisual("📍", "Ubicación", ubicacion)
    }
    return datos
}
