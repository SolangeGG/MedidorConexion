package com.tfi.medidorconexion

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object Servidor {
    // Dirección del servidor, terminada en "/". Según dónde corra:
    //   emulador de Android Studio -> "http://10.0.2.2:3000/"  (10.0.2.2 es la PC que aloja el emulador)
    //   celular real con el servidor en la PC -> "http://IP-DE-LA-PC:3000/" (misma red Wi-Fi)
    //   servidor desplegado en Render -> "https://NOMBRE.onrender.com/"
    const val URL_BASE = "http://192.168.1.10:3000/"
}

// JSON que espera POST /mediciones (ver servidor/README.md); los nulos no se envían
data class MedicionEnvio(
    @SerializedName("fecha_hora") val fechaHora: String,
    val marca: String,
    val modelo: String,
    @SerializedName("tipo_conexion") val tipoConexion: String,
    val latitud: Double?,
    val longitud: Double?,
    @SerializedName("descarga_mbps") val descargaMbps: Double,
    @SerializedName("subida_mbps") val subidaMbps: Double,
    @SerializedName("ping_ms") val pingMs: Double,
    @SerializedName("jitter_ms") val jitterMs: Double,
    // Solo Wi-Fi
    val ssid: String?,
    val banda: String?,
    @SerializedName("rssi_dbm") val rssiDbm: Int?,
    @SerializedName("enlace_mbps") val enlaceMbps: Int?,
    @SerializedName("distancia_m") val distanciaM: Double?,
    // Solo datos móviles
    val operador: String?,
    @SerializedName("red_movil") val redMovil: String?,
    @SerializedName("rsrp_dbm") val rsrpDbm: Int?,
    @SerializedName("snr_db") val snrDb: Int?,
    // Lo calcula el servidor a partir de la IP; la app no lo envía, solo lo recibe en el historial
    val proveedor: String? = null,
    // Identifica al celular que midió; el historial muestra solo las mediciones del propio celular
    @SerializedName("dispositivo_id") val dispositivoId: String? = null
)

interface ApiServidor {
    @POST("mediciones")
    suspend fun enviarMedicion(@Body medicion: MedicionEnvio)

    // Historial guardado, de la más nueva a la más vieja
    @GET("mediciones?limite=500")
    suspend fun listarMediciones(): List<MedicionEnvio>
}

private val api: ApiServidor = Retrofit.Builder()
    .baseUrl(Servidor.URL_BASE)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(ApiServidor::class.java)

suspend fun obtenerHistorial(): List<MedicionEnvio> = api.listarMediciones()

// Arma el JSON con todo lo medido y lo envía al servidor (lanza excepción si falla)
suspend fun enviarMedicion(
    red: DatosRed,
    contexto: ContextoMedicion,
    ping: ResultadoPing,
    descargaMbps: Double,
    subidaMbps: Double,
    distanciaM: Double?,
    idDispositivo: String
) {
    val esWifi = red.tipoConexion == "WIFI"
    api.enviarMedicion(
        MedicionEnvio(
            fechaHora = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            marca = contexto.marca,
            modelo = contexto.modelo,
            tipoConexion = red.tipoConexion,
            latitud = contexto.latitud,
            longitud = contexto.longitud,
            descargaMbps = descargaMbps,
            subidaMbps = subidaMbps,
            pingMs = ping.promedioMs,
            jitterMs = ping.jitterMs,
            ssid = red.nombreRed.takeIf { esWifi },
            banda = red.banda.takeIf { esWifi },
            rssiDbm = red.rssiDbm.takeIf { esWifi },
            enlaceMbps = red.velocidadEnlaceMbps.takeIf { esWifi },
            distanciaM = distanciaM.takeIf { esWifi },
            operador = red.operador,
            redMovil = red.redMovil,
            rsrpDbm = red.rsrpDbm,
            snrDb = red.snrDb,
            dispositivoId = idDispositivo
        )
    )
}
