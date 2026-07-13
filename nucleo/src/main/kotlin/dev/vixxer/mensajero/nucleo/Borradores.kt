package dev.vixxer.mensajero.nucleo

import org.json.JSONObject

class Borradores(private val almacen: Almacen)
{
    private fun clave(chat: String) = "vixxer_borrador_$chat"

    fun leer(chat: String): JSONObject
    {
        return try
        {
            JSONObject(almacen.leer(clave(chat)) ?: "{}")
        }
        catch (e: Exception)
        {
            JSONObject()
        }
    }

    fun guardar(chat: String, datos: JSONObject)
    {
        val limpio = JSONObject()
        val texto = datos.optString("texto")
        if (texto.isNotEmpty())
        {
            limpio.put("texto", texto)
        }
        val audio = datos.optJSONObject("audio")
        if (audio != null && audio.optString("uri").isNotEmpty())
        {
            limpio.put("audio", audio)
        }
        if (limpio.length() == 0)
        {
            almacen.borrar(clave(chat))
            return
        }
        almacen.escribir(clave(chat), limpio.toString())
    }

    fun limpiar(chat: String)
    {
        almacen.borrar(clave(chat))
    }
}
