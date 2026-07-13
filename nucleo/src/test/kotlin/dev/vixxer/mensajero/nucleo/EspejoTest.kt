package dev.vixxer.mensajero.nucleo

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.json.JSONObject

class EspejoTest
{
    private val vectores = JSONObject(javaClass.getResource("/vectores-espejo.json")!!.readText())
    private val efimero = vectores.getJSONObject("efimero")
    private val fechas = vectores.getJSONObject("fechas")
    private val zona = ZoneId.of(fechas.getString("zonaGeneracion"))

    @Test
    fun envolverProduceElMismoJsonQueRN()
    {
        val casos = efimero.getJSONArray("envolver")
        for (i in 0 until casos.length())
        {
            val caso = casos.getJSONObject(i)
            assertEquals(caso.getString("salida"), Efimero.envolver(caso.getString("t"), caso.getInt("d")))
        }
    }

    @Test
    fun envolverAvisoProduceElMismoJsonQueRN()
    {
        val casos = efimero.getJSONArray("envolverAviso")
        for (i in 0 until casos.length())
        {
            val caso = casos.getJSONObject(i)
            assertEquals(caso.getString("salida"), Efimero.envolverAviso(caso.getInt("d")))
        }
    }

    @Test
    fun textoAvisoYEtiquetaCoincidenConRN()
    {
        val avisos = efimero.getJSONArray("textoAviso")
        for (i in 0 until avisos.length())
        {
            val caso = avisos.getJSONObject(i)
            assertEquals(caso.getString("salida"), Efimero.textoAviso(caso.getInt("d")))
        }
        val etiquetas = efimero.getJSONArray("etiquetaDuracion")
        for (i in 0 until etiquetas.length())
        {
            val caso = etiquetas.getJSONObject(i)
            assertEquals(caso.getString("salida"), Efimero.etiquetaDuracion(caso.getInt("d")))
        }
    }

    @Test
    fun leerEfimeroCoincideConRN()
    {
        val casos = efimero.getJSONArray("leerEfimero")
        for (i in 0 until casos.length())
        {
            val caso = casos.getJSONObject(i)
            val entrada = if (caso.isNull("entrada")) null else caso.getString("entrada")
            val leido = Efimero.leerEfimero(entrada)
            if (caso.isNull("salida"))
            {
                assertNull(leido, "entrada: $entrada")
            }
            else
            {
                val esperado = caso.getJSONObject("salida")
                assertEquals(esperado.getInt("d"), leido!!.d)
                assertEquals(esperado.getString("m"), leido.m)
            }
        }
    }

    @Test
    fun leerAvisoCoincideConRN()
    {
        val casos = efimero.getJSONArray("leerAviso")
        for (i in 0 until casos.length())
        {
            val caso = casos.getJSONObject(i)
            val entrada = if (caso.isNull("entrada")) null else caso.getString("entrada")
            val leido = Efimero.leerAviso(entrada)
            if (caso.isNull("salida"))
            {
                assertNull(leido, "entrada: $entrada")
            }
            else
            {
                assertEquals(caso.getJSONObject("salida").getInt("d"), leido!!.d)
            }
        }
    }

    @Test
    fun expiraEnCoincideConRN()
    {
        val casos = efimero.getJSONArray("expiraEn")
        for (i in 0 until casos.length())
        {
            val caso = casos.getJSONObject(i)
            val enviadoEn = if (caso.isNull("enviado_en")) null else caso.getString("enviado_en")
            val esperado = if (caso.isNull("salida")) null else caso.getLong("salida")
            assertEquals(esperado, Efimero.expiraEn(caso.getString("texto"), enviadoEn))
        }
    }

    @Test
    fun resumenMensajeCoincideConRN()
    {
        val casos = vectores.getJSONArray("resumen")
        for (i in 0 until casos.length())
        {
            val caso = casos.getJSONObject(i)
            val entrada = if (caso.isNull("entrada")) null else caso.getString("entrada")
            assertEquals(caso.getString("salida"), Resumen.resumenMensaje(entrada), "entrada: $entrada")
        }
    }

    @Test
    fun extraerUrlYDominioCoincidenConRN()
    {
        val enlaces = vectores.getJSONObject("enlaces")
        val urls = enlaces.getJSONArray("extraerUrl")
        for (i in 0 until urls.length())
        {
            val caso = urls.getJSONObject(i)
            val esperado = if (caso.isNull("salida")) null else caso.getString("salida")
            assertEquals(esperado, Enlaces.extraerUrl(caso.getString("entrada")), "entrada: ${caso.getString("entrada")}")
        }
        val dominios = enlaces.getJSONArray("dominioDe")
        for (i in 0 until dominios.length())
        {
            val caso = dominios.getJSONObject(i)
            assertEquals(caso.getString("salida"), Enlaces.dominioDe(caso.getString("entrada")))
        }
    }

    @Test
    fun horaYMismoDiaCoincidenConRN()
    {
        val horas = fechas.getJSONArray("hora")
        for (i in 0 until horas.length())
        {
            val caso = horas.getJSONObject(i)
            assertEquals(caso.getString("salida"), Fechas.hora(caso.getString("entrada"), zona))
        }
        val dias = fechas.getJSONArray("mismoDia")
        for (i in 0 until dias.length())
        {
            val caso = dias.getJSONObject(i)
            assertEquals(caso.getBoolean("salida"), Fechas.mismoDia(caso.getString("a"), caso.getString("b"), zona), "a: ${caso.getString("a")} b: ${caso.getString("b")}")
        }
    }

    @Test
    fun etiquetaDiaDistingueHoyAyerYFecha()
    {
        val ahora = Fechas.aInstante("2026-07-12T20:00:00.000Z")
        assertEquals("Hoy", Fechas.etiquetaDia("2026-07-12T15:00:00.000Z", zona, ahora))
        assertEquals("Ayer", Fechas.etiquetaDia("2026-07-11T15:00:00.000Z", zona, ahora))
        assertEquals("9/7/2026", Fechas.etiquetaDia("2026-07-09T15:00:00.000Z", zona, ahora))
    }

    @Test
    fun previewDeHtmlExtraeMetadatosComoRN()
    {
        val html = """
            <html><head>
            <title>Titulo plano</title>
            <meta property="og:title" content="Vixxer &amp; amigos &#39;ya&#39;" />
            <meta name="description" content="Descripci&#243;n &lt;lista&gt;" />
            <meta content="https://vixxer.dev/img.png" property="og:image" />
            </head></html>
        """.trimIndent()
        val preview = Enlaces.previewDeHtml("https://vixxer.dev", html)!!
        assertEquals("Vixxer & amigos 'ya'", preview.titulo)
        assertEquals("Descripción <lista>", preview.desc)
        assertEquals("https://vixxer.dev/img.png", preview.imagen)
        assertNull(Enlaces.previewDeHtml("https://x.com", "<html><body>nada</body></html>"))
    }
}
