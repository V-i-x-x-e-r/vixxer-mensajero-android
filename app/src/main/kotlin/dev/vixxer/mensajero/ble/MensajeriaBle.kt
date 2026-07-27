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
import java.util.concurrent.ConcurrentHashMap
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
    private val peers = ConcurrentHashMap<String, Long>()
    private val oyentes = CopyOnWriteArraySet<(JSONObject) -> Unit>()
    private val vidaPeerMs = 120_000L

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

    @Volatile
    var chatVisible: String? = null

    private val CLAVE_COLA = "vixxer_cola_relay"
    private val alConectar = Emitter.Listener { drenarCola() }

    fun estadisticas(): EstadisticasMesh =
        EstadisticasMesh(enviados, recibidos, reenviados, puente, ultimaRuta)

    fun registrarPeer(id: String)
    {
        peers[id] = System.currentTimeMillis()
    }

    fun olvidarPeers()
    {
        peers.clear()
    }

    fun peersConocidos(): Int = peersVigentes().size

    private fun peersVigentes(): List<String>
    {
        val limite = System.currentTimeMillis() - vidaPeerMs
        peers.entries.removeAll { it.value < limite }
        return peers.keys.toList()
    }

    fun alEntrante(cb: (JSONObject) -> Unit): () -> Unit
    {
        oyentes.add(cb)
        return { oyentes.remove(cb) }
    }

    private fun difundir(sobre: MeshCercania.Sobre, excepto: String?): Int
    {
        val texto = MeshCercania.aJson(sobre)
        var entregados = 0
        for (id in peersVigentes())
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

    fun enviarPorCercania(
        destinatarioId: String,
        contenidoCifrado: String,
        nonce: String,
        clienteId: String? = null,
        tipo: String = MeshCercania.TIPO_DIRECTO,
    ): ResultadoEnvioCercania
    {
        val sobre = sellarSobre(
            destinatarioId,
            contenidoCifrado,
            nonce,
            ttl = MeshCercania.TTL_MAXIMO,
            clienteId = clienteId,
            tipo = tipo,
        )
        val entregados = difundir(sobre, null)
        if (entregados > 0)
        {
            enviados += 1
        }
        return ResultadoEnvioCercania(sobre.id, entregados)
    }

    fun enviarDirectoA(
        macs: List<String>,
        destinatarioId: String,
        contenidoCifrado: String,
        nonce: String,
        clienteId: String? = null,
    ): ResultadoEnvioCercania
    {
        val sobre = sellarSobre(destinatarioId, contenidoCifrado, nonce, ttl = 1, clienteId = clienteId)
        val texto = MeshCercania.aJson(sobre)
        var entregados = 0
        for (mac in macs)
        {
            if (radio.conectarYEnviar(mac, texto))
            {
                entregados += 1
                ultimaRuta = mac
                break
            }
        }
        if (entregados > 0)
        {
            enviados += 1
        }
        return ResultadoEnvioCercania(sobre.id, entregados)
    }

    private fun sellarSobre(
        destinatarioId: String,
        contenidoCifrado: String,
        nonce: String,
        ttl: Int,
        clienteId: String?,
        tipo: String = MeshCercania.TIPO_DIRECTO,
    ): MeshCercania.Sobre
    {
        val miId = app.boveda.leer(ClavesSeguras.MI_ID) ?: ""
        val base = MeshCercania.crearSobre(
            miId,
            destinatarioId,
            contenidoCifrado,
            nonce,
            ttl = ttl,
            clienteId = clienteId,
            tipo = tipo,
        )
        val canonico = Firma.mensajeCanonico(miId, destinatarioId, contenidoCifrado, nonce, idParaServidor(base))
        val firma = app.firma.firmar(canonico)
        val sobre = base.copy(firma = firma)
        vistos.marcar(sobre.id)
        return sobre
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
                    if (reenviar.tipo == MeshCercania.TIPO_DIRECTO)
                    {
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

    private fun idParaServidor(sobre: MeshCercania.Sobre): String =
        sobre.clienteId?.takeIf { it.isNotBlank() } ?: sobre.id

    private fun subirComoPuente(sobre: MeshCercania.Sobre): Boolean =
        runCatching {
            app.api.relayMensaje(
                Sobre(
                    remitenteId = sobre.remitenteId,
                    destinatarioId = sobre.destinatarioId,
                    contenidoCifrado = sobre.contenidoCifrado,
                    nonce = sobre.nonce,
                    id = idParaServidor(sobre),
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
        if (claro != null && esControlWifi(claro))
        {
            recibirFotoWifi(sobre, claro, publica, privada)
            return
        }
        val entregado = claro?.let { desenvolver(it) }
        publicarMensaje(sobre, entregado?.texto, entregado?.grupoId)
    }

    private fun publicarMensaje(sobre: MeshCercania.Sobre, texto: String?, grupoId: String?)
    {
        val idMensaje = sobre.clienteId ?: sobre.id
        val claveChat = grupoId?.let { "g-$it" } ?: sobre.remitenteId
        val mensaje = JSONObject()
            .put("id", idMensaje)
            .put("cliente_id", idMensaje)
            .put("remitente_id", sobre.remitenteId)
            .put("contenido_cifrado", sobre.contenidoCifrado)
            .put("nonce", sobre.nonce)
            .put("texto", texto ?: "No se pudo descifrar (BLE)")
            .put("enviado_en", Instant.now().toString())
            .put("estado", "cercania")
            .put("porBle", true)
        grupoId?.let {
            mensaje.put("grupo_id", it)
            mensaje.put("autor", GestorCercania.nombreDe(sobre.remitenteId) ?: JSONObject.NULL)
        }
        guardarEnCache(claveChat, mensaje)
        for (cb in oyentes)
        {
            runCatching { cb(mensaje) }
        }
        if (chatVisible != claveChat)
        {
            notificarEntrante(sobre.remitenteId, grupoId)
        }
    }

    private fun esControlWifi(claro: String): Boolean =
        claro.startsWith("{") &&
            runCatching { JSONObject(claro).optString("t") }.getOrNull() == "cercania-wifi"

    private fun recibirFotoWifi(
        sobre: MeshCercania.Sobre,
        claro: String,
        publicaRemitente: String?,
        privadaPropia: String?,
    )
    {
        val hilo = Thread {
            runCatching { recibirFotoWifiAhora(sobre, claro, publicaRemitente, privadaPropia) }
        }
        hilo.isDaemon = true
        hilo.start()
    }

    private fun recibirFotoWifiAhora(
        sobre: MeshCercania.Sobre,
        claro: String,
        publicaRemitente: String?,
        privadaPropia: String?,
    )
    {
        val control = runCatching { JSONObject(claro) }.getOrNull() ?: return
        val ssid = control.optString("ssid")
        val pass = control.optString("pass")
        val puerto = control.optInt("puerto")
        val nonce = control.optString("nonce")
        val plano = control.optString("plano")
        if (ssid.isBlank() || pass.isBlank() || puerto <= 0 || nonce.isBlank() || plano.isBlank())
        {
            return
        }
        if (publicaRemitente == null || privadaPropia == null)
        {
            return
        }
        val media = runCatching { JSONObject(plano) }.getOrNull() ?: return
        val path = media.optString("path")
        if (!path.startsWith("ble/"))
        {
            return
        }
        val caja = RadioWifi.recibir(app, ssid, pass, puerto, 90) ?: return
        val datos = runCatching {
            Cripto.descifrar(
                caja,
                Cripto.deBase64(nonce),
                Cripto.deBase64(publicaRemitente),
                Cripto.deBase64(privadaPropia),
            )
        }.getOrNull() ?: return
        dev.vixxer.mensajero.ui.CacheMedia.guardar(app, path, datos.size.toLong()) { datos.inputStream() }
        publicarMensaje(sobre, plano, null)
    }

    private fun notificarEntrante(remitenteId: String, grupoId: String? = null)
    {
        try
        {
            val destino = grupoId?.let { "grupo/$it" } ?: "chat/$remitenteId"
            val gestor = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            gestor.createNotificationChannel(
                android.app.NotificationChannel(
                    "mensajes-cercania",
                    "Mensajes por cercanía",
                    android.app.NotificationManager.IMPORTANCE_HIGH,
                ),
            )
            val abrir = android.app.PendingIntent.getActivity(
                app,
                destino.hashCode(),
                android.content.Intent(app, dev.vixxer.mensajero.ActividadPrincipal::class.java)
                    .putExtra("vixxer_destino", destino),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val nombre = GestorCercania.nombreDe(remitenteId) ?: "Un vixxer cercano"
            val notificacion = android.app.Notification.Builder(app, "mensajes-cercania")
                .setContentTitle(nombre)
                .setContentText("Te envió un mensaje por cercanía")
                .setSmallIcon(dev.vixxer.mensajero.R.mipmap.ic_lanzador)
                .setAutoCancel(true)
                .setContentIntent(abrir)
                .build()
            gestor.notify(remitenteId.hashCode(), notificacion)
        }
        catch (_: Exception)
        {
        }
    }

    private data class Desenvuelto(val texto: String, val grupoId: String? = null)

    private fun desenvolver(claro: String): Desenvuelto
    {
        if (!claro.startsWith("{"))
        {
            return Desenvuelto(claro)
        }
        val obj = runCatching { JSONObject(claro) }.getOrNull() ?: return Desenvuelto(claro)
        return when (obj.optString("t"))
        {
            "cercania-media" -> Desenvuelto(materializarMedia(obj) ?: claro)
            "cercania-grupo" ->
            {
                val grupoId = obj.optString("grupoId")
                val plano = obj.optString("plano")
                if (grupoId.isNotBlank() && plano.isNotBlank())
                {
                    Desenvuelto(plano, grupoId)
                }
                else
                {
                    Desenvuelto(claro)
                }
            }
            else -> Desenvuelto(claro)
        }
    }

    private fun materializarMedia(obj: JSONObject): String?
    {
        val plano = obj.optString("plano")
        val datos = runCatching { Cripto.deBase64(obj.optString("datos")) }.getOrNull()
        if (plano.isBlank() || datos == null || datos.isEmpty())
        {
            return null
        }
        val media = runCatching { JSONObject(plano) }.getOrNull() ?: return null
        val path = media.optString("path")
        if (path.startsWith("ble/"))
        {
            dev.vixxer.mensajero.ui.CacheMedia.guardar(app, path, datos.size.toLong()) { datos.inputStream() }
        }
        return plano
    }

    private fun guardarEnCache(claveChat: String, mensaje: JSONObject)
    {
        try
        {
            val cache = app.cacheChats.leerChat(claveChat) ?: JSONArray()
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
                app.cacheChats.guardarChat(claveChat, cache)
            }
        }
        catch (_: Exception)
        {
        }
    }
}
