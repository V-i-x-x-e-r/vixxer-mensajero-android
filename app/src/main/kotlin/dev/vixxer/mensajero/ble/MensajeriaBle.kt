package dev.vixxer.mensajero.ble

import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ConexionSocket
import dev.vixxer.mensajero.nucleo.Cripto
import dev.vixxer.mensajero.nucleo.Firma
import dev.vixxer.mensajero.nucleo.MeshCercania
import dev.vixxer.mensajero.nucleo.Sobre
import dev.vixxer.mensajero.nucleo.Vistos
import io.socket.client.Socket
import io.socket.emitter.Emitter
import java.time.Instant
import java.util.concurrent.CopyOnWriteArraySet
import org.json.JSONArray
import org.json.JSONObject

data class EstadisticasMesh(
    val enviados: Int = 0,
    val recibidos: Int = 0,
    val reenviados: Int = 0,
    val puente: Int = 0,
    val ultimaRuta: String? = null,
)

data class ResultadoEnvioCercania(val id: String, val entregados: Int)

class MensajeriaBle(
    private val app: AplicacionVixxer,
    private val radio: RadioBle,
)
{
    private val vistos = Vistos()
    private val peers = CopyOnWriteArraySet<String>()
    private val oyentes = CopyOnWriteArraySet<(JSONObject) -> Unit>()

    @Volatile
    private var enviados = 0

    @Volatile
    private var reenviados = 0

    @Volatile
    private var puente = 0

    @Volatile
    private var recibidos = 0

    @Volatile
    private var ultimaRuta: String? = null

    @Volatile
    private var puenteActivo = false

    private val CLAVE_COLA = "vixxer_cola_relay"
    private val alConectar = Emitter.Listener { drenarCola() }

    fun estadisticas(): EstadisticasMesh =
        EstadisticasMesh(enviados, recibidos, reenviados, puente, ultimaRuta)

    fun registrarPeer(id: String)
    {
        peers.add(id)
    }

    fun olvidarPeers()
    {
        peers.clear()
    }

    fun peersConocidos(): Int = peers.size

    fun alEntrante(cb: (JSONObject) -> Unit): () -> Unit
    {
        oyentes.add(cb)
        return { oyentes.remove(cb) }
    }

    private fun difundir(sobre: MeshCercania.Sobre, excepto: String?): Int
    {
        val texto = MeshCercania.aJson(sobre)
        var entregados = 0
        for (id in peers)
        {
            if (id == excepto)
            {
                continue
            }
            if (radio.conectarYEnviar(id, texto))
            {
                entregados += 1
                ultimaRuta = id
            }
        }
        return entregados
    }

    fun enviarPorCercania(destinatarioId: String, contenidoCifrado: String, nonce: String): ResultadoEnvioCercania
    {
        val miId = app.boveda.leer(ClavesSeguras.MI_ID) ?: ""
        val base = MeshCercania.crearSobre(miId, destinatarioId, contenidoCifrado, nonce)
        val canonico = Firma.mensajeCanonico(miId, destinatarioId, contenidoCifrado, nonce, base.id)
        val firma = app.firma.firmar(canonico)
        val sobre = base.copy(firma = firma)
        vistos.marcar(sobre.id)
        val entregados = difundir(sobre, null)
        if (entregados > 0)
        {
            enviados += 1
        }
        return ResultadoEnvioCercania(sobre.id, entregados)
    }

    fun iniciarPuente()
    {
        if (puenteActivo)
        {
            return
        }
        puenteActivo = true
        val miId = app.boveda.leer(ClavesSeguras.MI_ID) ?: ""
        ConexionSocket.obtener()?.let { socket ->
            socket.off(Socket.EVENT_CONNECT, alConectar)
            socket.on(Socket.EVENT_CONNECT, alConectar)
        }
        drenarCola()
        radio.alRecibir { texto ->
            if (!puenteActivo)
            {
                return@alRecibir
            }
            val sobre = MeshCercania.deJson(texto) ?: return@alRecibir
            val decision = MeshCercania.procesar(sobre, miId, vistos)
            when (decision.accion)
            {
                MeshCercania.Accion.ENTREGAR ->
                {
                    recibidos += 1
                    entregarLocal(sobre)
                }
                MeshCercania.Accion.REENVIAR ->
                {
                    val reenviar = decision.sobre ?: return@alRecibir
                    val socket = ConexionSocket.obtener()
                    if (socket != null && socket.connected())
                    {
                        puente += 1
                        subirComoPuente(reenviar)
                        drenarCola()
                    }
                    else
                    {
                        encolar(reenviar)
                    }
                    reenviados += 1
                    difundir(reenviar, null)
                }
                MeshCercania.Accion.DESCARTAR ->
                {
                }
            }
        }
    }

    fun detenerPuente()
    {
        puenteActivo = false
        ConexionSocket.obtener()?.off(Socket.EVENT_CONNECT, alConectar)
    }

    private fun subirComoPuente(sobre: MeshCercania.Sobre): Boolean =
        runCatching {
            app.api.relayMensaje(
                Sobre(
                    remitenteId = sobre.remitenteId,
                    destinatarioId = sobre.destinatarioId,
                    contenidoCifrado = sobre.contenidoCifrado,
                    nonce = sobre.nonce,
                    id = sobre.id,
                    firma = sobre.firma,
                ),
            )
        }.isSuccess

    private fun encolar(sobre: MeshCercania.Sobre)
    {
        try
        {
            val cola = JSONArray(app.estado.leer(CLAVE_COLA) ?: "[]")
            for (i in 0 until cola.length())
            {
                if (MeshCercania.deJson(cola.optString(i))?.id == sobre.id)
                {
                    return
                }
            }
            cola.put(MeshCercania.aJson(sobre))
            while (cola.length() > 200)
            {
                cola.remove(0)
            }
            app.estado.escribir(CLAVE_COLA, cola.toString())
        }
        catch (_: Exception)
        {
        }
    }

    fun drenarCola()
    {
        val socket = ConexionSocket.obtener()
        if (socket == null || !socket.connected())
        {
            return
        }
        val cola = runCatching { JSONArray(app.estado.leer(CLAVE_COLA) ?: "[]") }.getOrNull() ?: return
        if (cola.length() == 0)
        {
            return
        }
        val pendientes = JSONArray()
        for (i in 0 until cola.length())
        {
            val crudo = cola.optString(i)
            val sobre = MeshCercania.deJson(crudo)
            if (sobre == null)
            {
                continue
            }
            if (subirComoPuente(sobre))
            {
                puente += 1
            }
            else
            {
                pendientes.put(crudo)
            }
        }
        app.estado.escribir(CLAVE_COLA, pendientes.toString())
    }

    private fun entregarLocal(sobre: MeshCercania.Sobre)
    {
        val privada = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA)
        val publica = runCatching { app.llaves.llavePublicaDe(sobre.remitenteId) }.getOrNull()
        val claro = if (privada != null && publica != null)
        {
            Cripto.descifrarTexto(sobre.contenidoCifrado, sobre.nonce, publica, privada)
        }
        else
        {
            null
        }
        val mensaje = JSONObject()
            .put("id", sobre.id)
            .put("cliente_id", sobre.id)
            .put("remitente_id", sobre.remitenteId)
            .put("contenido_cifrado", sobre.contenidoCifrado)
            .put("nonce", sobre.nonce)
            .put("texto", claro ?: "No se pudo descifrar (BLE)")
            .put("enviado_en", Instant.now().toString())
            .put("porBle", true)
        guardarEnCache(sobre.remitenteId, mensaje)
        for (cb in oyentes)
        {
            runCatching { cb(mensaje) }
        }
    }

    private fun guardarEnCache(remitenteId: String, mensaje: JSONObject)
    {
        try
        {
            val cache = app.cacheChats.leerChat(remitenteId) ?: JSONArray()
            val id = mensaje.optString("id")
            var existe = false
            for (i in 0 until cache.length())
            {
                if (cache.optJSONObject(i)?.optString("id") == id)
                {
                    existe = true
                    break
                }
            }
            if (!existe)
            {
                cache.put(mensaje)
                app.cacheChats.guardarChat(remitenteId, cache)
            }
        }
        catch (_: Exception)
        {
        }
    }
}
