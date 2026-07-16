package dev.vixxer.mensajero.nucleo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediosTest
{
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
}
