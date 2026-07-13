package dev.vixxer.mensajero.nucleo

import org.json.JSONArray

class Ocultos(private val almacen: Almacen)
{
    private fun clave(chat: String) = "vixxer_ocultos_$chat"

    fun leer(chat: String): LinkedHashSet<String>
    {
        val salida = LinkedHashSet<String>()
        val crudo = almacen.leer(clave(chat)) ?: return salida
        try
        {
            val arreglo = JSONArray(crudo)
            for (i in 0 until arreglo.length())
            {
                salida.add(arreglo.optString(i))
            }
        }
        catch (e: Exception)
        {
        }
        return salida
    }

    fun ocultar(chat: String, id: String): LinkedHashSet<String>
    {
        val set = leer(chat)
        set.add(id)
        almacen.escribir(clave(chat), JSONArray(set.toList().takeLast(500)).toString())
        return set
    }
}
