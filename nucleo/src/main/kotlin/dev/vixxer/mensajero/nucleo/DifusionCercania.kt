package dev.vixxer.mensajero.nucleo

import org.json.JSONObject
import java.security.SecureRandom

object DifusionCercania
{
    const val VERSION_SIN_DIFUSION = 1
    const val VERSION_CON_DIFUSION = 2
    const val SEGUNDOS_VENTANA = 900L
    const val TAMANO_LLAVE = 32
    const val LARGO_CAPACIDADES_BASE = 4
    const val LARGO_CAPACIDADES_DIFUSION = LARGO_CAPACIDADES_BASE + TAMANO_LLAVE
    const val TOPE_TEXTO = 2000

    enum class Tipo(val valor: String)
    {
        OFERTA("oferta"),
        ACEPTACION("aceptacion"),
        CONTENIDO("contenido"),
        RECHAZO("rechazo");

        companion object
        {
            fun de(valor: String): Tipo? = entries.firstOrNull { it.valor == valor }
        }
    }

    data class Capacidades(
        val version: Int,
        val soportaL2cap: Boolean,
        val psm: Int,
        val llaveDifusion: String?,
    )

    data class Sobre(
        val id: String,
        val tipo: Tipo,
        val llaveEmisor: String,
        val nonce: String,
        val carga: String,
    )

    data class Cuerpo(
        val emisorId: String,
        val alias: String,
        val llaveFirma: String,
        val firma: String,
        val texto: String? = null,
        val tamano: Int = 0,
    )

    fun codificarCapacidades(psm: Int, llaveDifusionB64: String?): ByteArray
    {
        val banderas = if (psm > 0) 1 else 0
        val base = byteArrayOf(
            VERSION_SIN_DIFUSION.toByte(),
            banderas.toByte(),
            ((psm shr 8) and 0xFF).toByte(),
            (psm and 0xFF).toByte(),
        )
        val llave = llaveDifusionB64?.let { runCatching { Cripto.deBase64(it) }.getOrNull() }
        if (llave == null || llave.size != TAMANO_LLAVE)
        {
            return base
        }
        base[0] = VERSION_CON_DIFUSION.toByte()
        return base + llave
    }

    fun leerCapacidades(bytes: ByteArray?): Capacidades?
    {
        if (bytes == null || bytes.size < LARGO_CAPACIDADES_BASE)
        {
            return null
        }
        val version = bytes[0].toInt() and 0xFF
        val soportaL2cap = (bytes[1].toInt() and 1) == 1
        val psm = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        val llave = if (version >= VERSION_CON_DIFUSION && bytes.size >= LARGO_CAPACIDADES_DIFUSION)
        {
            Cripto.aBase64(bytes.copyOfRange(LARGO_CAPACIDADES_BASE, LARGO_CAPACIDADES_DIFUSION))
        }
        else
        {
            null
        }
        return Capacidades(version, soportaL2cap, if (soportaL2cap) psm else 0, llave)
    }

    fun ventanaActual(epochSegundos: Long): Long = epochSegundos / SEGUNDOS_VENTANA

    fun secretaDeVentana(semillaLocal: ByteArray, ventana: Long): ByteArray
    {
        val entrada = "vixxer-difusion".toByteArray(Charsets.UTF_8) + semillaLocal + bytesVentana(ventana)
        return Cripto.hash(entrada).copyOfRange(0, TAMANO_LLAVE)
    }

    fun publicaDeVentana(semillaLocal: ByteArray, ventana: Long): String
    {
        return Cripto.aBase64(Cripto.publicaDeSecreta(secretaDeVentana(semillaLocal, ventana)))
    }

    fun publicaActual(semillaLocal: ByteArray, epochSegundos: Long): String
    {
        return publicaDeVentana(semillaLocal, ventanaActual(epochSegundos))
    }

    fun secretasVigentes(semillaLocal: ByteArray, epochSegundos: Long): List<ByteArray>
    {
        val ventana = ventanaActual(epochSegundos)
        return listOf(
            secretaDeVentana(semillaLocal, ventana),
            secretaDeVentana(semillaLocal, ventana - 1),
        )
    }

    fun alias(usuario: String, codigo: String): String
    {
        val recorte = codigo.filter { it.isLetterOrDigit() }.takeLast(4).lowercase()
        return if (recorte.isEmpty()) usuario else "$usuario#$recorte"
    }

    fun canonico(id: String, tipo: Tipo, emisorId: String, alias: String, texto: String?): String
    {
        val resumen = Cripto.aBase64(
            Cripto.hash((texto ?: "").toByteArray(Charsets.UTF_8)).copyOfRange(0, 16),
        )
        return "${tipo.valor}|$id|$emisorId|$alias|$resumen"
    }

    fun cuerpoAJson(cuerpo: Cuerpo): String = JSONObject()
        .put("emisorId", cuerpo.emisorId)
        .put("alias", cuerpo.alias)
        .put("llaveFirma", cuerpo.llaveFirma)
        .put("firma", cuerpo.firma)
        .put("texto", cuerpo.texto ?: JSONObject.NULL)
        .put("tamano", cuerpo.tamano)
        .toString()

    fun cuerpoDeJson(texto: String): Cuerpo?
    {
        return try
        {
            val obj = JSONObject(texto)
            val emisorId = obj.optString("emisorId")
            if (emisorId.isBlank())
            {
                return null
            }
            val contenido = if (obj.isNull("texto")) null else obj.optString("texto")
            Cuerpo(
                emisorId = emisorId,
                alias = obj.optString("alias"),
                llaveFirma = obj.optString("llaveFirma"),
                firma = obj.optString("firma"),
                texto = contenido?.take(TOPE_TEXTO),
                tamano = obj.optInt("tamano", 0),
            )
        }
        catch (_: Exception)
        {
            null
        }
    }

    fun sellar(
        id: String,
        tipo: Tipo,
        cuerpo: Cuerpo,
        llaveDestinoB64: String,
        aleatorio: (Int) -> ByteArray = ::bytesAleatorios,
    ): Sobre?
    {
        val destino = runCatching { Cripto.deBase64(llaveDestinoB64) }.getOrNull() ?: return null
        if (destino.size != TAMANO_LLAVE)
        {
            return null
        }
        val secretaEfimera = aleatorio(TAMANO_LLAVE)
        val publicaEfimera = Cripto.publicaDeSecreta(secretaEfimera)
        val nonce = aleatorio(Cripto.TAMANO_NONCE)
        val caja = Cripto.cifrar(
            cuerpoAJson(cuerpo).toByteArray(Charsets.UTF_8),
            nonce,
            destino,
            secretaEfimera,
        )
        return Sobre(
            id = id,
            tipo = tipo,
            llaveEmisor = Cripto.aBase64(publicaEfimera),
            nonce = Cripto.aBase64(nonce),
            carga = Cripto.aBase64(caja),
        )
    }

    fun abrir(sobre: Sobre, secretasPropias: List<ByteArray>): Cuerpo?
    {
        val llaveEmisor = runCatching { Cripto.deBase64(sobre.llaveEmisor) }.getOrNull() ?: return null
        val nonce = runCatching { Cripto.deBase64(sobre.nonce) }.getOrNull() ?: return null
        val caja = runCatching { Cripto.deBase64(sobre.carga) }.getOrNull() ?: return null
        if (llaveEmisor.size != TAMANO_LLAVE || nonce.size != Cripto.TAMANO_NONCE)
        {
            return null
        }
        for (secreta in secretasPropias)
        {
            val abierto = Cripto.descifrar(caja, nonce, llaveEmisor, secreta) ?: continue
            return cuerpoDeJson(String(abierto, Charsets.UTF_8))
        }
        return null
    }

    fun firmaValida(id: String, tipo: Tipo, cuerpo: Cuerpo): Boolean
    {
        val publica = runCatching { Cripto.deBase64(cuerpo.llaveFirma) }.getOrNull() ?: return false
        val firma = runCatching { Cripto.deBase64(cuerpo.firma) }.getOrNull() ?: return false
        if (firma.size != Cripto.TAMANO_FIRMA)
        {
            return false
        }
        val mensaje = canonico(id, tipo, cuerpo.emisorId, cuerpo.alias, cuerpo.texto)
        return Cripto.verificarFirma(firma, mensaje.toByteArray(Charsets.UTF_8), publica)
    }

    fun aJson(sobre: Sobre): String = JSONObject()
        .put("difusion", 1)
        .put("id", sobre.id)
        .put("tipo", sobre.tipo.valor)
        .put("llaveEmisor", sobre.llaveEmisor)
        .put("nonce", sobre.nonce)
        .put("carga", sobre.carga)
        .toString()

    fun deJson(texto: String): Sobre?
    {
        return try
        {
            val obj = JSONObject(texto)
            if (obj.optInt("difusion", 0) != 1)
            {
                return null
            }
            val id = obj.optString("id")
            val tipo = Tipo.de(obj.optString("tipo"))
            if (id.isBlank() || tipo == null)
            {
                return null
            }
            Sobre(
                id = id,
                tipo = tipo,
                llaveEmisor = obj.optString("llaveEmisor"),
                nonce = obj.optString("nonce"),
                carga = obj.optString("carga"),
            )
        }
        catch (_: Exception)
        {
            null
        }
    }

    fun esSobreDifusion(texto: String): Boolean
    {
        return try
        {
            JSONObject(texto).optInt("difusion", 0) == 1
        }
        catch (_: Exception)
        {
            false
        }
    }

    private fun bytesAleatorios(cuantos: Int): ByteArray
    {
        val bytes = ByteArray(cuantos)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    private fun bytesVentana(ventana: Long): ByteArray
    {
        val bytes = ByteArray(8)
        var resto = ventana
        for (i in 7 downTo 0)
        {
            bytes[i] = (resto and 0xFF).toByte()
            resto = resto shr 8
        }
        return bytes
    }
}
