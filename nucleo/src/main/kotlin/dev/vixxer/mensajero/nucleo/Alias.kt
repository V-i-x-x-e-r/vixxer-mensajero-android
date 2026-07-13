package dev.vixxer.mensajero.nucleo

import org.json.JSONObject

class Alias(private val almacen: Almacen)
{
    private val clave = "vixxer_alias"

    fun leerTodos(): MutableMap<String, String>
    {
        return mapaDe(almacen.leer(clave))
    }

    fun de(convId: String): String? = leerTodos()[convId]

    fun guardar(convId: String, nombre: String?)
    {
        val mapa = leerTodos()
        val limpio = (nombre ?: "").trim()
        if (limpio.isNotEmpty())
        {
            mapa[convId] = limpio
        }
        else
        {
            mapa.remove(convId)
        }
        almacen.escribir(clave, JSONObject(mapa as Map<*, *>).toString())
    }
}

internal fun mapaDe(crudo: String?): MutableMap<String, String>
{
    val salida = LinkedHashMap<String, String>()
    if (crudo == null)
    {
        return salida
    }
    try
    {
        val obj = JSONObject(crudo)
        for (k in obj.keys())
        {
            salida[k] = obj.optString(k)
        }
    }
    catch (e: Exception)
    {
    }
    return salida
}
