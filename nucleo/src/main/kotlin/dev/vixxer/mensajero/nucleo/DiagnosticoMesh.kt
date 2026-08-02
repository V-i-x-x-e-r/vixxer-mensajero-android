package dev.vixxer.mensajero.nucleo

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

class DiagnosticoMesh(
    private val almacen: Almacen,
    private val reloj: () -> Long = System::currentTimeMillis,
    private val maximo: Int = 200,
)
{
    enum class Transporte(val valor: String)
    {
        SERVIDOR("servidor"),
        BLE("ble"),
        WIFI("wifi"),
        LORA("lora"),
        SIN_RUTA("sin_ruta");

        companion object
        {
            fun de(valor: String): Transporte? = entries.firstOrNull { it.valor == valor }
        }
    }

    enum class Etapa(val valor: String)
    {
        INTENTO("intento"),
        ENVIADO("enviado"),
        RECIBIDO("recibido"),
        REENVIADO("reenviado"),
        ENCOLADO("encolado"),
        PUENTE("puente"),
        DESCARTADO("descartado"),
        ERROR("error");

        companion object
        {
            fun de(valor: String): Etapa? = entries.firstOrNull { it.valor == valor }
        }
    }

    enum class CodigoError(val valor: String)
    {
        SIN_RUTA("sin_ruta"),
        SIN_ACUSE("sin_acuse"),
        SIN_VECINO("sin_vecino"),
        PREPARACION("preparacion"),
        RADIO("radio"),
        RELAY("relay"),
        WIFI("wifi"),
        ESCANEO("escaneo");

        companion object
        {
            fun de(valor: String): CodigoError? = entries.firstOrNull { it.valor == valor }
        }
    }

    data class Evento(
        val instanteMs: Long,
        val mensaje: String?,
        val etapa: Etapa,
        val transporte: Transporte,
        val enlace: String? = null,
        val saltos: Int? = null,
        val duracionMs: Long? = null,
        val intento: Int? = null,
        val reintentos: Int = 0,
        val cola: Int? = null,
        val error: CodigoError? = null,
    )

    data class Instantanea(
        val eventos: List<Evento>,
        val ultimaDuracionMs: Long?,
        val ultimoError: CodigoError?,
        val reintentos: Int,
    )

    data class ContextoExportacion(
        val versionApp: String,
        val modelo: String,
        val sdk: Int,
    )

    private val clave = "vixxer_diagnostico_mesh_v1"

    init
    {
        require(maximo > 0)
    }

    @Synchronized
    fun registrar(
        mensajeId: String?,
        etapa: Etapa,
        transporte: Transporte,
        enlace: String? = null,
        saltos: Int? = null,
        duracionMs: Long? = null,
        intento: Int? = null,
        reintentos: Int = 0,
        cola: Int? = null,
        error: CodigoError? = null,
    )
    {
        val eventos = leerInterno().toMutableList()
        eventos.add(
            Evento(
                instanteMs = reloj(),
                mensaje = mensajeId?.takeIf { it.isNotBlank() }?.let { referenciaDe(it) },
                etapa = etapa,
                transporte = transporte,
                enlace = normalizarEnlace(enlace),
                saltos = saltos?.coerceAtLeast(0),
                duracionMs = duracionMs?.coerceAtLeast(0),
                intento = intento?.coerceAtLeast(1),
                reintentos = reintentos.coerceAtLeast(0),
                cola = cola?.coerceAtLeast(0),
                error = error,
            ),
        )
        escribir(eventos.takeLast(maximo))
    }

    @Synchronized
    fun instantanea(): Instantanea
    {
        val eventos = leerInterno()
        return Instantanea(
            eventos = eventos.asReversed(),
            ultimaDuracionMs = eventos.lastOrNull { it.duracionMs != null }?.duracionMs,
            ultimoError = eventos.lastOrNull { it.error != null }?.error,
            reintentos = eventos.sumOf { it.reintentos },
        )
    }

    @Synchronized
    fun exportar(
        contexto: ContextoExportacion,
        colaOutbox: Int,
        colaRelay: Int,
    ): String
    {
        val eventos = JSONArray()
        for (evento in leerInterno())
        {
            eventos.put(aJson(evento))
        }
        return JSONObject()
            .put("formato", 1)
            .put("generado_en_ms", reloj())
            .put("app", contexto.versionApp.take(32))
            .put("modelo", contexto.modelo.take(80))
            .put("sdk", contexto.sdk)
            .put("cola_outbox", colaOutbox.coerceAtLeast(0))
            .put("cola_relay", colaRelay.coerceAtLeast(0))
            .put("eventos", eventos)
            .toString(2)
    }

    @Synchronized
    fun limpiar()
    {
        almacen.borrar(clave)
    }

    private fun referenciaDe(id: String): String
    {
        val bytes = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        return bytes.take(6).joinToString("") {
            (it.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    private fun normalizarEnlace(enlace: String?): String?
    {
        val valor = enlace?.lowercase()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when (valor)
        {
            "ble", "gatt", "l2cap", "wifi_direct", "meshtastic" -> valor
            else -> "otro"
        }
    }

    private fun aJson(evento: Evento): JSONObject
    {
        val json = JSONObject()
            .put("instante_ms", evento.instanteMs)
            .put("etapa", evento.etapa.valor)
            .put("transporte", evento.transporte.valor)
            .put("reintentos", evento.reintentos)
        evento.mensaje?.let { json.put("mensaje", it) }
        evento.enlace?.let { json.put("enlace", it) }
        evento.saltos?.let { json.put("saltos", it) }
        evento.duracionMs?.let { json.put("duracion_ms", it) }
        evento.intento?.let { json.put("intento", it) }
        evento.cola?.let { json.put("cola", it) }
        evento.error?.let { json.put("error", it.valor) }
        return json
    }

    private fun leerInterno(): List<Evento>
    {
        val crudo = almacen.leer(clave) ?: return emptyList()
        return try
        {
            val arreglo = JSONArray(crudo)
            (0 until arreglo.length()).mapNotNull { indice ->
                arreglo.optJSONObject(indice)?.let { deJson(it) }
            }
        }
        catch (_: Exception)
        {
            emptyList()
        }
    }

    private fun deJson(json: JSONObject): Evento?
    {
        val etapa = Etapa.de(json.optString("etapa")) ?: return null
        val transporte = Transporte.de(json.optString("transporte")) ?: return null
        return Evento(
            instanteMs = json.optLong("instante_ms"),
            mensaje = json.optString("mensaje").takeIf { it.isNotBlank() },
            etapa = etapa,
            transporte = transporte,
            enlace = normalizarEnlace(json.optString("enlace")),
            saltos = json.optInt("saltos").takeIf { json.has("saltos") },
            duracionMs = json.optLong("duracion_ms").takeIf { json.has("duracion_ms") },
            intento = json.optInt("intento").takeIf { json.has("intento") },
            reintentos = json.optInt("reintentos"),
            cola = json.optInt("cola").takeIf { json.has("cola") },
            error = CodigoError.de(json.optString("error")),
        )
    }

    private fun escribir(eventos: List<Evento>)
    {
        if (eventos.isEmpty())
        {
            almacen.borrar(clave)
            return
        }
        val arreglo = JSONArray()
        for (evento in eventos)
        {
            arreglo.put(aJson(evento))
        }
        almacen.escribir(clave, arreglo.toString())
    }
}
