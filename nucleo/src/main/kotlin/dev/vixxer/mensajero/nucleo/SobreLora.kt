package dev.vixxer.mensajero.nucleo

import java.security.SecureRandom
import java.util.UUID

object SobreLora
{
    const val VERSION = 1
    const val TOPE_PAQUETE = 237
    const val LARGO_CABECERA = 8
    const val TOPE_CARGA = TOPE_PAQUETE - LARGO_CABECERA
    const val LARGO_ID = 4
    const val LARGO_EMISOR = 16
    const val LARGO_PRUEBA = 16
    const val LARGO_CONTENIDO = 8
    const val LARGO_TAMANO = 3
    const val MAXIMO_PARTES = 255
    const val TTL_MAXIMO = 5
    const val GASTO_SOBRE = Cripto.TAMANO_CLAVE + LARGO_EMISOR + LARGO_PRUEBA + Cripto.TAMANO_MAC
    const val TOPE_TEXTO = TOPE_CARGA - GASTO_SOBRE
    const val TOPE_NOMBRE = 64
    const val TOPE_MINIATURA = 4096

    enum class Tipo(val valor: Int)
    {
        TEXTO(1),
        OFERTA(2),
        MINIATURA(3);

        companion object
        {
            fun de(valor: Int): Tipo? = entries.firstOrNull { it.valor == valor }
        }
    }

    enum class Clase(val valor: Int)
    {
        FOTO(1),
        VIDEO(2),
        AUDIO(3),
        DOCUMENTO(4);

        companion object
        {
            fun de(valor: Int): Clase? = entries.firstOrNull { it.valor == valor }
        }
    }

    data class Oferta(
        val idContenido: String,
        val tamano: Int,
        val clase: Clase,
        val nombre: String,
    )

    data class Cuerpo(
        val emisorId: String,
        val tipo: Tipo,
        val texto: String? = null,
        val oferta: Oferta? = null,
        val miniatura: String? = null,
    )

    data class Abierto(val cuerpo: Cuerpo, val prueba: String)

    data class Parte(
        val id: String,
        val tipo: Tipo,
        val ttl: Int,
        val saltos: Int,
        val indice: Int,
        val total: Int,
        val carga: ByteArray,
    )
    {
        override fun equals(other: Any?): Boolean
        {
            if (this === other)
            {
                return true
            }
            if (other !is Parte)
            {
                return false
            }
            return id == other.id && tipo == other.tipo && ttl == other.ttl &&
                saltos == other.saltos && indice == other.indice &&
                total == other.total && carga.contentEquals(other.carga)
        }

        override fun hashCode(): Int
        {
            var resultado = id.hashCode()
            resultado = 31 * resultado + tipo.hashCode()
            resultado = 31 * resultado + ttl
            resultado = 31 * resultado + saltos
            resultado = 31 * resultado + indice
            resultado = 31 * resultado + total
            resultado = 31 * resultado + carga.contentHashCode()
            return resultado
        }
    }

    data class Decision(val accion: MeshCercania.Accion, val parte: Parte? = null)

    fun nuevoId(aleatorio: (Int) -> ByteArray = ::bytesAleatorios): String = aHex(aleatorio(LARGO_ID))

    fun partesQueOcupa(bytesDeCuerpo: Int): Int
    {
        val flujo = Cripto.TAMANO_CLAVE + LARGO_EMISOR + LARGO_PRUEBA + bytesDeCuerpo + Cripto.TAMANO_MAC
        return (flujo + TOPE_CARGA - 1) / TOPE_CARGA
    }

    fun empaquetar(
        id: String,
        cuerpo: Cuerpo,
        llaveDestinoB64: String,
        secretaEstaticaB64: String,
        ttl: Int = TTL_MAXIMO,
        aleatorio: (Int) -> ByteArray = ::bytesAleatorios,
    ): List<Parte>?
    {
        val idBytes = deHex(id) ?: return null
        val emisor = bytesDeUuid(cuerpo.emisorId) ?: return null
        val destino = llaveDe(llaveDestinoB64) ?: return null
        val estatica = llaveDe(secretaEstaticaB64) ?: return null
        if (idBytes.size != LARGO_ID)
        {
            return null
        }
        val contenido = codificarCuerpo(cuerpo) ?: return null
        val prueba = sello(idBytes, cuerpo.tipo, emisor, contenido, destino, estatica)
        val secretaEfimera = aleatorio(Cripto.TAMANO_CLAVE)
        val publicaEfimera = Cripto.publicaDeSecreta(secretaEfimera)
        val caja = Cripto.cifrar(
            emisor + prueba + contenido,
            nonceDe(publicaEfimera, destino),
            destino,
            secretaEfimera,
        )
        return fragmentar(id, cuerpo.tipo, ttl, publicaEfimera + caja)
    }

    fun desempaquetar(partes: List<Parte>, secretaPropiaB64: String): Abierto?
    {
        val secreta = llaveDe(secretaPropiaB64) ?: return null
        val flujo = unir(partes) ?: return null
        if (flujo.size < Cripto.TAMANO_CLAVE + Cripto.TAMANO_MAC)
        {
            return null
        }
        val publicaEfimera = flujo.copyOfRange(0, Cripto.TAMANO_CLAVE)
        val caja = flujo.copyOfRange(Cripto.TAMANO_CLAVE, flujo.size)
        val plano = Cripto.descifrar(
            caja,
            nonceDe(publicaEfimera, Cripto.publicaDeSecreta(secreta)),
            publicaEfimera,
            secreta,
        ) ?: return null
        if (plano.size < LARGO_EMISOR + LARGO_PRUEBA)
        {
            return null
        }
        val emisorId = uuidDeBytes(plano.copyOfRange(0, LARGO_EMISOR))
        val prueba = plano.copyOfRange(LARGO_EMISOR, LARGO_EMISOR + LARGO_PRUEBA)
        val contenido = plano.copyOfRange(LARGO_EMISOR + LARGO_PRUEBA, plano.size)
        val cuerpo = decodificarCuerpo(partes.first().tipo, emisorId, contenido) ?: return null
        return Abierto(cuerpo, Cripto.aBase64(prueba))
    }

    fun pruebaValida(
        id: String,
        abierto: Abierto,
        publicaEmisorB64: String,
        secretaPropiaB64: String,
    ): Boolean
    {
        val idBytes = deHex(id) ?: return false
        val emisor = bytesDeUuid(abierto.cuerpo.emisorId) ?: return false
        val publica = llaveDe(publicaEmisorB64) ?: return false
        val secreta = llaveDe(secretaPropiaB64) ?: return false
        val contenido = codificarCuerpo(abierto.cuerpo) ?: return false
        val esperada = sello(idBytes, abierto.cuerpo.tipo, emisor, contenido, publica, secreta)
        val recibida = runCatching { Cripto.deBase64(abierto.prueba) }.getOrNull() ?: return false
        return constanteIguales(esperada, recibida)
    }

    fun procesar(parte: Parte?, vistos: Vistos): Decision
    {
        if (parte == null || parte.id.isBlank())
        {
            return Decision(MeshCercania.Accion.DESCARTAR)
        }
        val marca = "${parte.id}-${parte.indice}"
        if (vistos.visto(marca))
        {
            return Decision(MeshCercania.Accion.DESCARTAR)
        }
        vistos.marcar(marca)
        if (parte.ttl <= 1)
        {
            return Decision(MeshCercania.Accion.DESCARTAR)
        }
        return Decision(
            MeshCercania.Accion.REENVIAR,
            parte.copy(
                ttl = parte.ttl - 1,
                saltos = (parte.saltos + 1).coerceAtMost(TTL_MAXIMO),
            ),
        )
    }

    fun aBytes(parte: Parte): ByteArray
    {
        val idBytes = deHex(parte.id) ?: ByteArray(LARGO_ID)
        val cabecera = byteArrayOf(
            (((VERSION and 0xF) shl 4) or (parte.tipo.valor and 0xF)).toByte(),
            (((parte.ttl and 0xF) shl 4) or (parte.saltos and 0xF)).toByte(),
            idBytes[0],
            idBytes[1],
            idBytes[2],
            idBytes[3],
            (parte.indice and 0xFF).toByte(),
            (parte.total and 0xFF).toByte(),
        )
        return cabecera + parte.carga
    }

    fun deBytes(bytes: ByteArray?): Parte?
    {
        if (bytes == null || bytes.size <= LARGO_CABECERA || bytes.size > TOPE_PAQUETE)
        {
            return null
        }
        val version = (bytes[0].toInt() shr 4) and 0xF
        val tipo = Tipo.de(bytes[0].toInt() and 0xF)
        if (version != VERSION || tipo == null)
        {
            return null
        }
        val indice = bytes[6].toInt() and 0xFF
        val total = bytes[7].toInt() and 0xFF
        if (total < 1 || indice >= total)
        {
            return null
        }
        return Parte(
            id = aHex(bytes.copyOfRange(2, 2 + LARGO_ID)),
            tipo = tipo,
            ttl = ((bytes[1].toInt() shr 4) and 0xF).coerceIn(0, TTL_MAXIMO),
            saltos = (bytes[1].toInt() and 0xF).coerceIn(0, TTL_MAXIMO),
            indice = indice,
            total = total,
            carga = bytes.copyOfRange(LARGO_CABECERA, bytes.size),
        )
    }

    private fun fragmentar(id: String, tipo: Tipo, ttl: Int, flujo: ByteArray): List<Parte>?
    {
        val total = (flujo.size + TOPE_CARGA - 1) / TOPE_CARGA
        if (total < 1 || total > MAXIMO_PARTES)
        {
            return null
        }
        return (0 until total).map { indice ->
            val desde = indice * TOPE_CARGA
            val hasta = minOf(desde + TOPE_CARGA, flujo.size)
            Parte(
                id = id,
                tipo = tipo,
                ttl = ttl.coerceIn(1, TTL_MAXIMO),
                saltos = 0,
                indice = indice,
                total = total,
                carga = flujo.copyOfRange(desde, hasta),
            )
        }
    }

    private fun unir(partes: List<Parte>): ByteArray?
    {
        if (partes.isEmpty())
        {
            return null
        }
        val total = partes.first().total
        val id = partes.first().id
        val tipo = partes.first().tipo
        val ordenadas = partes.distinctBy { it.indice }.sortedBy { it.indice }
        if (ordenadas.size != total || ordenadas.any { it.total != total || it.id != id || it.tipo != tipo })
        {
            return null
        }
        if (ordenadas.mapIndexed { posicion, parte -> parte.indice == posicion }.any { !it })
        {
            return null
        }
        var flujo = ByteArray(0)
        for (parte in ordenadas)
        {
            flujo += parte.carga
        }
        return flujo
    }

    private fun codificarCuerpo(cuerpo: Cuerpo): ByteArray?
    {
        return when (cuerpo.tipo)
        {
            Tipo.TEXTO ->
            {
                val bytes = (cuerpo.texto ?: return null).toByteArray(Charsets.UTF_8)
                if (bytes.isEmpty() || bytes.size > TOPE_TEXTO) null else bytes
            }

            Tipo.OFERTA ->
            {
                val oferta = cuerpo.oferta ?: return null
                val contenido = deHex(oferta.idContenido) ?: return null
                val nombre = oferta.nombre.toByteArray(Charsets.UTF_8)
                if (contenido.size != LARGO_CONTENIDO || nombre.size > TOPE_NOMBRE ||
                    oferta.tamano < 0 || oferta.tamano > 0xFFFFFF
                )
                {
                    return null
                }
                contenido + byteArrayOf(
                    ((oferta.tamano shr 16) and 0xFF).toByte(),
                    ((oferta.tamano shr 8) and 0xFF).toByte(),
                    (oferta.tamano and 0xFF).toByte(),
                    (oferta.clase.valor and 0xFF).toByte(),
                ) + nombre
            }

            Tipo.MINIATURA ->
            {
                val bytes = runCatching { Cripto.deBase64(cuerpo.miniatura ?: return null) }.getOrNull()
                    ?: return null
                if (bytes.isEmpty() || bytes.size > TOPE_MINIATURA) null else bytes
            }
        }
    }

    private fun decodificarCuerpo(tipo: Tipo, emisorId: String, contenido: ByteArray): Cuerpo?
    {
        return when (tipo)
        {
            Tipo.TEXTO ->
            {
                if (contenido.isEmpty() || contenido.size > TOPE_TEXTO)
                {
                    null
                }
                else
                {
                    Cuerpo(emisorId, tipo, texto = String(contenido, Charsets.UTF_8))
                }
            }

            Tipo.OFERTA ->
            {
                val fijo = LARGO_CONTENIDO + LARGO_TAMANO + 1
                if (contenido.size < fijo || contenido.size - fijo > TOPE_NOMBRE)
                {
                    return null
                }
                val clase = Clase.de(contenido[fijo - 1].toInt() and 0xFF) ?: return null
                val tamano = ((contenido[LARGO_CONTENIDO].toInt() and 0xFF) shl 16) or
                    ((contenido[LARGO_CONTENIDO + 1].toInt() and 0xFF) shl 8) or
                    (contenido[LARGO_CONTENIDO + 2].toInt() and 0xFF)
                Cuerpo(
                    emisorId = emisorId,
                    tipo = tipo,
                    oferta = Oferta(
                        idContenido = aHex(contenido.copyOfRange(0, LARGO_CONTENIDO)),
                        tamano = tamano,
                        clase = clase,
                        nombre = String(contenido.copyOfRange(fijo, contenido.size), Charsets.UTF_8),
                    ),
                )
            }

            Tipo.MINIATURA ->
            {
                if (contenido.isEmpty() || contenido.size > TOPE_MINIATURA)
                {
                    null
                }
                else
                {
                    Cuerpo(emisorId, tipo, miniatura = Cripto.aBase64(contenido))
                }
            }
        }
    }

    private fun sello(
        idBytes: ByteArray,
        tipo: Tipo,
        emisor: ByteArray,
        contenido: ByteArray,
        publicaOtro: ByteArray,
        secretaPropia: ByteArray,
    ): ByteArray
    {
        val entrada = "vixxer-lora".toByteArray(Charsets.UTF_8) +
            Cripto.secretoCompartido(publicaOtro, secretaPropia) +
            idBytes +
            byteArrayOf(tipo.valor.toByte()) +
            emisor +
            contenido
        return Cripto.hash(entrada).copyOfRange(0, LARGO_PRUEBA)
    }

    private fun nonceDe(publicaEfimera: ByteArray, publicaDestino: ByteArray): ByteArray
    {
        return Cripto.hash(publicaEfimera + publicaDestino).copyOfRange(0, Cripto.TAMANO_NONCE)
    }

    private fun llaveDe(base64: String): ByteArray?
    {
        val bytes = runCatching { Cripto.deBase64(base64) }.getOrNull() ?: return null
        return if (bytes.size == Cripto.TAMANO_CLAVE) bytes else null
    }

    private fun constanteIguales(uno: ByteArray, otro: ByteArray): Boolean
    {
        if (uno.size != otro.size)
        {
            return false
        }
        var diferencia = 0
        for (i in uno.indices)
        {
            diferencia = diferencia or (uno[i].toInt() xor otro[i].toInt())
        }
        return diferencia == 0
    }

    private fun aHex(bytes: ByteArray): String
    {
        val digitos = "0123456789abcdef"
        val texto = StringBuilder(bytes.size * 2)
        for (byte in bytes)
        {
            val valor = byte.toInt() and 0xFF
            texto.append(digitos[valor shr 4])
            texto.append(digitos[valor and 0xF])
        }
        return texto.toString()
    }

    private fun deHex(texto: String): ByteArray?
    {
        if (texto.isEmpty() || texto.length % 2 != 0)
        {
            return null
        }
        return runCatching {
            ByteArray(texto.length / 2) { texto.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }

    private fun bytesDeUuid(texto: String): ByteArray?
    {
        val uuid = runCatching { UUID.fromString(texto) }.getOrNull() ?: return null
        val bytes = ByteArray(LARGO_EMISOR)
        var alto = uuid.mostSignificantBits
        var bajo = uuid.leastSignificantBits
        for (i in 7 downTo 0)
        {
            bytes[i] = (alto and 0xFF).toByte()
            bytes[i + 8] = (bajo and 0xFF).toByte()
            alto = alto shr 8
            bajo = bajo shr 8
        }
        return bytes
    }

    private fun uuidDeBytes(bytes: ByteArray): String
    {
        var alto = 0L
        var bajo = 0L
        for (i in 0 until 8)
        {
            alto = (alto shl 8) or (bytes[i].toLong() and 0xFF)
            bajo = (bajo shl 8) or (bytes[i + 8].toLong() and 0xFF)
        }
        return UUID(alto, bajo).toString()
    }

    private fun bytesAleatorios(cuantos: Int): ByteArray
    {
        val bytes = ByteArray(cuantos)
        SecureRandom().nextBytes(bytes)
        return bytes
    }
}
