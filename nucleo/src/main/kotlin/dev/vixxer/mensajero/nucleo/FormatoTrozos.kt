package dev.vixxer.mensajero.nucleo

object FormatoTrozos
{
    const val MAGIA_B64 = "VlgyQ0gx"
    const val TROZO_B64 = 87376
    const val TROZO_BYTES = 65532

    private const val MARCA_1 = 0x56
    private const val MARCA_2 = 0x58
    private const val BANDERA_FINAL = 1

    data class Marco(
        val len: Int,
        val salto: Int,
        val marcado: Boolean = false,
        val final: Boolean = false,
        val cabeceraValida: Boolean = true,
    )

    fun nonceDeTrozo(nonceBase: ByteArray, indice: Int): ByteArray
    {
        val n = nonceBase.copyOf()
        n[20] = ((indice ushr 24) and 255).toByte()
        n[21] = ((indice ushr 16) and 255).toByte()
        n[22] = ((indice ushr 8) and 255).toByte()
        n[23] = (indice and 255).toByte()
        return n
    }

    fun nonceDeTrozoStreaming(nonceBase: ByteArray, indice: Int, final: Boolean): ByteArray
    {
        val n = nonceDeTrozo(nonceBase, indice)
        n[16] = 0x56
        n[17] = 0x58
        n[18] = 0x32
        n[19] = if (final) 0x46 else 0x53
        return n
    }

    fun enmarcar(sellado: ByteArray): ByteArray
    {
        val relleno = (3 - ((6 + sellado.size) % 3)) % 3
        val marco = ByteArray(6 + sellado.size + relleno)
        marco[0] = ((sellado.size ushr 16) and 255).toByte()
        marco[1] = ((sellado.size ushr 8) and 255).toByte()
        marco[2] = (sellado.size and 255).toByte()
        sellado.copyInto(marco, 6)
        return marco
    }

    fun enmarcarStreaming(sellado: ByteArray, final: Boolean): ByteArray
    {
        val marco = enmarcar(sellado)
        marco[3] = MARCA_1.toByte()
        marco[4] = MARCA_2.toByte()
        marco[5] = if (final) BANDERA_FINAL.toByte() else 0
        return marco
    }

    fun medidaMarco(cabecera: ByteArray): Marco
    {
        if (cabecera.size != 6)
        {
            return Marco(0, 0, cabeceraValida = false)
        }
        val len = ((cabecera[0].toInt() and 255) shl 16) or
            ((cabecera[1].toInt() and 255) shl 8) or
            (cabecera[2].toInt() and 255)
        val relleno = (3 - ((6 + len) % 3)) % 3
        val marca1 = cabecera[3].toInt() and 255
        val marca2 = cabecera[4].toInt() and 255
        val banderas = cabecera[5].toInt() and 255
        val legado = marca1 == 0 && marca2 == 0 && banderas == 0
        val marcado = marca1 == MARCA_1 && marca2 == MARCA_2 && banderas and BANDERA_FINAL.inv() == 0
        return Marco(
            len = len,
            salto = 6 + len + relleno,
            marcado = marcado,
            final = marcado && banderas and BANDERA_FINAL != 0,
            cabeceraValida = legado || marcado,
        )
    }

    fun cifrarArchivo(archivoB64: String, clave: ByteArray, nonce: ByteArray): String
    {
        val piezas = StringBuilder(MAGIA_B64)
        val total = maxOf(1, (archivoB64.length + TROZO_B64 - 1) / TROZO_B64)
        for (i in 0 until total)
        {
            val desde = i * TROZO_B64
            val hasta = minOf((i + 1) * TROZO_B64, archivoB64.length)
            val bytes = Cripto.deBase64(archivoB64.substring(desde, hasta))
            val sellado = Cripto.sellar(bytes, nonceDeTrozo(nonce, i), clave)
            piezas.append(Cripto.aBase64(enmarcar(sellado)))
        }
        return piezas.toString()
    }

    fun abrirTrozo(selladoB64: String, claveB64: String, nonceB64: String, indice: Int): String?
    {
        val abierto = Cripto.abrir(
            Cripto.deBase64(selladoB64),
            nonceDeTrozo(Cripto.deBase64(nonceB64), indice),
            Cripto.deBase64(claveB64),
        )
        return abierto?.let { Cripto.aBase64(it) }
    }
}
