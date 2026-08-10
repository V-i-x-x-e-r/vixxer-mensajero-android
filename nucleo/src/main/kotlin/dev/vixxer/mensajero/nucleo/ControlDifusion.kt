package dev.vixxer.mensajero.nucleo

import org.json.JSONObject

class ControlDifusion(
    private val almacen: Almacen,
    private val reloj: () -> Long = System::currentTimeMillis,
)
{
    enum class Modo(val valor: String)
    {
        NADIE("nadie"),
        SOLO_AMIGOS("solo_amigos"),
        TODOS("todos");

        companion object
        {
            fun de(valor: String?): Modo = entries.firstOrNull { it.valor == valor } ?: SOLO_AMIGOS
        }
    }

    fun modo(): Modo
    {
        val guardado = Modo.de(almacen.leer(CLAVE_MODO))
        if (guardado != Modo.TODOS)
        {
            return guardado
        }
        if (reloj() >= vencimientoTodos())
        {
            return Modo.SOLO_AMIGOS
        }
        return Modo.TODOS
    }

    fun fijarModo(modo: Modo)
    {
        almacen.escribir(CLAVE_MODO, modo.valor)
        if (modo == Modo.TODOS)
        {
            almacen.escribir(CLAVE_VENCIMIENTO, (reloj() + MILIS_TODOS).toString())
        }
        else
        {
            almacen.borrar(CLAVE_VENCIMIENTO)
        }
    }

    fun segundosRestantesDeTodos(): Long
    {
        if (modo() != Modo.TODOS)
        {
            return 0
        }
        return ((vencimientoTodos() - reloj()) / 1000).coerceAtLeast(0)
    }

    fun aceptaDe(emisorId: String, esAmigo: Boolean): Boolean
    {
        if (silenciado(emisorId))
        {
            return false
        }
        return when (modo())
        {
            Modo.NADIE -> false
            Modo.SOLO_AMIGOS -> esAmigo
            Modo.TODOS -> true
        }
    }

    fun registrarOferta(emisorId: String, ofertaId: String): Boolean
    {
        val estado = leerEstado()
        val pendiente = estado.optJSONObject(emisorId)
        val ahora = reloj()
        if (pendiente != null)
        {
            val vence = pendiente.optLong("vence", 0)
            if (ahora < vence && pendiente.optString("oferta") != ofertaId)
            {
                return false
            }
        }
        val nuevo = pendiente ?: JSONObject()
        nuevo.put("oferta", ofertaId)
        nuevo.put("vence", ahora + MILIS_OFERTA)
        estado.put(emisorId, nuevo)
        guardarEstado(estado)
        return true
    }

    fun cerrarOferta(emisorId: String)
    {
        val estado = leerEstado()
        val pendiente = estado.optJSONObject(emisorId) ?: return
        pendiente.remove("oferta")
        pendiente.put("vence", 0)
        estado.put(emisorId, pendiente)
        guardarEstado(estado)
    }

    fun registrarRechazo(emisorId: String)
    {
        val estado = leerEstado()
        val pendiente = estado.optJSONObject(emisorId) ?: JSONObject()
        val rechazos = pendiente.optInt("rechazos", 0) + 1
        pendiente.put("rechazos", rechazos)
        pendiente.remove("oferta")
        pendiente.put("vence", 0)
        if (rechazos >= MAXIMO_RECHAZOS)
        {
            pendiente.put("silencio", reloj() + MILIS_SILENCIO)
            pendiente.put("rechazos", 0)
        }
        estado.put(emisorId, pendiente)
        guardarEstado(estado)
    }

    fun silenciado(emisorId: String): Boolean
    {
        val pendiente = leerEstado().optJSONObject(emisorId) ?: return false
        return reloj() < pendiente.optLong("silencio", 0)
    }

    fun olvidar(emisorId: String)
    {
        val estado = leerEstado()
        estado.remove(emisorId)
        guardarEstado(estado)
    }

    private fun vencimientoTodos(): Long
    {
        return almacen.leer(CLAVE_VENCIMIENTO)?.toLongOrNull() ?: 0
    }

    private fun leerEstado(): JSONObject
    {
        val crudo = almacen.leer(CLAVE_EMISORES) ?: return JSONObject()
        return try
        {
            JSONObject(crudo)
        }
        catch (_: Exception)
        {
            JSONObject()
        }
    }

    private fun guardarEstado(estado: JSONObject)
    {
        podar(estado)
        almacen.escribir(CLAVE_EMISORES, estado.toString())
    }

    private fun podar(estado: JSONObject)
    {
        val ahora = reloj()
        for (clave in estado.keys().asSequence().toList())
        {
            val entrada = estado.optJSONObject(clave) ?: continue
            val vivo = ahora < entrada.optLong("silencio", 0) ||
                ahora < entrada.optLong("vence", 0) ||
                entrada.optInt("rechazos", 0) > 0
            if (!vivo)
            {
                estado.remove(clave)
            }
        }
        while (estado.length() > TOPE_EMISORES)
        {
            estado.remove(estado.keys().next())
        }
    }

    companion object
    {
        const val MAXIMO_RECHAZOS = 2
        const val MILIS_SILENCIO = 3_600_000L
        const val MILIS_TODOS = 600_000L
        const val MILIS_OFERTA = 60_000L
        const val TOPE_EMISORES = 200

        private const val CLAVE_MODO = "vixxer_difusion_modo"
        private const val CLAVE_VENCIMIENTO = "vixxer_difusion_vence"
        private const val CLAVE_EMISORES = "vixxer_difusion_emisores"
    }
}
