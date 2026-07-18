package dev.vixxer.mensajero.nucleo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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
    fun prepararIdentidadNoReemplazaLaActual()
    {
        val almacen = AlmacenEnMemoria()
        val identidad = instancia(almacen)
        val primera = identidad.crearIdentidad()
        val privadaPrimera = almacen.leer(ClavesSeguras.CLAVE_PRIVADA)

        val borrador = identidad.prepararIdentidad()

        assertNotEquals(primera.publicKey, borrador.publicKey)
        assertEquals(privadaPrimera, almacen.leer(ClavesSeguras.CLAVE_PRIVADA))
        identidad.confirmarIdentidad(borrador)
        assertEquals(borrador.privateKey, almacen.leer(ClavesSeguras.CLAVE_PRIVADA))
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
    fun restauracionPreparadaNoPersisteHastaConfirmarse()
    {
        val origen = AlmacenEnMemoria()
        val nueva = instancia(origen).crearIdentidad()
        val destino = AlmacenEnMemoria()
        val identidad = instancia(destino)

        val restaurada = identidad.prepararRestauracion(nueva.respaldo, nueva.codigo)

        assertNull(destino.leer(ClavesSeguras.CLAVE_PRIVADA))
        identidad.confirmarIdentidad(restaurada!!)
        assertEquals(nueva.publicKey, destino.leer(ClavesSeguras.CLAVE_PUBLICA))
    }

    @Test
    fun descifraConUnaLlaveHistorica()
    {
        val almacenReceptor = AlmacenEnMemoria()
        val receptor = instancia(almacenReceptor)
        val identidadVieja = receptor.crearIdentidad()
        val almacenEmisor = AlmacenEnMemoria()
        val identidadEmisor = instancia(almacenEmisor).crearIdentidad()
        val (cifrado, nonce) = Cripto.cifrarTexto(
            "mensaje anterior",
            identidadVieja.publicKey,
            identidadEmisor.privateKey,
        )
        receptor.crearIdentidad()

        assertEquals(
            "mensaje anterior",
            receptor.descifrarConHistoricas(cifrado, nonce, identidadEmisor.publicKey),
        )
    }

    @Test
    fun llaveHistoricaInvalidaNoImpideProbarLaSiguiente()
    {
        val almacenReceptor = AlmacenEnMemoria()
        val receptor = instancia(almacenReceptor)
        val identidadVieja = receptor.crearIdentidad()
        val emisor = instancia(AlmacenEnMemoria()).crearIdentidad()
        val (cifrado, nonce) = Cripto.cifrarTexto(
            "mensaje recuperable",
            identidadVieja.publicKey,
            emisor.privateKey,
        )
        almacenReceptor.escribir(ClavesSeguras.CLAVE_PRIVADA, "base64-invalido")
        LlavesPasadas(almacenReceptor).recordar(identidadVieja.privateKey)

        assertEquals(
            "mensaje recuperable",
            receptor.descifrarConHistoricas(cifrado, nonce, emisor.publicKey),
        )
    }

    @Test
    fun prepararFirmaNoLaPersiste()
    {
        val almacen = AlmacenEnMemoria()
        val identidad = instancia(almacen)
        val firma = identidad.prepararFirma()

        assertNull(almacen.leer(ClavesSeguras.CLAVE_FIRMA_PRIVADA))
        identidad.confirmarFirma(firma)
        assertEquals(firma.publicKey, almacen.leer(ClavesSeguras.CLAVE_FIRMA_PUBLICA))
    }

    @Test
    fun identidadConfirmadaConservaRespaldoHastaQueSeSube()
    {
        val almacen = AlmacenEnMemoria()
        val identidad = instancia(almacen)
        val nueva = identidad.prepararIdentidad()

        identidad.confirmarIdentidad(nueva)

        assertEquals(nueva.respaldo.toString(), identidad.respaldoPendiente()?.toString())
        assertEquals(nueva.codigo, identidad.codigoPendiente())
        identidad.confirmarRespaldoSubido()
        assertNull(identidad.respaldoPendiente())
        assertEquals(nueva.codigo, identidad.codigoPendiente())
        identidad.confirmarCodigoGuardado()
        assertNull(identidad.codigoPendiente())
    }

    @Test
    fun registroPendienteEsDurableYSeReutilizaPorUsuario()
    {
        val almacen = AlmacenEnMemoria()
        val identidad = instancia(almacen)
        val primero = identidad.prepararRegistro("  CESAR  ")

        val recuperado = instancia(almacen).registroPendiente()
        assertNotNull(recuperado)
        assertEquals("cesar", recuperado.usuario)
        assertEquals(primero.identidad.privateKey, recuperado.identidad.privateKey)
        assertEquals(primero.firma.privateKey, recuperado.firma.privateKey)

        val reintento = identidad.prepararRegistro("cesar")
        assertEquals(primero.identidad.publicKey, reintento.identidad.publicKey)
        identidad.confirmarRegistro(recuperado)
        assertEquals(recuperado.identidad.publicKey, almacen.leer(ClavesSeguras.CLAVE_PUBLICA))
        identidad.borrarRegistroPendiente()
        assertNull(identidad.registroPendiente())
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
