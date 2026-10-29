package com.tfi.medidorconexion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalidadTest {

    @Test
    fun calidadWifi_segunRssi() {
        assertEquals(Calidad.EXCELENTE, calidadSenal("WIFI", -47))
        assertEquals(Calidad.BUENA, calidadSenal("WIFI", -55))
        assertEquals(Calidad.REGULAR, calidadSenal("WIFI", -65))
        assertEquals(Calidad.DEBIL, calidadSenal("WIFI", -80))
    }

    @Test
    fun calidadMovil_usaOtraEscala() {
        // -90 dBm es débil en Wi-Fi pero buena en datos móviles (RSRP)
        assertEquals(Calidad.DEBIL, calidadSenal("WIFI", -90))
        assertEquals(Calidad.BUENA, calidadSenal("MOVIL", -90))
    }

    @Test
    fun calidad_sinDato_esNula() {
        assertNull(calidadSenal("WIFI", null))
    }

    @Test
    fun nivelDeSenal_estaEntreCeroYUno() {
        assertEquals(0f, nivelSenal("WIFI", -120), 0f)   // peor que el mínimo: queda en 0
        assertEquals(1f, nivelSenal("WIFI", -30), 0f)    // mejor que el máximo: queda en 1
        assertEquals(0.5f, nivelSenal("WIFI", -65), 0.001f)
        assertEquals(0.5f, nivelSenal("MOVIL", -95), 0.001f)
    }

    @Test
    fun datosExtraidos_wifiTrae_red_banda_senal_y_enlace() {
        val red = DatosRed(
            tipoConexion = "WIFI", nombreRed = "Casa", frecuenciaMhz = 5180, banda = "5 GHz",
            rssiDbm = -47, velocidadEnlaceMbps = 433
        )
        val contexto = ContextoMedicion("motorola", "moto g32", "29/10/2026 21:10:00", -26.83, -65.2)
        val datos = datosExtraidos(red, contexto).associate { it.titulo to it.valor }
        assertEquals("Wi-Fi", datos["Conexión"])
        assertEquals("Casa", datos["Red"])
        assertEquals("5 GHz", datos["Banda"])
        assertEquals("5180 MHz", datos["Frecuencia"])
        assertEquals("-47 dBm", datos["Señal"])
        assertEquals("433 Mbps", datos["Velocidad de enlace"])
        assertEquals("motorola moto g32", datos["Dispositivo"])
    }

    @Test
    fun datosExtraidos_sinConexion_noPideContexto() {
        val datos = datosExtraidos(DatosRed(tipoConexion = "SIN_CONEXION"), null)
        assertEquals(1, datos.size)
        assertEquals("Sin conexión a Internet", datos.first().valor)
    }

    @Test
    fun colorDeNivel_vaDelRojoAlVerde() {
        assertEquals(colorDe(Calidad.DEBIL), colorDeNivel(0f))
        assertEquals(colorDe(Calidad.REGULAR), colorDeNivel(0.4f))
        assertEquals(colorDe(Calidad.BUENA), colorDeNivel(0.7f))
        assertEquals(colorDe(Calidad.EXCELENTE), colorDeNivel(1f))
        // Fuera de rango se queda en los extremos
        assertEquals(colorDe(Calidad.DEBIL), colorDeNivel(-3f))
        assertEquals(colorDe(Calidad.EXCELENTE), colorDeNivel(5f))
    }

    @Test
    fun escalaDelVelocimetro() {
        assertEquals(50.0, escalaVelocimetro(30.0), 0.0)
        assertEquals(100.0, escalaVelocimetro(85.0), 0.0)
        assertEquals(1000.0, escalaVelocimetro(1000.0), 0.0)
        assertEquals(2000.0, escalaVelocimetro(1500.0), 0.0)
    }

    private fun medicion(tipo: String, banda: String?, descarga: Double) = MedicionEnvio(
        fechaHora = "2026-10-29T00:00:00.000Z", marca = "m", modelo = "x", tipoConexion = tipo,
        latitud = null, longitud = null, descargaMbps = descarga, subidaMbps = descarga / 2,
        pingMs = 10.0, jitterMs = 1.0, ssid = null, banda = banda, rssiDbm = null,
        enlaceMbps = null, distanciaM = null, operador = null, redMovil = null,
        rsrpDbm = null, snrDb = null
    )

    @Test
    fun resultadoDeMedicionWifi_traeCondicionVelocidadesYFichas() {
        val m = medicion("WIFI", "5 GHz", 100.0).copy(
            rssiDbm = -47, ssid = "Casa", enlaceMbps = 433, distanciaM = 3.0, proveedor = "Personal",
            fechaHora = "2026-10-29T00:10:00.000Z"
        )
        val r = resultadoDeMedicion(m)
        assertEquals(Calidad.EXCELENTE, r.calidad)
        assertEquals(-47, r.senalDbm)
        assertEquals(100.0, r.velocidades!!.descargaMbps, 0.0)
        val fichas = r.datos.associate { it.titulo to it.valor }
        assertEquals("Casa", fichas["Red"])
        assertEquals("3,0 m".replace(',', '.'), fichas["Distancia al router"]?.replace(',', '.'))
        assertEquals("Personal", fichas["Proveedor"])
    }

    @Test
    fun resultadoDeMedicionMovil_usaRsrpYNoMuestraCamposDeWifi() {
        val m = medicion("MOVIL", null, 20.0).copy(operador = "Personal", redMovil = "4G", rsrpDbm = -90, snrDb = 8)
        val r = resultadoDeMedicion(m)
        assertEquals(Calidad.BUENA, r.calidad)
        val titulos = r.datos.map { it.titulo }
        assertEquals(false, titulos.contains("Banda"))
        assertEquals("4G", r.datos.first { it.titulo == "Tipo de red" }.valor)
    }

    @Test
    fun soloDeEsteDispositivo_filtraPorMarcaYModelo() {
        val mias = medicion("WIFI", "5 GHz", 50.0).copy(marca = "motorola", modelo = "moto g32")
        val deOtra = medicion("WIFI", "5 GHz", 90.0).copy(marca = "samsung", modelo = "A54")
        val otroModelo = medicion("MOVIL", null, 20.0).copy(marca = "motorola", modelo = "moto g60")
        val resultado = soloDeEsteDispositivo(listOf(mias, deOtra, otroModelo), "Motorola", "MOTO G32")
        assertEquals(listOf(mias), resultado)   // sin importar mayúsculas
    }

    @Test
    fun resumen_calculaPromediosYMejoresValores() {
        val datos = listOf(
            medicion("WIFI", "5 GHz", 100.0).copy(rssiDbm = -40),
            medicion("WIFI", "2,4 GHz", 50.0).copy(rssiDbm = -60),
            medicion("MOVIL", null, 30.0)   // sin señal Wi-Fi: no entra en ese promedio
        )
        val r = resumenDeMediciones(datos).associate { it.titulo to it.valor }
        assertEquals("3", r["Mediciones"])
        assertEquals("%.1f Mbps".format(60.0), r["Descarga promedio"])   // (100 + 50 + 30) / 3
        assertEquals("%.1f Mbps".format(30.0), r["Subida promedio"])     // la subida del helper es descarga / 2
        assertEquals("%.0f dBm".format(-50.0), r["Señal Wi-Fi promedio"])
        assertEquals("%.1f Mbps".format(100.0), r["Mejor descarga"])
    }

    @Test
    fun promedios_calculaPromediosYMejores_oNuloSinMediciones() {
        val datos = listOf(
            medicion("WIFI", "5 GHz", 100.0),   // subida 50, ping 10, jitter 1
            medicion("WIFI", "5 GHz", 60.0)     // subida 30
        )
        val p = calcularPromedios(datos)!!
        assertEquals(80.0, p.descarga, 0.0001)
        assertEquals(40.0, p.subida, 0.0001)
        assertEquals(10.0, p.ping, 0.0001)
        assertEquals(1.0, p.jitter, 0.0001)
        assertEquals(100.0, p.mejorDescarga, 0.0)
        assertEquals(50.0, p.mejorSubida, 0.0)
        assertNull(calcularPromedios(emptyList()))
    }

    @Test
    fun filtros_porConexionBandaOperador() {
        val wifi5 = medicion("WIFI", "5 GHz", 100.0)
        val wifi24 = medicion("WIFI", "2,4 GHz", 40.0)
        val movil = medicion("MOVIL", null, 20.0).copy(operador = "Personal")
        val todas = listOf(wifi5, wifi24, movil)

        assertEquals(todas, filtrarMediciones(todas, FiltroHistorial()))
        assertEquals(listOf(wifi5, wifi24), filtrarMediciones(todas, FiltroHistorial(tipo = "WIFI")))
        assertEquals(listOf(wifi5), filtrarMediciones(todas, FiltroHistorial(banda = "5 GHz")))
        assertEquals(listOf(movil), filtrarMediciones(todas, FiltroHistorial(operador = "Personal")))
        assertEquals(emptyList<MedicionEnvio>(), filtrarMediciones(todas, FiltroHistorial(tipo = "MOVIL", banda = "5 GHz")))
    }

    @Test
    fun filtros_porFecha() {
        val zona = java.time.ZoneId.of("America/Argentina/Tucuman")
        val ahora = java.time.Instant.parse("2026-10-29T15:00:00Z")   // 12:00 del 29/10 en Tucumán
        val deHoy = medicion("WIFI", "5 GHz", 10.0).copy(fechaHora = "2026-10-29T13:00:00Z")
        val dePocosDias = medicion("WIFI", "5 GHz", 20.0).copy(fechaHora = "2026-10-25T13:00:00Z")
        val deHaceUnMes = medicion("WIFI", "5 GHz", 30.0).copy(fechaHora = "2026-09-01T13:00:00Z")
        val todas = listOf(deHoy, dePocosDias, deHaceUnMes)

        fun con(rango: RangoFecha) = filtrarMediciones(todas, FiltroHistorial(fecha = rango), ahora, zona)
        assertEquals(listOf(deHoy), con(RangoFecha.HOY))
        assertEquals(listOf(deHoy, dePocosDias), con(RangoFecha.SIETE_DIAS))
        assertEquals(listOf(deHoy, dePocosDias), con(RangoFecha.TREINTA_DIAS))
        assertEquals(todas, con(RangoFecha.TODAS))
    }

    @Test
    fun filtro_activo_soloSiHayAlgoElegido() {
        assertEquals(false, FiltroHistorial().activo)
        assertEquals(true, FiltroHistorial(tipo = "WIFI").activo)
        assertEquals(true, FiltroHistorial(fecha = RangoFecha.HOY).activo)
    }

    @Test
    fun resumen_sinMediciones_esVacio() {
        assertEquals(emptyList<DatoVisual>(), resumenDeMediciones(emptyList()))
    }

    @Test
    fun soloDeEsteDispositivo_conIdentificador_separaCelularesDelMismoModelo() {
        val mio = medicion("WIFI", "5 GHz", 50.0).copy(marca = "motorola", modelo = "moto g32", dispositivoId = "MI-ID")
        val gemelo = medicion("WIFI", "5 GHz", 90.0).copy(marca = "motorola", modelo = "moto g32", dispositivoId = "OTRO-ID")
        val viejaMia = medicion("WIFI", "5 GHz", 20.0).copy(marca = "motorola", modelo = "moto g32")   // sin identificador
        val viejaAjena = medicion("WIFI", "5 GHz", 30.0).copy(marca = "samsung", modelo = "A54")
        val resultado = soloDeEsteDispositivo(listOf(mio, gemelo, viejaMia, viejaAjena), "motorola", "moto g32", "MI-ID")
        // El gemelo (mismo modelo, otro identificador) no entra; la vieja sin identificador sí, por marca y modelo
        assertEquals(listOf(mio, viejaMia), resultado)
    }

    @Test
    fun promedioPorBanda_ignoraLasQueNoTienenBanda() {
        val datos = listOf(
            medicion("WIFI", "5 GHz", 100.0),
            medicion("WIFI", "5 GHz", 60.0),
            medicion("WIFI", "2,4 GHz", 30.0),
            medicion("MOVIL", null, 20.0)
        )
        val promedios = promedioPor(datos, { it.banda }) { it.descargaMbps }
        assertEquals(mapOf("5 GHz" to 80.0, "2,4 GHz" to 30.0), promedios)
    }
}
