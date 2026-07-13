package dev.vixxer.mensajero.nucleo

object Resumen
{
    fun resumenMensaje(texto: String?): String
    {
        if (texto.isNullOrEmpty())
        {
            return "Mensaje"
        }
        val obj = Efimero.objetoDe(texto) ?: return texto
        return when (obj.optString("t"))
        {
            "img" -> if (obj.optString("cap").isNotEmpty()) "Foto · ${obj.optString("cap")}" else "Foto"
            "video" -> if (obj.optString("cap").isNotEmpty()) "Video · ${obj.optString("cap")}" else "Video"
            "audio" -> "Nota de voz"
            "sticker" -> "Sticker"
            "file" -> obj.optString("nombre").ifEmpty { "Documento" }
            "tmp" -> obj.optString("m")
            "tmpaviso" -> "Mensajes temporales"
            else -> texto
        }
    }
}
