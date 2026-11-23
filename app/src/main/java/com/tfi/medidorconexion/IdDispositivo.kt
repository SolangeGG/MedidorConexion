package com.tfi.medidorconexion

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.util.UUID

// Identificador propio de este celular: se envía con cada medición y sirve para mostrar solo las suyas.
// Usa el ANDROID_ID, que es distinto en cada dispositivo y no cambia al reinstalar la app;
// si el sistema no lo informa, se genera uno al azar y se guarda.
@SuppressLint("HardwareIds")
fun idDelDispositivo(context: Context): String {
    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    if (!androidId.isNullOrBlank()) return androidId

    val preferencias = context.getSharedPreferences("medidor_conexion", Context.MODE_PRIVATE)
    return preferencias.getString("id_dispositivo", null)
        ?: UUID.randomUUID().toString().also { preferencias.edit().putString("id_dispositivo", it).apply() }
}
