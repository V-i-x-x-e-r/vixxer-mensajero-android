package dev.vixxer.mensajero.nucleo

import org.json.JSONObject
import kotlin.random.Random

object MeshCercania
{
    const val SERVICIO_UUID = "6f1d0001-5b3c-4a7e-9f21-7c9a1b2c3d4e"
    const val CARACTERISTICA_UUID = "6f1d0002-5b3c-4a7e-9f21-7c9a1b2c3d4e"
    const val CARACTERISTICA_CAPS_UUID = "6f1d0003-5b3c-4a7e-9f21-7c9a1b2c3d4e"
    const val DATO_CERCANIA_UUID = "0000a55a-0000-1000-8000-00805f9b34fb"
    const val DATO_CAPS_UUID = "0000a55b-0000-1000-8000-00805f9b34fb"

    enum class Accion
    {
        ENTREGAR,
        REENVIAR,
        DESCARTAR,
    }

    const val TIPO_DIRECTO = "directo"
    const val TIPO_GRUPO = "grupo"
    const val TTL_MAXIMO = 5

    data class Sobre(
        val id: String,
        val remitenteId: String,
        val destinatarioId: String,
        val contenidoCifrado: String,
        val nonce: String,
        val ttl: Int,
        val saltos: Int = 0,
        val firma: String? = null,
        val clienteId: String? = null,
        val tipo: String = TIPO_DIRECTO,
        val respuestaA: String? = null,
    )

    data class Decision(val accion: Accion, val sobre: Sobre? = null)

    fun crearSobre(
        remitenteId: String,
        destinatarioId: String,
        contenidoCifrado: String,
        nonce: String,
        ttl: Int = TTL_MAXIMO,
        saltos: Int = 0,
        clienteId: String? = null,
        tipo: String = TIPO_DIRECTO,
        respuestaA: String? = null,
    ): Sobre = Sobre(
        id = "${System.currentTimeMillis()}-${cadenaAleatoria()}",
        remitenteId = remitenteId,
        destinatarioId = destinatarioId,
        contenidoCifrado = contenidoCifrado,
        nonce = nonce,
        ttl = ttl.coerceIn(1, TTL_MAXIMO),
        saltos = saltos.coerceIn(0, TTL_MAXIMO),
        clienteId = clienteId,
        tipo = tipo,
        respuestaA = respuestaA,
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
        return Decision(
            Accion.REENVIAR,
            sobre.copy(
                ttl = sobre.ttl - 1,
                saltos = (sobre.saltos + 1).coerceAtMost(TTL_MAXIMO),
            ),
        )
    }

    fun aJson(sobre: Sobre): String = JSONObject()
        .put("id", sobre.id)
        .put("remitenteId", sobre.remitenteId)
        .put("destinatarioId", sobre.destinatarioId)
        .put("contenidoCifrado", sobre.contenidoCifrado)
        .put("nonce", sobre.nonce)
        .put("ttl", sobre.ttl)
        .put("saltos", sobre.saltos)
        .put("firma", sobre.firma ?: JSONObject.NULL)
        .put("clienteId", sobre.clienteId ?: JSONObject.NULL)
        .put("tipo", sobre.tipo)
        .put("respuestaA", sobre.respuestaA ?: JSONObject.NULL)
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
                ttl = obj.optInt("ttl", 1).coerceIn(0, TTL_MAXIMO),
                saltos = obj.optInt("saltos", 0).coerceIn(0, TTL_MAXIMO),
                firma = if (obj.isNull("firma")) null else obj.optString("firma"),
                clienteId = if (obj.isNull("clienteId")) null else obj.optString("clienteId"),
                tipo = obj.optString("tipo").ifBlank { TIPO_DIRECTO },
                respuestaA = if (obj.isNull("respuestaA")) null else obj.optString("respuestaA"),
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
