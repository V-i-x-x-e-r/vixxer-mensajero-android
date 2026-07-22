package dev.vixxer.mensajero.nucleo

import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

class Llaves(private val api: ClienteApi)
{
    private val cache = ConcurrentHashMap<String, String>()

    fun sembrar(userId: String, llave: String?)
    {
        if (llave != null && !cache.containsKey(userId))
        {
            cache[userId] = llave
        }
    }

    fun llavePublicaDe(userId: String, forzar: Boolean = false): String
    {
        if (!forzar)
        {
            val guardada = cache[userId]
            if (guardada != null)
            {
                return guardada
            }
        }
        val respuesta = api.llavePublica(userId) as JSONObject
        val llave = respuesta.getString("llave_publica")
        cache[userId] = llave
        return llave
    }
}
