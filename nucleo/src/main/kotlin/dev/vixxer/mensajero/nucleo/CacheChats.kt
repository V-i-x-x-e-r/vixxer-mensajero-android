package dev.vixxer.mensajero.nucleo

import org.json.JSONArray
import org.json.JSONObject

class CacheChats(private val almacen: Almacen)
{
    private fun claveChat(otroId: String) = "vixxer_chat_$otroId"

    fun leerChat(otroId: String): JSONArray? = arregloDe(almacen.leer(claveChat(otroId)))

    fun guardarChat(otroId: String, mensajes: JSONArray)
    {
        val limpios = JSONArray()
        for (i in 0 until mensajes.length())
        {
            val m = mensajes.optJSONObject(i) ?: continue
            if (!m.optString("id").startsWith("local-"))
            {
                limpios.put(m)
            }
        }
        val recorte = JSONArray()
        for (i in maxOf(0, limpios.length() - 50) until limpios.length())
        {
            recorte.put(limpios.get(i))
        }
        almacen.escribir(claveChat(otroId), recorte.toString())
    }

    fun borrarChat(otroId: String)
    {
        almacen.borrar(claveChat(otroId))
    }

    fun leerLista(): JSONObject? = objetoJson(almacen.leer("vixxer_lista_chats"))

    fun guardarLista(datos: JSONObject)
    {
        almacen.escribir("vixxer_lista_chats", datos.toString())
    }

    fun leerGrupos(): JSONObject? = objetoJson(almacen.leer("vixxer_lista_grupos"))

    fun guardarGrupos(datos: JSONObject)
    {
        almacen.escribir("vixxer_lista_grupos", datos.toString())
    }

    private fun objetoJson(crudo: String?): JSONObject?
    {
        if (crudo == null)
        {
            return null
        }
        return try
        {
            JSONObject(crudo)
        }
        catch (e: Exception)
        {
            null
        }
    }

    private fun arregloDe(crudo: String?): JSONArray?
    {
        if (crudo == null)
        {
            return null
        }
        return try
        {
            JSONArray(crudo)
        }
        catch (e: Exception)
        {
            null
        }
    }
}
