package com.tfi.medidorconexion

import kotlin.math.ceil

enum class Calidad(val etiqueta: String) {
    EXCELENTE("Excelente"),
    BUENA("Buena"),
    REGULAR("Regular"),
    DEBIL("Débil")
}

// Clasifica el nivel de señal en dBm: en Wi-Fi se usa el RSSI y en datos móviles el RSRP
fun calidadSenal(tipoConexion: String, dbm: Int?): Calidad? {
    if (dbm == null) return null
    val (excelente, buena, regular) =
        if (tipoConexion == "WIFI") Triple(-50, -60, -70) else Triple(-85, -95, -105)
    return when {
        dbm >= excelente -> Calidad.EXCELENTE
        dbm >= buena -> Calidad.BUENA
        dbm >= regular -> Calidad.REGULAR
        else -> Calidad.DEBIL
    }
}

// Posición de la señal en una barra de 0 (muy mala) a 1 (excelente); cada tecnología tiene su rango en dBm
fun nivelSenal(tipoConexion: String, dbm: Int): Float {
    val (peor, mejor) = if (tipoConexion == "WIFI") -90 to -40 else -120 to -70
    return ((dbm - peor).toFloat() / (mejor - peor)).coerceIn(0f, 1f)
}

// Distancia estimada al router a partir del RSSI, con el modelo de pérdida logarítmica:
//   d = 10 ^ ((RSSI a 1 m - RSSI) / (10 * n))
// El RSSI a 1 m es menor en 5 GHz (esa banda pierde más) y n = 3 es un valor típico en interiores con paredes.
// Es solo una estimación: las paredes y las interferencias mueven el RSSI.
fun distanciaEstimadaM(rssiDbm: Int?, banda: String?): Double? {
    if (rssiDbm == null) return null
    val rssiA1m = if (banda == "2,4 GHz") -40.0 else -47.0
    val exponente = 3.0
    return Math.pow(10.0, (rssiA1m - rssiDbm) / (10 * exponente))
}

// Frase corta que acompaña a la condición de la conexión
fun fraseCondicion(calidad: Calidad, tipoConexion: String): String = when (calidad) {
    Calidad.EXCELENTE -> "Señal muy fuerte, ideal para todo uso."
    Calidad.BUENA -> "Buena señal, funciona sin problemas."
    Calidad.REGULAR -> "Señal justa, puede haber lentitud."
    Calidad.DEBIL ->
        if (tipoConexion == "WIFI") "Señal débil: acercate al router." else "Señal débil: buscá mejor cobertura."
}

// Máximo del velocímetro: el menor valor "redondo" que contiene la medición
fun escalaVelocimetro(mbps: Double): Double =
    listOf(50.0, 100.0, 200.0, 500.0, 1000.0).firstOrNull { mbps <= it }
        ?: (ceil(mbps / 1000) * 1000)

// Promedio de un valor por grupo; las mediciones sin clave (por ejemplo, sin banda) se ignoran
fun promedioPor(
    mediciones: List<MedicionEnvio>,
    clave: (MedicionEnvio) -> String?,
    valor: (MedicionEnvio) -> Double
): Map<String, Double> =
    mediciones
        .mapNotNull { m -> clave(m)?.let { it to valor(m) } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.average() }
