package dev.vixxer.mensajero.nucleo

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InteropTest
{
    private val vectores = JSONObject(
        javaClass.getResource("/vectores-interop.json")!!.readText(),
    )

    @Test
    fun boxProduceLosMismosBytesQueTweetnacl()
    {
        val v = vectores.getJSONObject("box")
        val mensaje = v.getString("mensajeUtf8").toByteArray(Charsets.UTF_8)
        val caja = Cripto.cifrar(
            mensaje,
            Cripto.deBase64(v.getString("nonce")),
            Cripto.deBase64(v.getString("publicaB")),
            Cripto.deBase64(v.getString("secretaA")),
        )
        assertEquals(v.getString("cifrado"), Cripto.aBase64(caja))
    }

    @Test
    fun boxAbreLoCifradoPorLaAppRN()
    {
        val v = vectores.getJSONObject("box")
        val abierto = Cripto.descifrar(
            Cripto.deBase64(v.getString("cifrado")),
            Cripto.deBase64(v.getString("nonce")),
            Cripto.deBase64(v.getString("publicaA")),
            Cripto.deBase64(v.getString("secretaB")),
        )
        assertNotNull(abierto)
        assertEquals(v.getString("mensajeUtf8"), abierto.toString(Charsets.UTF_8))
    }

    @Test
    fun laPublicaDerivadaCoincide()
    {
        val v = vectores.getJSONObject("box")
        assertContentEquals(
            Cripto.deBase64(v.getString("publicaA")),
            Cripto.publicaDeSecreta(Cripto.deBase64(v.getString("secretaA"))),
        )
        assertContentEquals(
            Cripto.deBase64(v.getString("publicaB")),
            Cripto.publicaDeSecreta(Cripto.deBase64(v.getString("secretaB"))),
        )
    }

    @Test
    fun secretboxProduceLosMismosBytes()
    {
        val v = vectores.getJSONObject("secretbox")
        val sellado = Cripto.sellar(
            Cripto.deBase64(v.getString("plano")),
            Cripto.deBase64(v.getString("nonce")),
            Cripto.deBase64(v.getString("clave")),
        )
        assertEquals(v.getString("sellado"), Cripto.aBase64(sellado))
        val abierto = Cripto.abrir(
            Cripto.deBase64(v.getString("sellado")),
            Cripto.deBase64(v.getString("nonce")),
            Cripto.deBase64(v.getString("clave")),
        )
        assertNotNull(abierto)
        assertContentEquals(Cripto.deBase64(v.getString("plano")), abierto)
    }

    @Test
    fun laFirmaCoincideYSeVerifica()
    {
        val v = vectores.getJSONObject("firma")
        val (publica, secreta) = Cripto.parFirmaDeSemilla(Cripto.deBase64(v.getString("semilla")))
        assertEquals(v.getString("publica"), Cripto.aBase64(publica))
        assertEquals(v.getString("secreta"), Cripto.aBase64(secreta))
        val firma = Cripto.firmar(v.getString("canonico").toByteArray(Charsets.UTF_8), secreta)
        assertEquals(v.getString("firma"), Cripto.aBase64(firma))
        assertTrue(
            Cripto.verificarFirma(firma, v.getString("canonico").toByteArray(Charsets.UTF_8), publica),
        )
    }

    @Test
    fun elCanonicoReproduceElDelVector()
    {
        val v = vectores.getJSONObject("firma")
        assertEquals(
            v.getString("canonico"),
            Canonico.mensaje("u-abc123", "u-def456", "Q2lmcmFkb0RlUHJ1ZWJh", "Tm9uY2VEZVBydWViYQ==", "m-789"),
        )
    }

    @Test
    fun elNumeroDeSeguridadCoincide()
    {
        val v = vectores.getJSONObject("numeroSeguridad")
        assertEquals(
            v.getString("esperado"),
            Cripto.numeroSeguridad(v.getString("publicaA"), v.getString("publicaB")),
        )
        assertEquals(
            v.getString("esperado"),
            Cripto.numeroSeguridad(v.getString("publicaB"), v.getString("publicaA")),
        )
    }

    @Test
    fun elRespaldoDeLaAppRNSeRestaura()
    {
        val v = vectores.getJSONObject("respaldo")
        val secreta = Cripto.abrirRespaldo(
            v.getString("cifrado"),
            v.getString("nonce"),
            v.getString("salt"),
            v.getString("codigo"),
        )
        assertEquals(v.getString("secretaOriginal"), secreta)
        assertEquals(
            v.getString("secretaOriginal"),
            Cripto.abrirRespaldo(v.getString("cifrado"), v.getString("nonce"), v.getString("salt"), "abcd efgh jklm npqr stuv"),
        )
    }

    @Test
    fun elFormatoV2ProduceLosMismosTrozos()
    {
        val v = vectores.getJSONObject("trozos")
        val datos = FormatoTrozos.cifrarArchivo(
            v.getString("archivoB64"),
            Cripto.deBase64(v.getString("clave")),
            Cripto.deBase64(v.getString("nonce")),
        )
        assertEquals(v.getString("datos"), datos)
    }

    @Test
    fun losTrozosDeLaAppRNSeAbrenYReconstruyenElArchivo()
    {
        val v = vectores.getJSONObject("trozos")
        val datos = v.getString("datos")
        assertTrue(datos.startsWith(FormatoTrozos.MAGIA_B64))
        var pos = FormatoTrozos.MAGIA_B64.length
        var indice = 0
        val reconstruido = StringBuilder()
        while (pos < datos.length)
        {
            val cabecera = Cripto.deBase64(datos.substring(pos, pos + 8))
            val marco = FormatoTrozos.medidaMarco(cabecera)
            val charsMarco = marco.salto / 3 * 4
            val bytesMarco = Cripto.deBase64(datos.substring(pos, pos + charsMarco))
            val sellado = bytesMarco.copyOfRange(6, 6 + marco.len)
            val abierto = FormatoTrozos.abrirTrozo(
                Cripto.aBase64(sellado),
                v.getString("clave"),
                v.getString("nonce"),
                indice,
            )
            assertNotNull(abierto)
            reconstruido.append(abierto)
            pos += charsMarco
            indice += 1
        }
        assertEquals(v.getString("archivoB64"), reconstruido.toString())
    }
}
