package dev.vixxer.mensajero.nucleo

object IdentidadRotativa
{
    const val SEGUNDOS_VENTANA = 600L
    const val TAMANO_TOKEN = 16

    fun ventanaActual(epochSegundos: Long): Long
    {
        return epochSegundos / SEGUNDOS_VENTANA
    }

    fun token(miId: String, secretoCompartido: ByteArray, ventana: Long): String
    {
        val entrada = secretoCompartido + bytesVentana(ventana) + miId.toByteArray(Charsets.UTF_8)
        val resumen = Cripto.hash(entrada)
        return Cripto.aBase64(resumen.copyOfRange(0, TAMANO_TOKEN))
    }

    fun tokenActual(miId: String, secretoCompartido: ByteArray, epochSegundos: Long): String
    {
        return token(miId, secretoCompartido, ventanaActual(epochSegundos))
    }

    fun coincidir(
        tokenObservado: String,
        amigos: Map<String, ByteArray>,
        epochSegundos: Long,
    ): String?
    {
        val ventana = ventanaActual(epochSegundos)
        for ((amigoId, secreto) in amigos)
        {
            for (desfase in -1..1)
            {
                if (token(amigoId, secreto, ventana + desfase) == tokenObservado)
                {
                    return amigoId
                }
            }
        }
        return null
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
