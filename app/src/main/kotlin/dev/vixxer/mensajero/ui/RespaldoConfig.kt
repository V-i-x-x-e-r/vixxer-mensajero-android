package dev.vixxer.mensajero.ui

import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.Almacen
import java.util.Calendar
import org.json.JSONObject

data class RespaldoConfig(
    val frecuencia: String = "nunca",
    val hora: Int = 3,
    val destino: String = "nube",
    val ultimo: Long? = null,
)
{
    fun debeRespaldar(ahora: Long = System.currentTimeMillis()): Boolean
    {
        if (frecuencia == "nunca")
        {
            return false
        }
        val previo = ultimo ?: return true
        val dias = if (frecuencia == "diaria") 1 else 7
        return ahora - previo > dias * 86_400_000L
    }

    companion object
    {
        const val CLAVE = "vixxer_respaldo_config"
        val FRECUENCIAS = listOf("nunca", "diaria", "semanal")

        fun etiquetaFrecuencia(frecuencia: String): String = when (frecuencia)
        {
            "diaria" -> "Diaria"
            "semanal" -> "Semanal"
            else -> "Nunca"
        }

        fun leer(estado: Almacen): RespaldoConfig
        {
            val crudo = estado.leer(CLAVE) ?: return RespaldoConfig()
            return runCatching {
                val objeto = JSONObject(crudo)
                RespaldoConfig(
                    frecuencia = objeto.optString("frecuencia", "nunca"),
                    hora = objeto.optInt("hora", 3).coerceIn(0, 23),
                    destino = objeto.optString("destino", "nube"),
                    ultimo = if (objeto.has("ultimo") && !objeto.isNull("ultimo")) objeto.optLong("ultimo") else null,
                )
            }.getOrDefault(RespaldoConfig())
        }

        fun guardar(estado: Almacen, config: RespaldoConfig)
        {
            val objeto = JSONObject()
                .put("frecuencia", config.frecuencia)
                .put("hora", config.hora)
                .put("destino", config.destino)
            if (config.ultimo != null)
            {
                objeto.put("ultimo", config.ultimo)
            }
            estado.escribir(CLAVE, objeto.toString())
        }
    }
}

fun respaldoAutomatico(app: AplicacionVixxer)
{
    val config = RespaldoConfig.leer(app.estado)
    if (config.destino != "nube" || !config.debeRespaldar())
    {
        return
    }
    if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < config.hora)
    {
        return
    }
    runCatching {
        val listo = app.identidad.prepararRespaldoActual() ?: return
        app.api.subirRespaldo(listo.respaldo)
        RespaldoConfig.guardar(app.estado, config.copy(ultimo = System.currentTimeMillis()))
    }
}
