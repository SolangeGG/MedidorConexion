package com.tfi.medidorconexion

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random

// Resultados de las pruebas contra el servidor
data class ResultadoPing(val promedioMs: Double, val jitterMs: Double)

private const val MUESTRAS_PING = 10
private const val DURACION_PRUEBA_SEG = 10
private const val DESCARTE_INICIAL_SEG = 1   // arranque lento de TCP: no se cuenta el primer segundo
private const val NANOS_POR_SEG = 1_000_000_000.0

private val cliente = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()

// Velocidad (Mbps) = (bytes × 8) / (segundos × 1.000.000)
fun calcularMbps(bytes: Long, segundos: Double): Double =
    if (segundos <= 0) 0.0 else (bytes * 8) / (segundos * 1_000_000)

// Promedio de las muestras y jitter (variación media entre muestras consecutivas)
fun resumirPing(muestrasMs: List<Double>): ResultadoPing {
    val jitter = muestrasMs.zipWithNext { a, b -> abs(b - a) }.average()
    return ResultadoPing(muestrasMs.average(), if (jitter.isNaN()) 0.0 else jitter)
}

// 10 solicitudes breves a /ping; se mide el tiempo de ida y vuelta de cada una
suspend fun medirPing(): ResultadoPing = withContext(Dispatchers.IO) {
    val pedido = Request.Builder().url(Servidor.URL_BASE + "ping").build()
    val muestras = mutableListOf<Double>()
    repeat(MUESTRAS_PING) {
        val inicio = System.nanoTime()
        cliente.newCall(pedido).execute().use { it.body?.bytes() }
        muestras.add((System.nanoTime() - inicio) / 1_000_000.0)
    }
    resumirPing(muestras)
}

// Lee datos aleatorios de /descarga unos 10 s y devuelve Mbps (sin contar el primer segundo)
suspend fun medirDescarga(): Double = withContext(Dispatchers.IO) {
    val pedido = Request.Builder()
        .url(Servidor.URL_BASE + "descarga?segundos=${DURACION_PRUEBA_SEG + DESCARTE_INICIAL_SEG + 2}")
        .build()
    val buffer = ByteArray(64 * 1024)
    var bytes = 0L
    var inicioMedicion = 0L
    val inicio = System.nanoTime()
    val fin = inicio + (DESCARTE_INICIAL_SEG + DURACION_PRUEBA_SEG) * NANOS_POR_SEG.toLong()

    cliente.newCall(pedido).execute().use { respuesta ->
        val flujo = respuesta.body?.byteStream() ?: error("Respuesta vacía")
        while (true) {
            val ahora = System.nanoTime()
            if (ahora >= fin) break
            val leidos = flujo.read(buffer)
            if (leidos < 0) break
            if (inicioMedicion == 0L && ahora - inicio >= DESCARTE_INICIAL_SEG * NANOS_POR_SEG) {
                inicioMedicion = ahora
            } else if (inicioMedicion != 0L) {
                bytes += leidos
            }
        }
    }
    if (inicioMedicion == 0L) 0.0 else calcularMbps(bytes, (System.nanoTime() - inicioMedicion) / NANOS_POR_SEG)
}

// Cuerpo que genera datos aleatorios en el celular y los envía durante unos 10 s
private class CuerpoSubida : RequestBody() {
    var bytesMedidos = 0L
    var segundosMedidos = 0.0

    override fun contentType() = "application/octet-stream".toMediaType()

    override fun writeTo(sink: BufferedSink) {
        val bloque = Random.nextBytes(64 * 1024)
        val inicio = System.nanoTime()
        val fin = inicio + (DESCARTE_INICIAL_SEG + DURACION_PRUEBA_SEG) * NANOS_POR_SEG.toLong()
        var inicioMedicion = 0L
        while (true) {
            val ahora = System.nanoTime()
            if (ahora >= fin) break
            sink.write(bloque)
            sink.flush()
            if (inicioMedicion == 0L && ahora - inicio >= DESCARTE_INICIAL_SEG * NANOS_POR_SEG) {
                inicioMedicion = System.nanoTime()
            } else if (inicioMedicion != 0L) {
                bytesMedidos += bloque.size
            }
        }
        if (inicioMedicion != 0L) segundosMedidos = (System.nanoTime() - inicioMedicion) / NANOS_POR_SEG
    }
}

// Lo que informa /subida: la velocidad calculada por el servidor con los bytes que efectivamente recibió
private data class RespuestaSubida(val mbps: Double?)

// Envía datos aleatorios a /subida unos 10 s y devuelve Mbps (sin contar el primer segundo).
// El celular solo sabe cuántos bytes entregó a su conexión, no cuántos llegaron, por eso
// se usa la velocidad medida por el servidor; si no la informa, se usa la cuenta del celular.
suspend fun medirSubida(): Double = withContext(Dispatchers.IO) {
    val cuerpo = CuerpoSubida()
    val pedido = Request.Builder().url(Servidor.URL_BASE + "subida").post(cuerpo).build()
    val texto = cliente.newCall(pedido).execute().use { it.body?.string() }
    val mbpsServidor = try {
        Gson().fromJson(texto, RespuestaSubida::class.java)?.mbps
    } catch (e: Exception) {
        null
    }
    mbpsServidor ?: calcularMbps(cuerpo.bytesMedidos, cuerpo.segundosMedidos)
}
