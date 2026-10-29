package com.tfi.medidorconexion

import org.junit.Assert.assertEquals
import org.junit.Test

class PruebasRedTest {

    @Test
    fun velocidad_ejemploDelInforme() {
        // 56.250.000 bytes en 9 s = 50 Mbps
        assertEquals(50.0, calcularMbps(56_250_000, 9.0), 0.001)
    }

    @Test
    fun velocidad_conTiempoCero_esCero() {
        assertEquals(0.0, calcularMbps(1000, 0.0), 0.0)
    }

    @Test
    fun ping_promedioYJitter() {
        val r = resumirPing(listOf(20.0, 30.0, 20.0, 30.0))
        assertEquals(25.0, r.promedioMs, 0.001)
        assertEquals(10.0, r.jitterMs, 0.001)
    }

    @Test
    fun fecha_seConvierteDeUtcALaHoraLocal() {
        val tucuman = java.time.ZoneId.of("America/Argentina/Tucuman")
        assertEquals("28/10/2026 21:10", formatearFecha("2026-10-29T00:10:00.000Z", tucuman))
    }

    @Test
    fun fecha_invalida_sedevuelveTalCual() {
        assertEquals("hola", formatearFecha("hola"))
    }

    @Test
    fun ping_unaSolaMuestra_jitterCero() {
        assertEquals(0.0, resumirPing(listOf(42.0)).jitterMs, 0.0)
    }
}
