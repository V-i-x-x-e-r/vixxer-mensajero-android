package dev.vixxer.mensajero.nucleo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class IdentidadRotativaTest
{
    private val secretoAna = ByteArray(Cripto.TAMANO_CLAVE) { (it + 1).toByte() }
    private val secretoBeto = ByteArray(Cripto.TAMANO_CLAVE) { (it + 99).toByte() }

    @Test
    fun mismoSecretoYVentanaProduceMismoToken()
    {
        val a = IdentidadRotativa.token("u-ana", secretoAna, 12345L)
        val b = IdentidadRotativa.token("u-ana", secretoAna, 12345L)
        assertEquals(a, b)
    }

    @Test
    fun secretoDistintoProduceTokenDistinto()
    {
        val conAna = IdentidadRotativa.token("u-ana", secretoAna, 12345L)
        val conBeto = IdentidadRotativa.token("u-ana", secretoBeto, 12345L)
        assertNotEquals(conAna, conBeto)
    }

    @Test
    fun ventanaDistintaProduceTokenDistinto()
    {
        val ahora = IdentidadRotativa.token("u-ana", secretoAna, 12345L)
        val despues = IdentidadRotativa.token("u-ana", secretoAna, 12346L)
        assertNotEquals(ahora, despues)
    }

    @Test
    fun elCoincidirEncuentraAlAmigoDeLaVentanaActual()
    {
        val epoch = 1_700_000_000L
        val ventana = IdentidadRotativa.ventanaActual(epoch)
        val observado = IdentidadRotativa.token("u-ana", secretoAna, ventana)
        val amigos = mapOf("u-ana" to secretoAna, "u-beto" to secretoBeto)
        assertEquals("u-ana", IdentidadRotativa.coincidir(observado, amigos, epoch))
    }

    @Test
    fun elCoincidirToleraUnaVentanaAdyacente()
    {
        val epoch = 1_700_000_000L
        val ventanaAnterior = IdentidadRotativa.ventanaActual(epoch) - 1
        val observado = IdentidadRotativa.token("u-beto", secretoBeto, ventanaAnterior)
        val amigos = mapOf("u-ana" to secretoAna, "u-beto" to secretoBeto)
        assertEquals("u-beto", IdentidadRotativa.coincidir(observado, amigos, epoch))
    }

    @Test
    fun elCoincidirRechazaUnaVentanaLejana()
    {
        val epoch = 1_700_000_000L
        val ventanaLejana = IdentidadRotativa.ventanaActual(epoch) - 5
        val observado = IdentidadRotativa.token("u-ana", secretoAna, ventanaLejana)
        val amigos = mapOf("u-ana" to secretoAna, "u-beto" to secretoBeto)
        assertNull(IdentidadRotativa.coincidir(observado, amigos, epoch))
    }

    @Test
    fun elCoincidirRegresaNullParaTokenDesconocido()
    {
        val epoch = 1_700_000_000L
        val amigos = mapOf("u-ana" to secretoAna, "u-beto" to secretoBeto)
        assertNull(IdentidadRotativa.coincidir("dG9rZW5EZXNjb25vY2lkbw==", amigos, epoch))
    }

    @Test
    fun elTokenActualUsaLaVentanaDeDiezMinutos()
    {
        val epoch = 1_700_000_123L
        val esperado = IdentidadRotativa.token("u-ana", secretoAna, epoch / 600L)
        assertEquals(esperado, IdentidadRotativa.tokenActual("u-ana", secretoAna, epoch))
    }

    @Test
    fun elSecretoCompartidoEsSimetrico()
    {
        val secretaAna = ByteArray(Cripto.TAMANO_CLAVE) { (it + 7).toByte() }
        val secretaBeto = ByteArray(Cripto.TAMANO_CLAVE) { (it + 40).toByte() }
        val publicaAna = Cripto.publicaDeSecreta(secretaAna)
        val publicaBeto = Cripto.publicaDeSecreta(secretaBeto)
        val ladoAna = Cripto.secretoCompartido(publicaBeto, secretaAna)
        val ladoBeto = Cripto.secretoCompartido(publicaAna, secretaBeto)
        assertEquals(Cripto.aBase64(ladoAna), Cripto.aBase64(ladoBeto))
        assertEquals(Cripto.TAMANO_CLAVE, ladoAna.size)
    }

    @Test
    fun elSecretoCompartidoCambiaPorPar()
    {
        val secretaAna = ByteArray(Cripto.TAMANO_CLAVE) { (it + 7).toByte() }
        val secretaBeto = ByteArray(Cripto.TAMANO_CLAVE) { (it + 40).toByte() }
        val secretaCarla = ByteArray(Cripto.TAMANO_CLAVE) { (it + 81).toByte() }
        val conBeto = Cripto.secretoCompartido(Cripto.publicaDeSecreta(secretaBeto), secretaAna)
        val conCarla = Cripto.secretoCompartido(Cripto.publicaDeSecreta(secretaCarla), secretaAna)
        assertNotEquals(Cripto.aBase64(conBeto), Cripto.aBase64(conCarla))
    }

    @Test
    fun elAnuncioDeAnaSeReconoceConElSecretoDerivadoPorBeto()
    {
        val epoch = 1_700_000_000L
        val secretaAna = ByteArray(Cripto.TAMANO_CLAVE) { (it + 7).toByte() }
        val secretaBeto = ByteArray(Cripto.TAMANO_CLAVE) { (it + 40).toByte() }
        val publicaAna = Cripto.publicaDeSecreta(secretaAna)
        val publicaBeto = Cripto.publicaDeSecreta(secretaBeto)
        val anunciado = IdentidadRotativa.tokenActual("u-ana", Cripto.secretoCompartido(publicaBeto, secretaAna), epoch)
        val amigosDeBeto = mapOf("u-ana" to Cripto.secretoCompartido(publicaAna, secretaBeto), "u-carla" to secretoAna)
        assertEquals("u-ana", IdentidadRotativa.coincidir(anunciado, amigosDeBeto, epoch))
    }
}
