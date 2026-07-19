package dev.vixxer.mensajero.nucleo

import org.json.JSONObject
import kotlin.random.Random

object MeshCercania
{
    const val SERVICIO_UUID = "6f1d0001-5b3c-4a7e-9f21-7c9a1b2c3d4e"
    const val CARACTERISTICA_UUID = "6f1d0002-5b3c-4a7e-9f21-7c9a1b2c3d4e"
    const val DATO_CERCANIA_UUID = "0000a55a-0000-1000-8000-00805f9b34fb"

    enum class Accion
    {
        ENTREGAR,
        REENVIAR,
        DESCARTAR,
    }

    data class Sobre(
        val id: String,
        val remitenteId: String,
        val destinatarioId: String,
        val contenidoCifrado: String,
        val nonce: String,
        val ttl: Int,
        val firma: String? = null,
    )

    data class Decision(val accion: Accion, val sobre: Sobre? = null)

    fun crearSobre(
        remitenteId: String,
        destinatarioId: String,
        contenidoCifrado: String,
        nonce: String,
        ttl: Int = 5,
    ): Sobre = Sobre(
        id = "${System.currentTimeMillis()}-${cadenaAleatoria()}",
        remitenteId = remitenteId,
        destinatarioId = destinatarioId,
        contenidoCifrado = contenidoCifrado,
        nonce = nonce,
        ttl = ttl,
    )

    fun procesar(sobre: Sobre?, miId: String, vistos: Vistos): Decision
    {
        if (sobre == null || sobre.id.isBlank() || vistos.visto(sobre.id))
        {
            return Decision(Accion.DESCARTAR)
        }
        vistos.marcar(sobre.id)
        if (sobre.destinatarioId == miId)
        {
            return Decision(Accion.ENTREGAR)
        }
        if (sobre.ttl <= 1)
        {
            return Decision(Accion.DESCARTAR)
        }
        return Decision(Accion.REENVIAR, sobre.copy(ttl = sobre.ttl - 1))
    }

    fun aJson(sobre: Sobre): String = JSONObject()
        .put("id", sobre.id)
        .put("remitenteId", sobre.remitenteId)
        .put("destinatarioId", sobre.destinatarioId)
        .put("contenidoCifrado", sobre.contenidoCifrado)
        .put("nonce", sobre.nonce)
        .put("ttl", sobre.ttl)
        .put("firma", sobre.firma ?: JSONObject.NULL)
        .toString()

    fun deJson(texto: String): Sobre?
    {
        return try
        {
            val obj = JSONObject(texto)
            val id = obj.optString("id")
            if (id.isBlank())
            {
                return null
            }
            Sobre(
                id = id,
                remitenteId = obj.optString("remitenteId"),
                destinatarioId = obj.optString("destinatarioId"),
                contenidoCifrado = obj.optString("contenidoCifrado"),
                nonce = obj.optString("nonce"),
                ttl = obj.optInt("ttl", 1),
                firma = if (obj.isNull("firma")) null else obj.optString("firma"),
            )
        }
        catch (_: Exception)
        {
            null
        }
    }

    private fun cadenaAleatoria(): String
    {
        val alfabeto = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..8).map { alfabeto[Random.nextInt(alfabeto.length)] }.joinToString("")
    }
}

class Vistos(private val maximo: Int = 500)
{
    private val conjunto = HashSet<String>()
    private val orden = ArrayDeque<String>()

    @Synchronized
    fun visto(id: String): Boolean = conjunto.contains(id)

    @Synchronized
    fun marcar(id: String)
    {
        if (conjunto.contains(id))
        {
            return
        }
        conjunto.add(id)
        orden.addLast(id)
        if (orden.size > maximo)
        {
            val viejo = orden.removeFirst()
            conjunto.remove(viejo)
        }
    }
}
