package dev.vixxer.mensajero.nucleo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdentidadTest
{
    private fun instancia(almacen: Almacen): Identidad
    {
        var contador = 0
        return Identidad(almacen, azar = { cuantos ->
            contador += 1
            ByteArray(cuantos) { ((it + contador * 31) and 255).toByte() }
        })
    }

    @Test
    fun codigoConFormatoDeGrupos()
    {
        val codigo = instancia(AlmacenEnMemoria()).generarCodigoRecuperacion()
        assertTrue(Regex("^[A-Z2-9]{4}(-[A-Z2-9]{4}){4}$").matches(codigo))
        assertTrue(codigo.none { it in "IO01" })
    }

    @Test
    fun identidadPersisteYRespaldoRestaura()
    {
        val almacen = AlmacenEnMemoria()
        val identidad = instancia(almacen)
        val nueva = identidad.crearIdentidad()
        assertEquals(nueva.publicKey, almacen.leer(ClavesSeguras.CLAVE_PUBLICA))
        assertEquals(nueva.codigo, almacen.leer(ClavesSeguras.CODIGO_RECUP))

        val otroAlmacen = AlmacenEnMemoria()
        val restaurada = instancia(otroAlmacen).restaurarDeRespaldo(nueva.respaldo, nueva.codigo)
        assertEquals(nueva.publicKey, restaurada)
        assertEquals(almacen.leer(ClavesSeguras.CLAVE_PRIVADA), otroAlmacen.leer(ClavesSeguras.CLAVE_PRIVADA))
    }

    @Test
    fun codigoIncorrectoRegresaNull()
    {
        val identidad = instancia(AlmacenEnMemoria())
        val nueva = identidad.crearIdentidad()
        assertNull(instancia(AlmacenEnMemoria()).restaurarDeRespaldo(nueva.respaldo, "AAAA-BBBB-CCCC-DDDD-EEEE"))
    }

    @Test
    fun crearIdentidadRecuerdaLaLlaveAnterior()
    {
        val almacen = AlmacenEnMemoria()
        val identidad = instancia(almacen)
        val primera = identidad.crearIdentidad()
        val privadaPrimera = almacen.leer(ClavesSeguras.CLAVE_PRIVADA)!!
        val segunda = identidad.crearIdentidad()
        assertNotEquals(primera.publicKey, segunda.publicKey)
        assertTrue(LlavesPasadas(almacen).cargar().contains(privadaPrimera))
    }

    @Test
    fun respaldoDeArchivoValidaCampos()
    {
        val identidad = instancia(AlmacenEnMemoria())
        assertNull(identidad.leerRespaldoArchivo("no es json"))
        assertNull(identidad.leerRespaldoArchivo("{\"cifrado\":\"x\"}"))
        val valido = identidad.leerRespaldoArchivo("{\"cifrado\":\"a\",\"nonce\":\"b\",\"salt\":\"c\",\"extra\":1}")
        assertEquals("a", valido!!.getString("cifrado"))
        assertTrue(!valido.has("extra"))
    }
}
