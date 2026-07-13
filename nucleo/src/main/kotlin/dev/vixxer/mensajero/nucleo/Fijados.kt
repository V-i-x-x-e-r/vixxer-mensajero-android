package dev.vixxer.mensajero.nucleo

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class Fijados(private val almacen: Almacen)
{
    data class Fijado(val id: String, val texto: String?, val remitenteId: String?)

    private fun clave(convId: String) = "vixxer_fijado_$convId"

    fun leer(convId: String): List<Fijado>
    {
        val crudo = almacen.leer(clave(convId)) ?: return emptyList()
        try
        {
            val valor = JSONTokener(crudo).nextValue()
            if (valor is JSONArray)
            {
                return (0 until valor.length()).mapNotNull { deJson(valor.optJSONObject(it)) }
            }
            if (valor is JSONObject)
            {
                return listOfNotNull(deJson(valor))
            }
        }
        catch (e: Exception)
        {
        }
        return emptyList()
    }

    fun alternar(convId: String, mensaje: Fijado): List<Fijado>
    {
        val lista = leer(convId)
        val existe = lista.any { it.id == mensaje.id }
        val nueva = if (existe) lista.filter { it.id != mensaje.id } else lista + mensaje
        escribir(convId, nueva)
        return nueva
    }

    fun quitar(convId: String, id: String): List<Fijado>
    {
        val nueva = leer(convId).filter { it.id != id }
        escribir(convId, nueva)
        return nueva
    }

    private fun escribir(convId: String, lista: List<Fijado>)
    {
        if (lista.isEmpty())
        {
            almacen.borrar(clave(convId))
            return
        }
        val arreglo = JSONArray()
        for (f in lista)
        {
            val obj = JSONObject().put("id", f.id)
            obj.putOpt("texto", f.texto)
            obj.putOpt("remitente_id", f.remitenteId)
            arreglo.put(obj)
        }
        almacen.escribir(clave(convId), arreglo.toString())
    }

    private fun deJson(obj: JSONObject?): Fijado?
    {
        if (obj == null || obj.optString("id").isEmpty())
        {
            return null
        }
        val texto = if (obj.has("texto")) obj.optString("texto") else null
        val remitente = if (obj.has("remitente_id")) obj.optString("remitente_id") else null
        return Fijado(obj.optString("id"), texto, remitente)
    }
}
