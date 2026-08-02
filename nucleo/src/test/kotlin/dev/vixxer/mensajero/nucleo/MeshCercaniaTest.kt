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
    fun elSobreCuentaLosSaltosAlReenviarse()
    {
        val vistos = Vistos()
        val sobre = MeshCercania.crearSobre("u-ana", "u-beto", "c", "n")

        val reenviado = MeshCercania.procesar(sobre, "u-carla", vistos).sobre
        val vuelto = MeshCercania.deJson(MeshCercania.aJson(reenviado!!))

        assertEquals(1, vuelto?.saltos)
        assertEquals(MeshCercania.TTL_MAXIMO - 1, vuelto?.ttl)
    }

    @Test
    fun elSobreConservaLaRespuestaAlIrYVolverDeJson()
    {
        val respuesta = "550e8400-e29b-41d4-a716-446655440000"
        val sobre = MeshCercania.crearSobre(
            "u-ana",
            "u-beto",
            "cifrado",
            "nonce",
            respuestaA = respuesta,
        )

        val vuelto = MeshCercania.deJson(MeshCercania.aJson(sobre))

        assertEquals(respuesta, vuelto?.respuestaA)
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

    @Test
    fun elSobreNaceDirectoYConservaElTipoGrupo()
    {
        val directo = MeshCercania.crearSobre("u-ana", "u-beto", "c", "n")
        assertEquals(MeshCercania.TIPO_DIRECTO, directo.tipo)
        val grupo = MeshCercania.crearSobre("u-ana", "u-beto", "c", "n", tipo = MeshCercania.TIPO_GRUPO)
        val vuelto = MeshCercania.deJson(MeshCercania.aJson(grupo))
        assertEquals(MeshCercania.TIPO_GRUPO, vuelto?.tipo)
    }

    @Test
    fun elSobreDeUnaVersionViejaSeLeeComoDirecto()
    {
        val crudo = """{"id":"a-1","remitenteId":"u-ana","destinatarioId":"u-beto","contenidoCifrado":"c","nonce":"n","ttl":3}"""
        assertEquals(MeshCercania.TIPO_DIRECTO, MeshCercania.deJson(crudo)?.tipo)
        assertEquals(0, MeshCercania.deJson(crudo)?.saltos)
    }

    @Test
    fun elTtlDelCableSeRecortaAlMaximo()
    {
        val crudo = """{"id":"a-2","remitenteId":"u-ana","destinatarioId":"u-beto","contenidoCifrado":"c","nonce":"n","ttl":9999}"""
        assertEquals(MeshCercania.TTL_MAXIMO, MeshCercania.deJson(crudo)?.ttl)
    }

    @Test
    fun elTtlDeUnSobrePropioNoPasaDelMaximo()
    {
        val sobre = MeshCercania.crearSobre("u-ana", "u-beto", "c", "n", ttl = 50)
        assertEquals(MeshCercania.TTL_MAXIMO, sobre.ttl)
    }
}
