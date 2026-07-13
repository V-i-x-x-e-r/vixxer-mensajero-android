package dev.vixxer.mensajero.nucleo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirmaTest
{
    private fun instancia(almacen: Almacen): Firma
    {
        val api = ClienteApi("http://localhost:1", token = { null })
        return Firma(almacen, api, semilla = { ByteArray(Cripto.TAMANO_CLAVE) { (it + 7).toByte() } })
    }

    @Test
    fun asegurarGeneraYPersiste()
    {
        val almacen = AlmacenEnMemoria()
        val firma = instancia(almacen)
        val publica = firma.asegurarLlaveFirma()
        assertEquals(publica, almacen.leer(ClavesSeguras.CLAVE_FIRMA_PUBLICA))
        assertNotNull(almacen.leer(ClavesSeguras.CLAVE_FIRMA_PRIVADA))
        assertEquals(publica, firma.asegurarLlaveFirma())
    }

    @Test
    fun firmarVerificaConLaPublica()
    {
        val almacen = AlmacenEnMemoria()
        val firma = instancia(almacen)
        val publica = firma.asegurarLlaveFirma()
        val mensaje = Firma.mensajeCanonico("a", "b", "cifrado", "nonce", "id-1")
        val firmada = firma.firmar(mensaje)
        assertNotNull(firmada)
        assertTrue(Cripto.verificarFirma(Cripto.deBase64(firmada), mensaje.toByteArray(Charsets.UTF_8), Cripto.deBase64(publica)))
    }

    @Test
    fun firmarSinLlaveRegresaNull()
    {
        val firma = instancia(AlmacenEnMemoria())
        assertNull(firma.firmar("hola"))
    }

    @Test
    fun mensajeCanonicoConcatenaConBarras()
    {
        assertEquals("r|d|c|n|i", Firma.mensajeCanonico("r", "d", "c", "n", "i"))
    }
}
