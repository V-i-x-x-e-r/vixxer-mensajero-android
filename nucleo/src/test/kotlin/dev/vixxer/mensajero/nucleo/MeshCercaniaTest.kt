package dev.vixxer.mensajero.nucleo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MeshCercaniaTest
{
    @Test
    fun elSobreConservaClienteIdAlIrYVolverDeJson()
    {
        val sobre = MeshCercania.crearSobre("u-ana", "u-beto", "cifrado", "nonce", ttl = 1, clienteId = "local-77")
        val vuelto = MeshCercania.deJson(MeshCercania.aJson(sobre))
        assertEquals("local-77", vuelto?.clienteId)
        assertEquals(1, vuelto?.ttl)
    }

    @Test
    fun elSobreSinClienteIdSigueSiendoNulo()
    {
        val sobre = MeshCercania.crearSobre("u-ana", "u-beto", "cifrado", "nonce")
        val vuelto = MeshCercania.deJson(MeshCercania.aJson(sobre))
        assertNull(vuelto?.clienteId)
    }

    @Test
    fun elSobreConTtlUnoNoSeReenvia()
    {
        val vistos = Vistos()
        val sobre = MeshCercania.crearSobre("u-ana", "u-beto", "c", "n", ttl = 1)
        val decision = MeshCercania.procesar(sobre, "u-carla", vistos)
        assertEquals(MeshCercania.Accion.DESCARTAR, decision.accion)
    }

    @Test
    fun elSobreConTtlUnoSeEntregaAlDestinatario()
    {
        val vistos = Vistos()
        val sobre = MeshCercania.crearSobre("u-ana", "u-beto", "c", "n", ttl = 1)
        val decision = MeshCercania.procesar(sobre, "u-beto", vistos)
        assertEquals(MeshCercania.Accion.ENTREGAR, decision.accion)
    }
}
