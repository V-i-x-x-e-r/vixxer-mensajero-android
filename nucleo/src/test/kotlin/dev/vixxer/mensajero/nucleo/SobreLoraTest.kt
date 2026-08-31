package dev.vixxer.mensajero.nucleo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SobreLoraTest
{
    private val secretaAna = ByteArray(Cripto.TAMANO_CLAVE) { (it + 11).toByte() }
    private val secretaBeto = ByteArray(Cripto.TAMANO_CLAVE) { (it + 91).toByte() }
    private val secretaCarla = ByteArray(Cripto.TAMANO_CLAVE) { (it + 151).toByte() }
    private val anaSecreta = Cripto.aBase64(secretaAna)
    private val betoSecreta = Cripto.aBase64(secretaBeto)
    private val carlaSecreta = Cripto.aBase64(secretaCarla)
    private val anaPublica = Cripto.aBase64(Cripto.publicaDeSecreta(secretaAna))
    private val betoPublica = Cripto.aBase64(Cripto.publicaDeSecreta(secretaBeto))
    private val idAna = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    private val id = "a1b2c3d4"

    private fun aleatorioFijo(desplazamiento: Int): (Int) -> ByteArray =
        { cuantos -> ByteArray(cuantos) { (it + desplazamiento).toByte() } }

    private fun texto(contenido: String) = SobreLora.Cuerpo(
        emisorId = idAna,
        tipo = SobreLora.Tipo.TEXTO,
        texto = contenido,
    )

    private fun empaquetarTexto(contenido: String): List<SobreLora.Parte>? = SobreLora.empaquetar(
        id = id,
        cuerpo = texto(contenido),
        llaveDestinoB64 = betoPublica,
        secretaEstaticaB64 = anaSecreta,
        aleatorio = aleatorioFijo(3),
    )

    @Test
    fun unTextoCortoViajaEnUnSoloPaquete()
    {
        val partes = empaquetarTexto("nos vemos en la plaza")

        assertEquals(1, partes?.size)
        assertEquals(1, partes?.first()?.total)
        assertTrue(SobreLora.aBytes(partes!!.first()).size <= SobreLora.TOPE_PAQUETE)
    }

    @Test
    fun elTopeDeTextoEsExactamenteLoQueLlenaUnPaquete()
    {
        val justo = "a".repeat(SobreLora.TOPE_TEXTO)
        val partes = empaquetarTexto(justo)

        assertEquals(149, SobreLora.TOPE_TEXTO)
        assertEquals(1, partes?.size)
        assertEquals(SobreLora.TOPE_PAQUETE, SobreLora.aBytes(partes!!.first()).size)
    }

    @Test
    fun unTextoQueSePasaDelTopeNoSeEmpaqueta()
    {
        assertNull(empaquetarTexto("a".repeat(SobreLora.TOPE_TEXTO + 1)))
    }

    @Test
    fun elTextoRegresaIgualDelOtroLado()
    {
        val partes = empaquetarTexto("nos vemos en la plaza")
        val abierto = SobreLora.desempaquetar(partes!!, betoSecreta)

        assertEquals("nos vemos en la plaza", abierto?.cuerpo?.texto)
        assertEquals(idAna, abierto?.cuerpo?.emisorId)
        assertEquals(SobreLora.Tipo.TEXTO, abierto?.cuerpo?.tipo)
    }

    @Test
    fun laPruebaConfirmaAlEmisorQueDiceSer()
    {
        val partes = empaquetarTexto("soy ana")
        val abierto = SobreLora.desempaquetar(partes!!, betoSecreta)

        assertNotNull(abierto)
        assertTrue(SobreLora.pruebaValida(id, abierto, anaPublica, betoSecreta))
    }

    @Test
    fun laPruebaFallaSiElEmisorNoEsQuienDice()
    {
        val partes = empaquetarTexto("soy ana")
        val abierto = SobreLora.desempaquetar(partes!!, betoSecreta)

        assertNotNull(abierto)
        assertFalse(SobreLora.pruebaValida(id, abierto, betoPublica, betoSecreta))
    }

    @Test
    fun laPruebaFallaSiCambiaElIdDelSobre()
    {
        val partes = empaquetarTexto("soy ana")
        val abierto = SobreLora.desempaquetar(partes!!, betoSecreta)

        assertNotNull(abierto)
        assertFalse(SobreLora.pruebaValida("ffffffff", abierto, anaPublica, betoSecreta))
    }

    @Test
    fun unTerceroConSuPropiaLlaveNoAbreElSobre()
    {
        val partes = empaquetarTexto("secreto")

        assertNull(SobreLora.desempaquetar(partes!!, carlaSecreta))
    }

    @Test
    fun alterarUnByteDeLaCargaRompeElSobre()
    {
        val partes = empaquetarTexto("nos vemos en la plaza")!!
        val rota = partes.first().carga.copyOf()
        rota[rota.size - 1] = (rota[rota.size - 1] + 1).toByte()

        assertNull(SobreLora.desempaquetar(listOf(partes.first().copy(carga = rota)), betoSecreta))
    }

    @Test
    fun laOfertaRegresaConSusCamposIntactos()
    {
        val cuerpo = SobreLora.Cuerpo(
            emisorId = idAna,
            tipo = SobreLora.Tipo.OFERTA,
            oferta = SobreLora.Oferta(
                idContenido = "0011223344556677",
                tamano = 81920,
                clase = SobreLora.Clase.FOTO,
                nombre = "cenote.webp",
            ),
        )
        val partes = SobreLora.empaquetar(id, cuerpo, betoPublica, anaSecreta, aleatorio = aleatorioFijo(5))
        val abierto = SobreLora.desempaquetar(partes!!, betoSecreta)

        assertEquals(1, partes.size)
        assertEquals("0011223344556677", abierto?.cuerpo?.oferta?.idContenido)
        assertEquals(81920, abierto?.cuerpo?.oferta?.tamano)
        assertEquals(SobreLora.Clase.FOTO, abierto?.cuerpo?.oferta?.clase)
        assertEquals("cenote.webp", abierto?.cuerpo?.oferta?.nombre)
    }

    @Test
    fun laMiniaturaDeCuatroKiloBytesSeParteEnDiecinuevePaquetes()
    {
        val bytes = ByteArray(SobreLora.TOPE_MINIATURA) { (it % 251).toByte() }
        val cuerpo = SobreLora.Cuerpo(
            emisorId = idAna,
            tipo = SobreLora.Tipo.MINIATURA,
            miniatura = Cripto.aBase64(bytes),
        )
        val partes = SobreLora.empaquetar(id, cuerpo, betoPublica, anaSecreta, aleatorio = aleatorioFijo(7))

        assertEquals(19, partes?.size)
        assertEquals(19, SobreLora.partesQueOcupa(SobreLora.TOPE_MINIATURA))
        assertTrue(partes!!.all { SobreLora.aBytes(it).size <= SobreLora.TOPE_PAQUETE })
    }

    @Test
    fun laMiniaturaSeRearmaAunqueLasPartesLleguenRevueltas()
    {
        val bytes = ByteArray(1500) { (it % 241).toByte() }
        val cuerpo = SobreLora.Cuerpo(
            emisorId = idAna,
            tipo = SobreLora.Tipo.MINIATURA,
            miniatura = Cripto.aBase64(bytes),
        )
        val partes = SobreLora.empaquetar(id, cuerpo, betoPublica, anaSecreta, aleatorio = aleatorioFijo(9))!!
        val abierto = SobreLora.desempaquetar(partes.reversed(), betoSecreta)

        assertTrue(partes.size > 1)
        assertEquals(Cripto.aBase64(bytes), abierto?.cuerpo?.miniatura)
    }

    @Test
    fun siFaltaUnaParteLaMiniaturaNoSeArma()
    {
        val bytes = ByteArray(1500) { (it % 241).toByte() }
        val cuerpo = SobreLora.Cuerpo(
            emisorId = idAna,
            tipo = SobreLora.Tipo.MINIATURA,
            miniatura = Cripto.aBase64(bytes),
        )
        val partes = SobreLora.empaquetar(id, cuerpo, betoPublica, anaSecreta, aleatorio = aleatorioFijo(9))!!

        assertNull(SobreLora.desempaquetar(partes.drop(1), betoSecreta))
    }

    @Test
    fun unaMiniaturaQueSePasaDelTopeNoSeEmpaqueta()
    {
        val cuerpo = SobreLora.Cuerpo(
            emisorId = idAna,
            tipo = SobreLora.Tipo.MINIATURA,
            miniatura = Cripto.aBase64(ByteArray(SobreLora.TOPE_MINIATURA + 1)),
        )

        assertNull(SobreLora.empaquetar(id, cuerpo, betoPublica, anaSecreta))
    }

    @Test
    fun laParteSobreviveLaIdaYVueltaABytes()
    {
        val original = empaquetarTexto("hola")!!.first()
        val vuelta = SobreLora.deBytes(SobreLora.aBytes(original))

        assertEquals(original, vuelta)
    }

    @Test
    fun unaVersionDistintaSeRechaza()
    {
        val bytes = SobreLora.aBytes(empaquetarTexto("hola")!!.first())
        bytes[0] = ((2 shl 4) or SobreLora.Tipo.TEXTO.valor).toByte()

        assertNull(SobreLora.deBytes(bytes))
    }

    @Test
    fun unTipoDesconocidoSeRechaza()
    {
        val bytes = SobreLora.aBytes(empaquetarTexto("hola")!!.first())
        bytes[0] = ((SobreLora.VERSION shl 4) or 9).toByte()

        assertNull(SobreLora.deBytes(bytes))
    }

    @Test
    fun unPaqueteMasGrandeQueElAireSeRechaza()
    {
        assertNull(SobreLora.deBytes(ByteArray(SobreLora.TOPE_PAQUETE + 1)))
        assertNull(SobreLora.deBytes(ByteArray(SobreLora.LARGO_CABECERA)))
    }

    @Test
    fun elRelevoBajaElTtlYSubeLosSaltos()
    {
        val parte = empaquetarTexto("hola")!!.first()
        val decision = SobreLora.procesar(parte, Vistos())

        assertEquals(MeshCercania.Accion.REENVIAR, decision.accion)
        assertEquals(SobreLora.TTL_MAXIMO - 1, decision.parte?.ttl)
        assertEquals(1, decision.parte?.saltos)
    }

    @Test
    fun elRelevoDescartaLoQueYaPasoPorAqui()
    {
        val parte = empaquetarTexto("hola")!!.first()
        val vistos = Vistos()
        SobreLora.procesar(parte, vistos)

        assertEquals(MeshCercania.Accion.DESCARTAR, SobreLora.procesar(parte, vistos).accion)
    }

    @Test
    fun elRelevoDescartaCuandoSeAcabaElTtl()
    {
        val parte = empaquetarTexto("hola")!!.first().copy(ttl = 1)

        assertEquals(MeshCercania.Accion.DESCARTAR, SobreLora.procesar(parte, Vistos()).accion)
    }

    @Test
    fun cadaFragmentoSeCuentaAparteEnElRelevo()
    {
        val bytes = ByteArray(1500) { (it % 241).toByte() }
        val cuerpo = SobreLora.Cuerpo(
            emisorId = idAna,
            tipo = SobreLora.Tipo.MINIATURA,
            miniatura = Cripto.aBase64(bytes),
        )
        val partes = SobreLora.empaquetar(id, cuerpo, betoPublica, anaSecreta, aleatorio = aleatorioFijo(9))!!
        val vistos = Vistos()

        assertTrue(partes.all { SobreLora.procesar(it, vistos).accion == MeshCercania.Accion.REENVIAR })
    }

    @Test
    fun unEmisorQueNoEsUuidNoSeEmpaqueta()
    {
        val cuerpo = SobreLora.Cuerpo(emisorId = "u-ana", tipo = SobreLora.Tipo.TEXTO, texto = "hola")

        assertNull(SobreLora.empaquetar(id, cuerpo, betoPublica, anaSecreta))
    }

    @Test
    fun cadaSobreEstrenaLlaveEfimeraAunConElMismoTexto()
    {
        val uno = SobreLora.empaquetar(id, texto("hola"), betoPublica, anaSecreta, aleatorio = aleatorioFijo(3))
        val otro = SobreLora.empaquetar(id, texto("hola"), betoPublica, anaSecreta, aleatorio = aleatorioFijo(23))

        assertFalse(uno!!.first().carga.contentEquals(otro!!.first().carga))
    }

    @Test
    fun elIdNuevoTraeCuatroBytesEnHexadecimal()
    {
        assertEquals(SobreLora.LARGO_ID * 2, SobreLora.nuevoId(aleatorioFijo(1)).length)
        assertEquals("01020304", SobreLora.nuevoId(aleatorioFijo(1)))
    }
}
