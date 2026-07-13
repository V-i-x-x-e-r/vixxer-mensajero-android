package dev.vixxer.mensajero.nucleo

import org.json.JSONObject

object Efimero
{
    data class Opcion(val valor: Int, val etiqueta: String)
    data class Mensaje(val d: Int, val m: String)
    data class Aviso(val d: Int)

    val OPCIONES = listOf(
        Opcion(0, "Desactivado"),
        Opcion(60, "1 minuto"),
        Opcion(3600, "1 hora"),
        Opcion(86400, "1 día"))

    fun envolver(texto: String, segundos: Int): String =
        "{\"t\":\"tmp\",\"d\":$segundos,\"m\":${textoJson(texto)}}"

    fun envolverAviso(segundos: Int): String = "{\"t\":\"tmpaviso\",\"d\":$segundos}"

    fun leerEfimero(texto: String?): Mensaje?
    {
        val obj = objetoDe(texto) ?: return null
        return if (obj.optString("t") == "tmp") Mensaje(obj.optInt("d"), obj.optString("m")) else null
    }

    fun leerAviso(texto: String?): Aviso?
    {
        val obj = objetoDe(texto) ?: return null
        return if (obj.optString("t") == "tmpaviso") Aviso(obj.optInt("d")) else null
    }

    fun textoAviso(segundos: Int): String =
        if (segundos > 0) "Mensajes temporales activados · ${etiquetaDuracion(segundos)}"
        else "Mensajes temporales desactivados"

    fun expiraEn(texto: String?, enviadoEn: String?): Long?
    {
        val ef = leerEfimero(texto) ?: return null
        if (enviadoEn.isNullOrEmpty())
        {
            return null
        }
        return Fechas.aInstante(enviadoEn).toEpochMilli() + ef.d * 1000L
    }

    fun etiquetaDuracion(segundos: Int): String =
        OPCIONES.firstOrNull { it.valor == segundos }?.etiqueta ?: "${segundos}s"

    internal fun objetoDe(texto: String?): JSONObject?
    {
        if (texto.isNullOrEmpty() || texto[0] != '{')
        {
            return null
        }
        return try
        {
            JSONObject(texto)
        }
        catch (e: Exception)
        {
            null
        }
    }

    private fun textoJson(texto: String): String
    {
        val salida = StringBuilder("\"")
        for (c in texto)
        {
            when
            {
                c == '"' -> salida.append("\\\"")
                c == '\\' -> salida.append("\\\\")
                c == '\n' -> salida.append("\\n")
                c == '\r' -> salida.append("\\r")
                c == '\t' -> salida.append("\\t")
                c == '\b' -> salida.append("\\b")
                c == '\u000C' -> salida.append("\\f")
                c < ' ' -> salida.append("\\u%04x".format(c.code))
                else -> salida.append(c)
            }
        }
        return salida.append('"').toString()
    }
}

class TemporizadorEfimero(private val almacen: Almacen)
{
    private fun clave(convId: String) = "vixxer_efimero_$convId"

    fun leer(convId: String): Int = almacen.leer(clave(convId))?.toIntOrNull() ?: 0

    fun guardar(convId: String, segundos: Int)
    {
        if (segundos > 0)
        {
            almacen.escribir(clave(convId), segundos.toString())
        }
        else
        {
            almacen.borrar(clave(convId))
        }
    }
}
