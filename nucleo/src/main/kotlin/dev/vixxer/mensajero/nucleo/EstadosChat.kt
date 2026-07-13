package dev.vixxer.mensajero.nucleo

import org.json.JSONArray

class EstadosChat(private val almacen: Almacen)
{
    data class Estados(
        val fijados: List<String>,
        val silenciados: List<String>,
        val ocultos: List<String>,
        val archivados: List<String>,
        val favoritos: List<String>)

    fun leerEstados(): Estados = Estados(
        leerLista("vixxer_fijados"),
        leerLista("vixxer_silenciados"),
        leerLista("vixxer_ocultos"),
        leerLista("vixxer_archivados"),
        leerLista("vixxer_favoritos"))

    fun alternarFijado(id: String): List<String> = alternar("vixxer_fijados", id)

    fun alternarSilenciado(id: String): List<String> = alternar("vixxer_silenciados", id)

    fun alternarArchivado(id: String): List<String> = alternar("vixxer_archivados", id)

    fun alternarFavorito(id: String): List<String> = alternar("vixxer_favoritos", id)

    fun ocultar(id: String)
    {
        val lista = leerLista("vixxer_ocultos")
        if (id !in lista)
        {
            escribir("vixxer_ocultos", lista + id)
        }
    }

    fun mostrar(id: String)
    {
        val lista = leerLista("vixxer_ocultos")
        if (id in lista)
        {
            escribir("vixxer_ocultos", lista.filter { it != id })
        }
    }

    private fun alternar(clave: String, id: String): List<String>
    {
        val lista = leerLista(clave)
        val nueva = if (id in lista) lista.filter { it != id } else lista + id
        escribir(clave, nueva)
        return nueva
    }

    private fun leerLista(clave: String): List<String>
    {
        val crudo = almacen.leer(clave) ?: return emptyList()
        return try
        {
            val arreglo = JSONArray(crudo)
            (0 until arreglo.length()).map { arreglo.optString(it) }
        }
        catch (e: Exception)
        {
            emptyList()
        }
    }

    private fun escribir(clave: String, lista: List<String>)
    {
        almacen.escribir(clave, JSONArray(lista).toString())
    }
}
