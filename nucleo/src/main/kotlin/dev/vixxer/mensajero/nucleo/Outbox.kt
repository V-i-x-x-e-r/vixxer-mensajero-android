package dev.vixxer.mensajero.nucleo

import org.json.JSONArray
import org.json.JSONObject

class Outbox(private val almacen: Almacen)
{
    private fun clave(destId: String) = "vixxer_outbox_$destId"

    fun leer(destId: String): List<JSONObject>
    {
        val crudo = almacen.leer(clave(destId)) ?: return emptyList()
        return try
        {
            val arreglo = JSONArray(crudo)
            (0 until arreglo.length()).mapNotNull { arreglo.optJSONObject(it) }
        }
        catch (e: Exception)
        {
            emptyList()
        }
    }

    fun agregar(destId: String, item: JSONObject)
    {
        val items = leer(destId) + item
        escribir(destId, items)
    }

    fun quitar(destId: String, localId: String)
    {
        escribir(destId, leer(destId).filter { it.optString("localId") != localId })
    }

    private fun escribir(destId: String, items: List<JSONObject>)
    {
        almacen.escribir(clave(destId), JSONArray(items).toString())
    }
}
