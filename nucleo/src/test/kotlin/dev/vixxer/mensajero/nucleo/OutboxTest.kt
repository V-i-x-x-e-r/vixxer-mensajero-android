package dev.vixxer.mensajero.nucleo

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONArray
import org.json.JSONObject

class OutboxTest
{
    @Test
    fun enumeraDirectosYGruposSinMezclarDestinos()
    {
        val outbox = Outbox(AlmacenEnMemoria())
        outbox.agregar("u2", item("00000000-0000-4000-8000-000000000001"))
        outbox.agregar("u3", item("00000000-0000-4000-8000-000000000002"))
        outbox.agregarGrupo("g1", item("00000000-0000-4000-8000-000000000003"))

        assertEquals(3, outbox.leerTodos().size)
        assertEquals(1, outbox.leer("u2").size)
        assertEquals(1, outbox.leerGrupo("g1").size)
        assertEquals(Outbox.Tipo.GRUPO, outbox.leerTodos().last().tipo)
    }

    @Test
    fun agregarMismoClienteIdActualizaSinDuplicar()
    {
        val outbox = Outbox(AlmacenEnMemoria())
        val id = "00000000-0000-4000-8000-000000000001"
        outbox.agregar("u2", item(id).put("texto", "primero"))
        outbox.agregar("u2", item(id).put("texto", "corregido"))

        assertEquals(1, outbox.leerTodos().size)
        assertEquals("corregido", outbox.leer("u2").single().getString("texto"))
    }

    @Test
    fun falloPersisteBackoffYQuitarGrupoSoloQuitaEseMensaje()
    {
        val outbox = Outbox(AlmacenEnMemoria())
        outbox.agregarGrupo("g1", item("00000000-0000-4000-8000-000000000001"))
        outbox.agregarGrupo("g1", item("00000000-0000-4000-8000-000000000002"))
        val pendiente = outbox.leerTodos().first()

        outbox.registrarFallo(pendiente, ahoraMs = 1_000L)
        val actualizado = outbox.leerTodos().first()
        assertEquals(2, outbox.leerGrupo("g1").size)
        assertFalse(outbox.listoParaReintentar(actualizado, ahoraMs = 2_999L))
        assertTrue(outbox.listoParaReintentar(actualizado, ahoraMs = 3_000L))

        outbox.quitarGrupo("g1", pendiente.clienteId)
        assertEquals(1, outbox.leerGrupo("g1").size)
    }

    @Test
    fun migraColaDirectaAnteriorAlAbrirConversacion()
    {
        val almacen = AlmacenEnMemoria()
        almacen.escribir(
            "vixxer_outbox_u2",
            JSONArray().put(item("00000000-0000-4000-8000-000000000001")).toString(),
        )

        val outbox = Outbox(almacen)
        assertEquals(1, outbox.leer("u2").size)
        assertEquals(1, outbox.leerTodos().size)
        assertEquals(null, almacen.leer("vixxer_outbox_u2"))
    }

    @Test
    fun importaAAlmacenNuevoAntesDeBorrarOrigen()
    {
        val origen = Outbox(AlmacenEnMemoria())
        val destino = Outbox(AlmacenEnMemoria())
        origen.agregar("u2", item("00000000-0000-4000-8000-000000000001"))
        origen.agregarGrupo("g1", item("00000000-0000-4000-8000-000000000002"))

        assertEquals(2, destino.importarDesde(origen))
        assertEquals(0, origen.leerTodos().size)
        assertEquals(2, destino.leerTodos().size)
        assertEquals(0, destino.importarDesde(origen))
    }

    @Test
    fun generaUuidCanonicoNoBasadoEnReloj()
    {
        val primero = IdMensaje.nuevo()
        val segundo = IdMensaje.nuevo()

        assertTrue(IdMensaje.esValido(primero))
        assertEquals(primero, UUID.fromString(primero).toString())
        assertTrue(primero != segundo)
        assertFalse(IdMensaje.esValido("local-1234"))
    }

    @Test
    fun unMismoEnvioNoPuedeTomarseDosVecesEnParalelo()
    {
        val vuelos = EnviosEnVuelo()
        val clave = "cuenta|grupo|g1|00000000-0000-4000-8000-000000000001"

        assertTrue(vuelos.tomar(clave))
        assertFalse(vuelos.tomar(clave))
        vuelos.liberar(clave)
        assertTrue(vuelos.tomar(clave))
    }

    @Test
    fun snapshotQuitadoNoPuedeVolverAEnviarse()
    {
        val outbox = Outbox(AlmacenEnMemoria())
        outbox.agregar("u2", item("00000000-0000-4000-8000-000000000001"))
        val snapshot = outbox.leerTodos().single()

        outbox.quitar("u2", snapshot.clienteId)

        assertFalse(outbox.contiene(snapshot))
    }

    @Test
    fun pendienteDirectoProducePayloadCifradoCompletoAlDrenar()
    {
        val almacen = AlmacenEnMemoria()
        val outbox = Outbox(almacen)
        val clienteId = "00000000-0000-4000-8000-000000000001"
        val pendiente = EnvioDirecto.crearPendiente(
            texto = "mensaje sin conexion",
            enviadoEn = "2026-07-18T12:00:00Z",
            respuestaA = "mensaje-anterior",
            clienteId = clienteId,
        )
        outbox.agregar("destino", pendiente)

        val privadaEmisor = ByteArray(Cripto.TAMANO_CLAVE) { (it + 1).toByte() }
        val privadaDestino = ByteArray(Cripto.TAMANO_CLAVE) { (it + 41).toByte() }
        val publicaEmisor = Cripto.aBase64(Cripto.publicaDeSecreta(privadaEmisor))
        val publicaDestino = Cripto.aBase64(Cripto.publicaDeSecreta(privadaDestino))
        val enCola = outbox.leerTodos().single()

        val cuerpo = EnvioDirecto.prepararCuerpo(
            destinoId = enCola.destinoId,
            pendiente = enCola.datos,
            publicaDestino = publicaDestino,
            privadaPropia = Cripto.aBase64(privadaEmisor),
        )

        assertEquals("destino", cuerpo.getString("destinatarioId"))
        assertEquals(clienteId, cuerpo.getString("clienteId"))
        assertEquals("mensaje-anterior", cuerpo.getString("respuestaA"))
        assertTrue(cuerpo.getString("contenidoCifrado").isNotBlank())
        assertTrue(cuerpo.getString("nonce").isNotBlank())
        assertFalse(cuerpo.has("texto"))
        assertEquals(
            "mensaje sin conexion",
            Cripto.descifrarTexto(
                cuerpo.getString("contenidoCifrado"),
                cuerpo.getString("nonce"),
                publicaEmisor,
                Cripto.aBase64(privadaDestino),
            ),
        )
    }

    private fun item(id: String) = JSONObject()
        .put("localId", id)
        .put("texto", "hola")
}
