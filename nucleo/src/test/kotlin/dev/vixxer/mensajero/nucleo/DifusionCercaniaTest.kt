package dev.vixxer.mensajero.nucleo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DifusionCercaniaTest
{
    private val semillaAna = ByteArray(Cripto.TAMANO_CLAVE) { (it + 7).toByte() }
    private val semillaBeto = ByteArray(Cripto.TAMANO_CLAVE) { (it + 71).toByte() }
    private val ahora = 1_754_000_000L

    private fun aleatorioFijo(desplazamiento: Int): (Int) -> ByteArray =
        { cuantos -> ByteArray(cuantos) { (it + desplazamiento).toByte() } }

    private fun cuerpoDe(texto: String?): DifusionCercania.Cuerpo = DifusionCercania.Cuerpo(
        emisorId = "u-ana",
        alias = "ana#4f2c",
        llaveFirma = "",
        firma = "",
        texto = texto,
    )

    @Test
    fun lasCapacidadesViejasSeLeenSinLlaveDeDifusion()
    {
        val viejas = byteArrayOf(1, 1, 0x1f, 0x40.toByte())
        val leidas = DifusionCercania.leerCapacidades(viejas)

        assertEquals(1, leidas?.version)
        assertEquals(0x1f40, leidas?.psm)
        assertNull(leidas?.llaveDifusion)
    }

    @Test
    fun lasCapacidadesNuevasConservanElPrefijoQueLeeUnNodoViejo()
    {
        val llave = DifusionCercania.publicaActual(semillaAna, ahora)
        val bytes = DifusionCercania.codificarCapacidades(0x1f40, llave)

        assertEquals(DifusionCercania.LARGO_CAPACIDADES_DIFUSION, bytes.size)
        assertEquals(DifusionCercania.VERSION_CON_DIFUSION, bytes[0].toInt())
        assertEquals(1, bytes[1].toInt() and 1)
        assertEquals(0x1f, bytes[2].toInt())
        assertEquals(0x40, bytes[3].toInt() and 0xFF)
        assertEquals(llave, DifusionCercania.leerCapacidades(bytes)?.llaveDifusion)
    }

    @Test
    fun sinLlaveValidaLasCapacidadesCaenAVersionUno()
    {
        val bytes = DifusionCercania.codificarCapacidades(0x1f40, "no-es-base64-de-32")

        assertEquals(DifusionCercania.LARGO_CAPACIDADES_BASE, bytes.size)
        assertEquals(DifusionCercania.VERSION_SIN_DIFUSION, bytes[0].toInt())
        assertNull(DifusionCercania.leerCapacidades(bytes)?.llaveDifusion)
    }

    @Test
    fun elSobreSelladoLoAbreSoloSuDestinatario()
    {
        val llaveBeto = DifusionCercania.publicaActual(semillaBeto, ahora)
        val sobre = DifusionCercania.sellar(
            "of-1",
            DifusionCercania.Tipo.CONTENIDO,
            cuerpoDe("nos vemos en la entrada"),
            llaveBeto,
            aleatorioFijo(3),
        )

        val abierto = DifusionCercania.abrir(sobre!!, DifusionCercania.secretasVigentes(semillaBeto, ahora))
        val ajeno = DifusionCercania.abrir(sobre, DifusionCercania.secretasVigentes(semillaAna, ahora))

        assertEquals("nos vemos en la entrada", abierto?.texto)
        assertEquals("u-ana", abierto?.emisorId)
        assertNull(ajeno)
    }

    @Test
    fun elSobreSeAbreAunqueLaLlaveHayaRotadoDespuesDeEnviarlo()
    {
        val llaveBeto = DifusionCercania.publicaActual(semillaBeto, ahora)
        val sobre = DifusionCercania.sellar(
            "of-2",
            DifusionCercania.Tipo.OFERTA,
            cuerpoDe(null),
            llaveBeto,
            aleatorioFijo(11),
        )

        val despues = ahora + DifusionCercania.SEGUNDOS_VENTANA
        val abierto = DifusionCercania.abrir(sobre!!, DifusionCercania.secretasVigentes(semillaBeto, despues))

        assertEquals("u-ana", abierto?.emisorId)
    }

    @Test
    fun dosVentanasDespuesElSobreYaNoSeAbre()
    {
        val llaveBeto = DifusionCercania.publicaActual(semillaBeto, ahora)
        val sobre = DifusionCercania.sellar(
            "of-3",
            DifusionCercania.Tipo.OFERTA,
            cuerpoDe(null),
            llaveBeto,
            aleatorioFijo(23),
        )

        val muyDespues = ahora + DifusionCercania.SEGUNDOS_VENTANA * 2
        assertNull(DifusionCercania.abrir(sobre!!, DifusionCercania.secretasVigentes(semillaBeto, muyDespues)))
    }

    @Test
    fun laLlavePublicaCambiaAlCambiarDeVentana()
    {
        val antes = DifusionCercania.publicaActual(semillaAna, ahora)
        val despues = DifusionCercania.publicaActual(semillaAna, ahora + DifusionCercania.SEGUNDOS_VENTANA)

        assertNotEquals(antes, despues)
    }

    @Test
    fun laFirmaDelEmisorSeVerificaYSeRompeAlCambiarElTexto()
    {
        val (publica, secreta) = Cripto.parFirmaDeSemilla(ByteArray(Cripto.TAMANO_CLAVE) { 5 })
        val canonico = DifusionCercania.canonico(
            "of-4",
            DifusionCercania.Tipo.CONTENIDO,
            "u-ana",
            "ana#4f2c",
            "hola",
        )
        val firma = Cripto.aBase64(Cripto.firmar(canonico.toByteArray(Charsets.UTF_8), secreta))
        val cuerpo = DifusionCercania.Cuerpo(
            emisorId = "u-ana",
            alias = "ana#4f2c",
            llaveFirma = Cripto.aBase64(publica),
            firma = firma,
            texto = "hola",
        )

        assertTrue(DifusionCercania.firmaValida("of-4", DifusionCercania.Tipo.CONTENIDO, cuerpo))
        assertFalse(
            DifusionCercania.firmaValida(
                "of-4",
                DifusionCercania.Tipo.CONTENIDO,
                cuerpo.copy(texto = "hola!"),
            ),
        )
    }

    @Test
    fun elAliasNoSePuedeSuplantarPorqueLoAmarraLaFirma()
    {
        val (publica, secreta) = Cripto.parFirmaDeSemilla(ByteArray(Cripto.TAMANO_CLAVE) { 9 })
        val canonico = DifusionCercania.canonico(
            "of-5",
            DifusionCercania.Tipo.OFERTA,
            "u-ana",
            "ana#4f2c",
            null,
        )
        val cuerpo = DifusionCercania.Cuerpo(
            emisorId = "u-ana",
            alias = "soporte vixxer",
            llaveFirma = Cripto.aBase64(publica),
            firma = Cripto.aBase64(Cripto.firmar(canonico.toByteArray(Charsets.UTF_8), secreta)),
        )

        assertFalse(DifusionCercania.firmaValida("of-5", DifusionCercania.Tipo.OFERTA, cuerpo))
    }

    @Test
    fun elSobreSobreviveIrYVolverDeJson()
    {
        val llaveBeto = DifusionCercania.publicaActual(semillaBeto, ahora)
        val sobre = DifusionCercania.sellar(
            "of-6",
            DifusionCercania.Tipo.ACEPTACION,
            cuerpoDe(null),
            llaveBeto,
            aleatorioFijo(31),
        )!!

        val vuelto = DifusionCercania.deJson(DifusionCercania.aJson(sobre))

        assertEquals(sobre, vuelto)
    }

    @Test
    fun unSobreDeMallaNormalNoSeConfundeConDifusion()
    {
        val malla = MeshCercania.crearSobre("u-ana", "u-beto", "cifrado", "nonce")
        val json = MeshCercania.aJson(malla)

        assertFalse(DifusionCercania.esSobreDifusion(json))
        assertNull(DifusionCercania.deJson(json))
    }

    @Test
    fun elAliasRecortaElCodigoAmigo()
    {
        assertEquals("ana#9f2c", DifusionCercania.alias("ana", "VX-3B-9F2C"))
        assertEquals("ana", DifusionCercania.alias("ana", ""))
    }
}
