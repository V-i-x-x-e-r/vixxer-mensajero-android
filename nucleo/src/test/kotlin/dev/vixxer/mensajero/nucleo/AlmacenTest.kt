package dev.vixxer.mensajero.nucleo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONObject

class AlmacenTest
{
    @Test
    fun borradorGuardaLeeYDescartaVacios()
    {
        val almacen = AlmacenEnMemoria()
        val borradores = Borradores(almacen)
        borradores.guardar("c1", JSONObject().put("texto", "hola ñ"))
        assertEquals("hola ñ", borradores.leer("c1").optString("texto"))
        borradores.guardar("c1", JSONObject())
        assertNull(almacen.leer("vixxer_borrador_c1"))
        borradores.guardar("c2", JSONObject().put("audio", JSONObject().put("dur", 3)))
        assertNull(almacen.leer("vixxer_borrador_c2"))
        borradores.guardar("c3", JSONObject().put("audio", JSONObject().put("uri", "file:///a.m4a")))
        assertEquals("file:///a.m4a", borradores.leer("c3").getJSONObject("audio").getString("uri"))
        borradores.limpiar("c3")
        assertEquals(0, borradores.leer("c3").length())
    }

    @Test
    fun borradorCorruptoRegresaObjetoVacio()
    {
        val almacen = AlmacenEnMemoria()
        almacen.escribir("vixxer_borrador_c1", "{roto")
        assertEquals(0, Borradores(almacen).leer("c1").length())
    }

    @Test
    fun aliasGuardaRecortaYBorraConVacio()
    {
        val alias = Alias(AlmacenEnMemoria())
        alias.guardar("conv1", "  Prima Dulce  ")
        assertEquals("Prima Dulce", alias.de("conv1"))
        alias.guardar("conv1", "   ")
        assertNull(alias.de("conv1"))
        alias.guardar("conv2", null)
        assertEquals(0, alias.leerTodos().size)
    }

    @Test
    fun ocultosAcumulaYPersisteUltimos500()
    {
        val almacen = AlmacenEnMemoria()
        val ocultos = Ocultos(almacen)
        for (i in 1..501)
        {
            ocultos.ocultar("c1", "m$i")
        }
        val persistidos = Ocultos(almacen).leer("c1")
        assertEquals(500, persistidos.size)
        assertTrue("m2" in persistidos)
        assertTrue("m501" in persistidos)
        assertTrue("m1" !in persistidos)
    }

    @Test
    fun fijadosAlternaQuitaYLeeFormatoViejo()
    {
        val almacen = AlmacenEnMemoria()
        val fijados = Fijados(almacen)
        val mensaje = Fijados.Fijado("m1", "hola", "u1")
        assertEquals(listOf(mensaje), fijados.alternar("c1", mensaje))
        assertEquals(2, fijados.alternar("c1", Fijados.Fijado("m2", "otro", "u2")).size)
        assertEquals(listOf(mensaje), fijados.quitar("c1", "m2"))
        assertEquals(0, fijados.alternar("c1", mensaje).size)
        assertNull(almacen.leer("vixxer_fijado_c1"))
        almacen.escribir("vixxer_fijado_viejo", """{"id":"m9","texto":"legado","remitente_id":"u9"}""")
        assertEquals(listOf(Fijados.Fijado("m9", "legado", "u9")), fijados.leer("viejo"))
    }

    @Test
    fun grupoVistoEscribeIsoComoJs()
    {
        val almacen = AlmacenEnMemoria()
        val vistos = GrupoVisto(almacen)
        vistos.marcarVisto("g1", Fechas.aInstante("2026-07-12T18:30:00.000Z"))
        assertEquals("2026-07-12T18:30:00.000Z", vistos.leerVistos()["g1"])
    }

    @Test
    fun outboxAgregaYQuitaPorLocalId()
    {
        val outbox = Outbox(AlmacenEnMemoria())
        outbox.agregar("u2", JSONObject().put("localId", "l1").put("texto", "hola"))
        outbox.agregar("u2", JSONObject().put("localId", "l2").put("texto", "otro"))
        assertEquals(2, outbox.leer("u2").size)
        outbox.quitar("u2", "l1")
        val restantes = outbox.leer("u2")
        assertEquals(1, restantes.size)
        assertEquals("l2", restantes[0].getString("localId"))
        assertEquals(0, outbox.leer("u9").size)
    }

    @Test
    fun llavesPasadasRecuerdaSinDuplicarYTopeCinco()
    {
        val llavero = LlavesPasadas(AlmacenEnMemoria())
        for (i in 1..6)
        {
            llavero.recordar("priv$i")
        }
        llavero.recordar("priv6")
        llavero.recordar(null)
        val memoria = llavero.cargar()
        assertEquals(listOf("priv6", "priv5", "priv4", "priv3", "priv2"), memoria)
    }

    @Test
    fun cacheChatFiltraLocalesYRecortaA50()
    {
        val cache = CacheChats(AlmacenEnMemoria())
        val mensajes = org.json.JSONArray()
        mensajes.put(JSONObject().put("id", "local-1").put("texto", "pendiente"))
        for (i in 1..60)
        {
            mensajes.put(JSONObject().put("id", "m$i").put("texto", "t$i"))
        }
        cache.guardarChat("u2", mensajes)
        val leidos = cache.leerChat("u2")!!
        assertEquals(50, leidos.length())
        assertEquals("m11", leidos.getJSONObject(0).getString("id"))
        assertEquals("m60", leidos.getJSONObject(49).getString("id"))
        cache.borrarChat("u2")
        assertNull(cache.leerChat("u2"))
        assertNull(cache.leerLista())
        val lista = JSONObject()
            .put("amigos", org.json.JSONArray().put(JSONObject().put("id", "c1")))
            .put("convs", JSONObject().put("c1", JSONObject().put("preview", "hola")))
        cache.guardarLista(lista)
        assertEquals(1, cache.leerLista()!!.getJSONArray("amigos").length())
        assertEquals("hola", cache.leerLista()!!.getJSONObject("convs").getJSONObject("c1").getString("preview"))
    }

    @Test
    fun estadosChatAlternaYOcultaSinDuplicar()
    {
        val estados = EstadosChat(AlmacenEnMemoria())
        assertEquals(listOf("c1"), estados.alternarFijado("c1"))
        assertEquals(emptyList(), estados.alternarFijado("c1"))
        estados.alternarArchivado("c2")
        estados.alternarFavorito("c3")
        estados.alternarSilenciado("c4")
        estados.ocultar("c5")
        estados.ocultar("c5")
        val todos = estados.leerEstados()
        assertEquals(listOf("c2"), todos.archivados)
        assertEquals(listOf("c3"), todos.favoritos)
        assertEquals(listOf("c4"), todos.silenciados)
        assertEquals(listOf("c5"), todos.ocultos)
        estados.mostrar("c5")
        assertEquals(emptyList(), estados.leerEstados().ocultos)
    }

    @Test
    fun temporizadorEfimeroGuardaYCeroBorra()
    {
        val almacen = AlmacenEnMemoria()
        val temporizador = TemporizadorEfimero(almacen)
        temporizador.guardar("c1", 3600)
        assertEquals(3600, temporizador.leer("c1"))
        temporizador.guardar("c1", 0)
        assertEquals(0, temporizador.leer("c1"))
        assertNull(almacen.leer("vixxer_efimero_c1"))
    }
}
