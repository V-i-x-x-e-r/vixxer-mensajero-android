package dev.vixxer.mensajero.nucleo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediosTest
{
    private data class Nuevo(val bytes: ByteArray, val clave: String, val nonce: String, val peso: Long)

    private fun cifrarNuevo(original: ByteArray): Nuevo
    {
        val salida = ByteArrayOutputStream()
        val cifrado = Medios.cifrarFlujo(ByteArrayInputStream(original), salida, original.size.toLong())
        return Nuevo(salida.toByteArray(), cifrado.clave, cifrado.nonce, cifrado.peso)
    }

    @Test
    fun cifraYDescifraEnFlujoIdaYVuelta()
    {
        val original = ByteArray(200_000) { ((it * 31) and 255).toByte() }
        val cifrado = Medios.cifrarArchivo(original)
        val salida = ByteArrayOutputStream()
        val ok = Medios.descifrarFlujo(
            ByteArrayInputStream(Cripto.deBase64(cifrado.datos)),
            cifrado.clave,
            cifrado.nonce,
            salida,
        )
        assertTrue(ok)
        assertContentEquals(original, salida.toByteArray())
    }

    @Test
    fun archivoPequenoDeUnSoloTrozo()
    {
        val original = "hola media".toByteArray()
        val cifrado = Medios.cifrarArchivo(original)
        val salida = ByteArrayOutputStream()
        assertTrue(Medios.descifrarFlujo(
            ByteArrayInputStream(Cripto.deBase64(cifrado.datos)),
            cifrado.clave,
            cifrado.nonce,
            salida,
        ))
        assertContentEquals(original, salida.toByteArray())
    }

    @Test
    fun claveIncorrectaFalla()
    {
        val cifrado = Medios.cifrarArchivo("secreto".toByteArray())
        val salida = ByteArrayOutputStream()
        val claveMala = Cripto.aBase64(ByteArray(Cripto.TAMANO_CLAVE) { 7 })
        assertFalse(Medios.descifrarFlujo(
            ByteArrayInputStream(Cripto.deBase64(cifrado.datos)),
            claveMala,
            cifrado.nonce,
            salida,
        ))
    }

    @Test
    fun interoperaConAbrirTrozoDelFormatoRn()
    {
        val original = ByteArray(90_000) { (it and 255).toByte() }
        val cifrado = Medios.cifrarArchivo(original)
        val todos = Cripto.deBase64(cifrado.datos)
        val marco = FormatoTrozos.medidaMarco(todos.copyOfRange(6, 12))
        val primerTrozo = Cripto.aBase64(todos.copyOfRange(12, 12 + marco.len))
        val abierto = FormatoTrozos.abrirTrozo(primerTrozo, cifrado.clave, cifrado.nonce, 0)
        assertTrue(abierto != null && abierto.isNotEmpty())
    }

    @Test
    fun formatoStreamingMarcaFinalYDescifra()
    {
        val original = ByteArray(100_000) { ((it * 13) and 255).toByte() }
        val cifrado = cifrarNuevo(original)
        assertContentEquals(Cripto.deBase64(FormatoTrozos.MAGIA_B64), cifrado.bytes.copyOfRange(0, 6))
        var pos = 6
        var marcos = 0
        while (pos < cifrado.bytes.size)
        {
            val marco = FormatoTrozos.medidaMarco(cifrado.bytes.copyOfRange(pos, pos + 6))
            assertTrue(marco.cabeceraValida && marco.marcado)
            assertEquals(pos + marco.salto == cifrado.bytes.size, marco.final)
            pos += marco.salto
            marcos += 1
        }
        assertTrue(marcos >= 2)
        val salida = ByteArrayOutputStream()
        assertTrue(Medios.descifrarFlujo(
            ByteArrayInputStream(cifrado.bytes),
            cifrado.clave,
            cifrado.nonce,
            salida,
            cifrado.peso,
        ))
        assertContentEquals(original, salida.toByteArray())
    }

    @Test
    fun streamingTruncadoExactamenteEntreMarcosFalla()
    {
        val original = ByteArray(FormatoTrozos.TROZO_BYTES + 17) { (it and 255).toByte() }
        val cifrado = cifrarNuevo(original)
        val primero = FormatoTrozos.medidaMarco(cifrado.bytes.copyOfRange(6, 12))
        val truncado = cifrado.bytes.copyOf(6 + primero.salto)
        assertFalse(Medios.descifrarFlujo(
            ByteArrayInputStream(truncado),
            cifrado.clave,
            cifrado.nonce,
            ByteArrayOutputStream(),
            cifrado.peso,
        ))
    }

    @Test
    fun borrarFlagsDeStreamingNoPermiteDowngradeALegacy()
    {
        val cifrado = cifrarNuevo("marco autenticado".toByteArray())
        val alterado = cifrado.bytes.copyOf()
        alterado[9] = 0
        alterado[10] = 0
        alterado[11] = 0
        assertFalse(Medios.descifrarFlujo(
            ByteArrayInputStream(alterado),
            cifrado.clave,
            cifrado.nonce,
            ByteArrayOutputStream(),
            cifrado.peso,
        ))
    }

    @Test
    fun formatoLegacySigueAceptandoFinSinFlag()
    {
        val original = ByteArray(FormatoTrozos.TROZO_BYTES + 9) { ((it * 7) and 255).toByte() }
        val cifrado = Medios.cifrarArchivo(original)
        val salida = ByteArrayOutputStream()
        assertTrue(Medios.descifrarFlujo(
            ByteArrayInputStream(Cripto.deBase64(cifrado.datos)),
            cifrado.clave,
            cifrado.nonce,
            salida,
            original.size.toLong(),
        ))
        assertContentEquals(original, salida.toByteArray())
    }

    @Test
    fun cifradoStreamingImponeLimiteRealAunqueElProveedorNoInformeTamano()
    {
        assertFailsWith<MedioDemasiadoGrandeException> {
            Medios.cifrarFlujo(
                ByteArrayInputStream(ByteArray(1025)),
                ByteArrayOutputStream(),
                1024,
            )
        }
    }

    @Test
    fun vectorStreamingDeterminista()
    {
        val original = "hola-vixxer-stream".toByteArray()
        val clave = ByteArray(Cripto.TAMANO_CLAVE) { it.toByte() }
        val nonce = ByteArray(Cripto.TAMANO_NONCE) { (it + 32).toByte() }
        val salida = ByteArrayOutputStream()
        Medios.cifrarFlujoConLlaves(
            ByteArrayInputStream(original),
            salida,
            original.size.toLong(),
            clave,
            nonce,
        )
        val datos =
            "VlgyQ0gxAAAiVlgBxV9zzVaaGDpLKrCwL/UDT19m+DVHLFX1M6Dcx2X/WJWzSQAA"
        assertEquals(
            datos,
            Cripto.aBase64(salida.toByteArray()),
        )
        val claro = ByteArrayOutputStream()
        assertTrue(Medios.descifrarFlujo(
            ByteArrayInputStream(Cripto.deBase64(datos)),
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
            "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3",
            claro,
            original.size.toLong(),
        ))
        assertContentEquals(original, claro.toByteArray())
    }
}
