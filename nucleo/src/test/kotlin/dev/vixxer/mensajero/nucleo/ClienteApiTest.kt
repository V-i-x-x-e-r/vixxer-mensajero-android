package dev.vixxer.mensajero.nucleo

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject

class ClienteApiTest
{
    private lateinit var servidor: MockWebServer
    private var sesionExpirada = false

    private fun api(): ClienteApi =
        ClienteApi(servidor.url("/").toString().trimEnd('/'), { "tok123" }, { sesionExpirada = true })

    @BeforeTest
    fun preparar()
    {
        servidor = MockWebServer()
        servidor.start()
        sesionExpirada = false
    }

    @AfterTest
    fun cerrar()
    {
        servidor.shutdown()
    }

    @Test
    fun loginMandaCuerpoJsonSinAuthorization()
    {
        servidor.enqueue(MockResponse().setBody("""{"token":"abc","user_id":"u1"}"""))
        val salida = api().login("cesar", "secreta") as JSONObject
        assertEquals("abc", salida.getString("token"))
        val recibido = servidor.takeRequest()
        assertEquals("POST", recibido.method)
        assertEquals("/api/auth/login", recibido.path)
        assertNull(recibido.getHeader("Authorization"))
        val cuerpo = JSONObject(recibido.body.readUtf8())
        assertEquals("cesar", cuerpo.getString("usuario"))
        assertEquals("secreta", cuerpo.getString("contrasena"))
        assertTrue(recibido.getHeader("Content-Type")!!.startsWith("application/json"))
    }

    @Test
    fun conAuthAgregaBearer()
    {
        servidor.enqueue(MockResponse().setBody("[]"))
        val salida = api().amigos()
        assertTrue(salida is JSONArray)
        val recibido = servidor.takeRequest()
        assertEquals("GET", recibido.method)
        assertEquals("/api/amigos", recibido.path)
        assertEquals("Bearer tok123", recibido.getHeader("Authorization"))
    }

    @Test
    fun registroIncluyeRespaldoAtomico()
    {
        servidor.enqueue(MockResponse().setBody("""{"id":"u1"}"""))
        val respaldo = JSONObject().put("cifrado", "c").put("nonce", "n").put("salt", "s")

        api().registrar("cesar", "secreta", "pub", "firma", respaldo)

        val recibido = servidor.takeRequest()
        assertEquals("/api/auth/register", recibido.path)
        val cuerpo = JSONObject(recibido.body.readUtf8())
        assertEquals("c", cuerpo.getJSONObject("respaldo").getString("cifrado"))
    }

    @Test
    fun publicaIdentidadEnUnaSolaPeticion()
    {
        servidor.enqueue(MockResponse().setBody("""{"ok":true}"""))
        val respaldo = JSONObject().put("cifrado", "c").put("nonce", "n").put("salt", "s")

        api().publicarIdentidad("pub", "firma", respaldo)

        val recibido = servidor.takeRequest()
        assertEquals("PUT", recibido.method)
        assertEquals("/api/usuarios/identidad", recibido.path)
        assertEquals("Bearer tok123", recibido.getHeader("Authorization"))
        val cuerpo = JSONObject(recibido.body.readUtf8())
        assertEquals("pub", cuerpo.getString("llave_publica"))
        assertEquals("firma", cuerpo.getString("llave_firma"))
        assertEquals("s", cuerpo.getJSONObject("respaldo").getString("salt"))
    }

    @Test
    fun relayMensajeMapeaCamposComoRN()
    {
        servidor.enqueue(MockResponse().setBody("""{"id":"m1"}"""))
        api().relayMensaje(Sobre(
            "u-a",
            "u-b",
            "Q2lmcmFkbw==",
            "Tm9uY2U=",
            "cli-1",
            "RmlybWE=",
            "550e8400-e29b-41d4-a716-446655440000",
        ))
        val cuerpo = JSONObject(servidor.takeRequest().body.readUtf8())
        assertEquals("u-a", cuerpo.getString("remitente_id"))
        assertEquals("u-b", cuerpo.getString("destinatario_id"))
        assertEquals("Q2lmcmFkbw==", cuerpo.getString("contenido_cifrado"))
        assertEquals("Tm9uY2U=", cuerpo.getString("nonce"))
        assertEquals("cli-1", cuerpo.getString("cliente_id"))
        assertEquals("RmlybWE=", cuerpo.getString("firma"))
        assertEquals("550e8400-e29b-41d4-a716-446655440000", cuerpo.getString("respuesta_a"))
    }

    @Test
    fun historialCodificaAntesComoEncodeURIComponent()
    {
        servidor.enqueue(MockResponse().setBody("[]"))
        api().historial("u-b", "2026-07-12T18:30:00+00:00")
        assertEquals("/api/mensajes/historial/u-b?antes=2026-07-12T18%3A30%3A00%2B00%3A00", servidor.takeRequest().path)
        servidor.enqueue(MockResponse().setBody("[]"))
        api().historial("u-b")
        assertEquals("/api/mensajes/historial/u-b", servidor.takeRequest().path)
    }

    @Test
    fun urlMediaCodificaElPath()
    {
        servidor.enqueue(MockResponse().setBody("""{"url":"x"}"""))
        api().urlMedia("media/u1/foto final(1).bin")
        assertEquals("/api/media/url?path=media%2Fu1%2Ffoto%20final(1).bin", servidor.takeRequest().path)
    }

    @Test
    fun errorConDetailPropagaStatusYMensaje()
    {
        servidor.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail":"Usuario ya existe"}"""))
        val error = assertFailsWith<ErrorApi> { api().registrar("cesar", "x", "pub", "firma") }
        assertEquals(400, error.status)
        assertEquals("Usuario ya existe", error.message)
    }

    @Test
    fun errorSinCuerpoUsaMensajeGenerico()
    {
        servidor.enqueue(MockResponse().setResponseCode(500))
        val error = assertFailsWith<ErrorApi> { api().amigos() }
        assertEquals(500, error.status)
        assertEquals("Error 500", error.message)
    }

    @Test
    fun respuesta204RegresaNull()
    {
        servidor.enqueue(MockResponse().setResponseCode(204))
        assertNull(api().eliminarAmigo("u-b"))
        assertEquals("DELETE", servidor.takeRequest().method)
    }

    @Test
    fun error401ConAuthExpiraSesion()
    {
        servidor.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Token invalido"}"""))
        assertFailsWith<ErrorApi> { api().amigos() }
        assertTrue(sesionExpirada)
    }

    @Test
    fun error401SinAuthNoExpiraSesion()
    {
        servidor.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Credenciales"}"""))
        assertFailsWith<ErrorApi> { api().login("cesar", "mala") }
        assertTrue(!sesionExpirada)
    }

    @Test
    fun error401DeTokenAnteriorNoExpiraSesionNueva()
    {
        var tokenActual = "viejo"
        var expirada = false
        var error: Throwable? = null
        val cliente = ClienteApi(
            servidor.url("/").toString().trimEnd('/'),
            { tokenActual },
            { expirada = true },
        )
        servidor.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("""{"detail":"Token invalido"}""")
            .setHeadersDelay(200, TimeUnit.MILLISECONDS))

        val solicitud = thread {
            error = runCatching { cliente.amigos() }.exceptionOrNull()
        }
        assertEquals("Bearer viejo", servidor.takeRequest().getHeader("Authorization"))
        tokenActual = "nuevo"
        solicitud.join()

        assertTrue(error is ErrorApi)
        assertTrue(!expirada)
    }

    @Test
    fun sinConexionLanzaStatusCero()
    {
        val url = servidor.url("/").toString().trimEnd('/')
        servidor.shutdown()
        val error = assertFailsWith<ErrorApi> {
            ClienteApi(url, { "tok" }).amigos()
        }
        assertEquals(0, error.status)
        assertEquals("No se pudo conectar con el backend", error.message)
        servidor = MockWebServer()
        servidor.start()
    }

    @Test
    fun enviarGrupoMandaRespuestaANulaExplicita()
    {
        servidor.enqueue(MockResponse().setBody("""{"id":"m1"}"""))
        api().enviarGrupo("g1", "cli-9", JSONObject().put("u2", "cif"))
        val cuerpo = servidor.takeRequest().body.readUtf8()
        val obj = JSONObject(cuerpo)
        assertEquals("cli-9", obj.getString("cliente_id"))
        assertTrue(obj.has("respuesta_a"))
        assertTrue(obj.isNull("respuesta_a"))
    }

    @Test
    fun subirMediaConProgresoReportaYParsea()
    {
        servidor.enqueue(MockResponse().setBody("""{"path":"media/x.bin"}"""))
        val contenido = ByteArray(200_000) { (it and 255).toByte() }
        val archivo = File.createTempFile("media-api-", ".vx2").apply { writeBytes(contenido) }
        val avances = mutableListOf<Double>()
        try
        {
            val salida = api().subirMediaConProgreso(archivo) { avances.add(it) } as JSONObject
            assertEquals("media/x.bin", salida.getString("path"))
            assertTrue(avances.isNotEmpty())
            assertEquals(1.0, avances.last())
            val recibido = servidor.takeRequest()
            assertEquals("/api/media/archivo", recibido.path)
            assertEquals("Bearer tok123", recibido.getHeader("Authorization"))
            assertEquals("application/octet-stream", recibido.getHeader("Content-Type"))
            assertEquals(contenido.size.toLong(), recibido.getHeader("Content-Length")?.toLong())
            assertContentEquals(contenido, recibido.body.readByteArray())
        }
        finally
        {
            archivo.delete()
        }
    }
}
