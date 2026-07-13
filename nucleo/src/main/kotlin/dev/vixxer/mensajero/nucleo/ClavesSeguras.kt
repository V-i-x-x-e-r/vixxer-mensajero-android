package dev.vixxer.mensajero.nucleo

import org.json.JSONArray

object ClavesSeguras
{
    const val TOKEN = "vixxer_token"
    const val MI_ID = "vixxer_mi_id"
    const val CLAVE_PRIVADA = "vixxer_clave_privada"
    const val CLAVE_PUBLICA = "vixxer_clave_publica"
    const val CLAVE_FIRMA_PRIVADA = "vixxer_clave_firma_privada"
    const val CLAVE_FIRMA_PUBLICA = "vixxer_clave_firma_publica"
    const val CODIGO_RECUP = "vixxer_codigo_recup"
}

class LlavesPasadas(private val almacen: Almacen)
{
    private val clave = "vixxer_llaves_pasadas"

    fun cargar(): List<String>
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

    fun recordar(privada: String?)
    {
        if (privada.isNullOrEmpty())
        {
            return
        }
        val memoria = cargar()
        if (privada in memoria)
        {
            return
        }
        val nueva = (listOf(privada) + memoria).take(5)
        almacen.escribir(clave, JSONArray(nueva).toString())
    }
}
