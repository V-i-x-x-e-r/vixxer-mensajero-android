package dev.vixxer.mensajero.nucleo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticoMeshTest
{
    @Test
    fun persisteUnaReferenciaSinGuardarElIdentificador()
    {
        val almacen = AlmacenEnMemoria()
        val diagnostico = DiagnosticoMesh(almacen, reloj = { 1000L })

        diagnostico.registrar(
            mensajeId = "mensaje-secreto-123",
            etapa = DiagnosticoMesh.Etapa.ENVIADO,
            transporte = DiagnosticoMesh.Transporte.BLE,
            enlace = "GATT",
            saltos = 1,
            duracionMs = 42,
        )

        val evento = diagnostico.instantanea().eventos.single()
        assertEquals(12, evento.mensaje?.length)
        assertFalse(evento.mensaje!!.contains("secreto"))
        assertEquals("gatt", evento.enlace)
    }

    @Test
    fun conservaSoloLosEventosMasRecientes()
    {
        var ahora = 0L
        val diagnostico = DiagnosticoMesh(AlmacenEnMemoria(), reloj = { ahora++ }, maximo = 3)

        repeat(5) { indice ->
            diagnostico.registrar(
                mensajeId = "mensaje-$indice",
                etapa = DiagnosticoMesh.Etapa.INTENTO,
                transporte = DiagnosticoMesh.Transporte.SERVIDOR,
            )
        }

        val eventos = diagnostico.instantanea().eventos
        assertEquals(3, eventos.size)
        assertEquals(4L, eventos.first().instanteMs)
        assertEquals(2L, eventos.last().instanteMs)
    }

    @Test
    fun reemplazaUnEnlaceDesconocidoParaNoFiltrarDirecciones()
    {
        val diagnostico = DiagnosticoMesh(AlmacenEnMemoria())

        diagnostico.registrar(
            mensajeId = "uno",
            etapa = DiagnosticoMesh.Etapa.ERROR,
            transporte = DiagnosticoMesh.Transporte.BLE,
            enlace = "AA:BB:CC:DD:EE:FF",
        )

        assertEquals("otro", diagnostico.instantanea().eventos.single().enlace)
    }

    @Test
    fun restauraElResumenDesdeElAlmacen()
    {
        val almacen = AlmacenEnMemoria()
        val diagnostico = DiagnosticoMesh(almacen, reloj = { 2000L })
        diagnostico.registrar(
            mensajeId = "uno",
            etapa = DiagnosticoMesh.Etapa.ERROR,
            transporte = DiagnosticoMesh.Transporte.BLE,
            duracionMs = 900,
            reintentos = 1,
            error = DiagnosticoMesh.CodigoError.SIN_VECINO,
        )

        val restaurado = DiagnosticoMesh(almacen).instantanea()

        assertEquals(900, restaurado.ultimaDuracionMs)
        assertEquals(DiagnosticoMesh.CodigoError.SIN_VECINO, restaurado.ultimoError)
        assertEquals(1, restaurado.reintentos)
    }

    @Test
    fun exportaContextoYColasSinDatosCrudos()
    {
        val diagnostico = DiagnosticoMesh(AlmacenEnMemoria(), reloj = { 3000L })
        diagnostico.registrar(
            mensajeId = "id-que-no-debe-salir",
            etapa = DiagnosticoMesh.Etapa.ENCOLADO,
            transporte = DiagnosticoMesh.Transporte.SIN_RUTA,
            cola = 2,
            error = DiagnosticoMesh.CodigoError.SIN_RUTA,
        )

        val json = diagnostico.exportar(
            DiagnosticoMesh.ContextoExportacion("0.4.0", "Teléfono de prueba", 36),
            colaOutbox = 2,
            colaRelay = 1,
        )

        assertTrue(json.contains("\"cola_outbox\": 2"))
        assertTrue(json.contains("\"cola_relay\": 1"))
        assertTrue(json.contains("\"sin_ruta\""))
        assertFalse(json.contains("id-que-no-debe-salir"))
    }

    @Test
    fun limpiarBorraElResumen()
    {
        val diagnostico = DiagnosticoMesh(AlmacenEnMemoria())
        diagnostico.registrar(
            mensajeId = null,
            etapa = DiagnosticoMesh.Etapa.ERROR,
            transporte = DiagnosticoMesh.Transporte.SIN_RUTA,
            error = DiagnosticoMesh.CodigoError.ESCANEO,
        )

        diagnostico.limpiar()

        val resumen = diagnostico.instantanea()
        assertTrue(resumen.eventos.isEmpty())
        assertNull(resumen.ultimoError)
    }
}
