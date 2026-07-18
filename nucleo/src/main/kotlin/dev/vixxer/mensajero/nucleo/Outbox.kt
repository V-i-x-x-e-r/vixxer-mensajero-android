package dev.vixxer.mensajero.nucleo

import org.json.JSONArray
import org.json.JSONObject

class Outbox(private val almacen: Almacen)
{
    enum class Tipo(val valor: String)
    {
        DIRECTO("directo"),
        GRUPO("grupo");

        companion object
        {
            fun de(valor: String): Tipo? = entries.find { it.valor == valor }
        }
    }

    data class Pendiente(
        val tipo: Tipo,
        val destinoId: String,
        val datos: JSONObject,
    )
    {
        val clienteId: String
            get() = datos.optString("localId")
    }

    private val clave = "vixxer_outbox_v2"

    @Synchronized
    fun leer(destId: String): List<JSONObject>
    {
        migrarLegado(destId)
        return leerTodosInterno()
            .filter { it.tipo == Tipo.DIRECTO && it.destinoId == destId }
            .map { copia(it.datos) }
    }

    @Synchronized
    fun leerGrupo(grupoId: String): List<JSONObject> = leerTodosInterno()
        .filter { it.tipo == Tipo.GRUPO && it.destinoId == grupoId }
        .map { copia(it.datos) }

    @Synchronized
    fun leerTodos(): List<Pendiente> = leerTodosInterno().map {
        it.copy(datos = copia(it.datos))
    }

    @Synchronized
    fun contiene(pendiente: Pendiente): Boolean = leerTodosInterno().any {
        misma(it, pendiente)
    }

    fun importarDesde(origen: Outbox): Int
    {
        if (origen === this)
        {
            return 0
        }
        val candidatos = origen.leerTodos()
        if (candidatos.isEmpty())
        {
            return 0
        }
        mezclar(candidatos)
        val guardados = leerTodos()
        var importados = 0
        for (candidato in candidatos)
        {
            if (guardados.none { misma(it, candidato) })
            {
                continue
            }
            when (candidato.tipo)
            {
                Tipo.DIRECTO -> origen.quitar(candidato.destinoId, candidato.clienteId)
                Tipo.GRUPO -> origen.quitarGrupo(candidato.destinoId, candidato.clienteId)
            }
            importados += 1
        }
        return importados
    }

    fun agregar(destId: String, item: JSONObject)
    {
        agregar(Tipo.DIRECTO, destId, item)
    }

    fun agregarGrupo(grupoId: String, item: JSONObject)
    {
        agregar(Tipo.GRUPO, grupoId, item)
    }

    @Synchronized
    fun quitar(destId: String, localId: String)
    {
        quitar(Tipo.DIRECTO, destId, localId)
    }

    @Synchronized
    fun quitarGrupo(grupoId: String, localId: String)
    {
        quitar(Tipo.GRUPO, grupoId, localId)
    }

    @Synchronized
    fun registrarFallo(pendiente: Pendiente, ahoraMs: Long = System.currentTimeMillis())
    {
        val actuales = leerTodosInterno().toMutableList()
        val indice = actuales.indexOfFirst { misma(it, pendiente) }
        if (indice < 0)
        {
            return
        }
        val item = actuales[indice]
        val intentos = item.datos.optInt("intentos") + 1
        val espera = minOf(300_000L, 2_000L * (1L shl minOf(intentos - 1, 7)))
        item.datos.put("intentos", intentos)
        item.datos.put("reintentar_en", ahoraMs + espera)
        escribir(actuales)
    }

    fun listoParaReintentar(pendiente: Pendiente, ahoraMs: Long = System.currentTimeMillis()): Boolean =
        pendiente.datos.optLong("reintentar_en", 0L) <= ahoraMs

    @Synchronized
    private fun agregar(tipo: Tipo, destinoId: String, item: JSONObject)
    {
        require(destinoId.isNotBlank()) { "El destino del mensaje es obligatorio" }
        require(item.optString("localId").isNotBlank()) { "El clienteId del mensaje es obligatorio" }
        val actuales = leerTodosInterno().toMutableList()
        val nuevo = Pendiente(tipo, destinoId, copia(item))
        val indice = actuales.indexOfFirst { misma(it, nuevo) }
        if (indice >= 0)
        {
            actuales[indice] = nuevo
        }
        else
        {
            actuales.add(nuevo)
        }
        escribir(actuales)
    }

    @Synchronized
    private fun mezclar(candidatos: List<Pendiente>)
    {
        val actuales = leerTodosInterno().toMutableList()
        for (candidato in candidatos)
        {
            if (actuales.none { misma(it, candidato) })
            {
                actuales.add(candidato.copy(datos = copia(candidato.datos)))
            }
        }
        escribir(actuales)
    }

    private fun quitar(tipo: Tipo, destinoId: String, localId: String)
    {
        escribir(leerTodosInterno().filterNot {
            it.tipo == tipo && it.destinoId == destinoId && it.clienteId == localId
        })
    }

    private fun migrarLegado(destId: String)
    {
        val claveVieja = "vixxer_outbox_$destId"
        val crudo = almacen.leer(claveVieja) ?: return
        val viejos = arreglo(crudo).map { Pendiente(Tipo.DIRECTO, destId, it) }
        if (viejos.isNotEmpty())
        {
            val actuales = leerTodosInterno().toMutableList()
            for (viejo in viejos)
            {
                if (actuales.none { misma(it, viejo) })
                {
                    actuales.add(viejo)
                }
            }
            escribir(actuales)
        }
        almacen.borrar(claveVieja)
    }

    private fun leerTodosInterno(): List<Pendiente>
    {
        val crudo = almacen.leer(clave) ?: return emptyList()
        return try
        {
            val arreglo = JSONArray(crudo)
            (0 until arreglo.length()).mapNotNull { i ->
                val entrada = arreglo.optJSONObject(i) ?: return@mapNotNull null
                val tipo = Tipo.de(entrada.optString("tipo")) ?: return@mapNotNull null
                val destino = entrada.optString("destino_id")
                val datos = entrada.optJSONObject("datos")
                if (destino.isBlank() || datos == null || datos.optString("localId").isBlank())
                {
                    null
                }
                else
                {
                    Pendiente(tipo, destino, datos)
                }
            }
        }
        catch (_: Exception)
        {
            emptyList()
        }
    }

    private fun escribir(items: List<Pendiente>)
    {
        if (items.isEmpty())
        {
            almacen.borrar(clave)
            return
        }
        val arreglo = JSONArray()
        for (item in items)
        {
            arreglo.put(JSONObject()
                .put("tipo", item.tipo.valor)
                .put("destino_id", item.destinoId)
                .put("datos", item.datos))
        }
        almacen.escribir(clave, arreglo.toString())
    }

    private fun arreglo(crudo: String): List<JSONObject> = try
    {
        val arreglo = JSONArray(crudo)
        (0 until arreglo.length()).mapNotNull { arreglo.optJSONObject(it) }
    }
    catch (_: Exception)
    {
        emptyList()
    }

    private fun misma(a: Pendiente, b: Pendiente): Boolean =
        a.tipo == b.tipo && a.destinoId == b.destinoId && a.clienteId == b.clienteId

    private fun copia(objeto: JSONObject) = JSONObject(objeto.toString())
}
