package dev.vixxer.mensajero.ble

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.ContextCompat
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.DrenadorOutbox
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ConexionSocket
import dev.vixxer.mensajero.nucleo.Cripto
import dev.vixxer.mensajero.nucleo.IdentidadRotativa
import dev.vixxer.mensajero.nucleo.Outbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray

data class PeerCercano(
    val id: String,
    val rssi: Int,
    val visto: Long,
    val amigoId: String? = null,
    val nombre: String? = null,
    val caps: Int = 0,
)

data class ResultadoActivar(val ok: Boolean, val razon: String? = null, val abrirAjustes: Boolean = false)

object GestorCercania
{
    private const val CLAVE = "vixxer_modo_cercania"

    private const val MILIS_ROTACION = 15000L
    private const val TOPE_TOKENS = 128

    private var radio: RadioBle? = null
    private var mensajeria: MensajeriaBle? = null
    private var detenerEscaneo: (() -> Unit)? = null
    private val vistosCercanos = LinkedHashMap<String, PeerCercano>()
    private var hiloRotacion: Thread? = null

    @Volatile
    private var secretosAmigos: Map<String, ByteArray> = emptyMap()

    @Volatile
    private var nombresAmigos: Map<String, String> = emptyMap()

    private val tokensVistos = HashMap<String, String?>()

    @Volatile
    private var ventanaTokens = -1L

    @Volatile
    private var ultimoTokenAnunciado: String? = null

    private val drenados = HashMap<String, Long>()
    private val alcanceMesh = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var corriendo by mutableStateOf(false)
        private set

    private val _peers: SnapshotStateList<PeerCercano> = mutableStateListOf()
    val peers: List<PeerCercano> get() = _peers

    fun mensajeria(app: AplicacionVixxer): MensajeriaBle = prepararMensajeria(app)

    fun cercaniaSoportada(contexto: Context): Boolean
    {
        val paquetes = contexto.packageManager
        return paquetes.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    fun modoGuardado(app: AplicacionVixxer): Boolean =
        app.estado.leer(CLAVE) == "1"

    private fun guardarModo(app: AplicacionVixxer, valor: Boolean)
    {
        if (valor)
        {
            app.estado.escribir(CLAVE, "1")
        }
        else
        {
            app.estado.borrar(CLAVE)
        }
    }

    fun permisos(): Array<String>
    {
        return when
        {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.NEARBY_WIFI_DEVICES,
            )
            Build.VERSION.SDK_INT >= 31 -> arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
            )
            else -> arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun permisosConcedidos(contexto: Context): Boolean =
        permisos().all {
            ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED
        }

    fun iniciar(app: AplicacionVixxer, contexto: Context): ResultadoActivar
    {
        if (corriendo)
        {
            return ResultadoActivar(true)
        }
        if (!cercaniaSoportada(contexto))
        {
            return ResultadoActivar(false, "Este teléfono no tiene Bluetooth de baja energía")
        }
        if (!permisosConcedidos(contexto))
        {
            return ResultadoActivar(
                false,
                "Falta el permiso de Dispositivos cercanos o Ubicación. Actívalo en los ajustes de la app.",
                abrirAjustes = true,
            )
        }
        val motor = prepararRadio(contexto)
        val mensajero = prepararMensajeria(app)
        val estadoAnuncio = motor.anunciar()
        if (estadoAnuncio != "ok")
        {
            return ResultadoActivar(false, razonAnuncio(estadoAnuncio))
        }
        mensajero.iniciarPuente()
        detenerEscaneo = motor.escanear(
            soloVixxer = true,
            alEncontrar = { cercano ->
                mensajero.registrarPeer(cercano.id)
                val amigoId = synchronized(vistosCercanos)
                {
                    val previo = vistosCercanos[cercano.id]
                    val resuelto = cercano.token?.let { resolverAmigo(it) } ?: previo?.amigoId
                    vistosCercanos[cercano.id] = PeerCercano(
                        cercano.id,
                        cercano.rssi,
                        System.currentTimeMillis(),
                        resuelto,
                        resuelto?.let { nombresAmigos[it] },
                        if (cercano.caps != 0) cercano.caps else previo?.caps ?: 0,
                    )
                    resuelto
                }
                refrescarPeers()
                if (amigoId != null)
                {
                    drenarAlAvistar(app, amigoId)
                }
            },
            onError = {},
        )
        corriendo = true
        arrancarRotacion(app)
        return ResultadoActivar(true)
    }

    fun detener(app: AplicacionVixxer)
    {
        corriendo = false
        hiloRotacion?.interrupt()
        hiloRotacion = null
        detenerEscaneo?.invoke()
        detenerEscaneo = null
        radio?.detenerAnuncio()
        radio?.detenerEscaneo()
        mensajeria?.detenerPuente()
        mensajeria?.olvidarPeers()
        synchronized(vistosCercanos)
        {
            vistosCercanos.clear()
        }
        _peers.clear()
        ultimoTokenAnunciado = null
        synchronized(drenados) { drenados.clear() }
        limpiarTokens()
    }

    fun activar(app: AplicacionVixxer, contexto: Context, valor: Boolean): ResultadoActivar
    {
        guardarModo(app, valor)
        if (valor)
        {
            val resultado = iniciar(app, contexto)
            if (resultado.ok)
            {
                runCatching { ServicioCercania.arrancar(contexto) }
            }
            return resultado
        }
        detener(app)
        runCatching { ServicioCercania.parar(contexto) }
        return ResultadoActivar(false)
    }

    fun arrancarSiActivo(app: AplicacionVixxer, contexto: Context)
    {
        if (modoGuardado(app) && permisosConcedidos(contexto))
        {
            runCatching { ServicioCercania.arrancar(contexto) }
        }
    }

    private fun arrancarRotacion(app: AplicacionVixxer)
    {
        if (hiloRotacion?.isAlive == true)
        {
            return
        }
        val hilo = Thread {
            cargarSecretos(app)
            var indice = 0
            while (corriendo)
            {
                anunciarSiguienteToken(app, indice)
                indice += 1
                try
                {
                    Thread.sleep(MILIS_ROTACION)
                }
                catch (_: InterruptedException)
                {
                    return@Thread
                }
            }
        }
        hilo.isDaemon = true
        hiloRotacion = hilo
        hilo.start()
    }

    private fun anunciarSiguienteToken(app: AplicacionVixxer, indice: Int)
    {
        val secretos = secretosAmigos.values.toList()
        if (secretos.isEmpty())
        {
            return
        }
        val miId = app.boveda.leer(ClavesSeguras.MI_ID) ?: return
        val secreto = secretos[indice % secretos.size]
        val token = IdentidadRotativa.tokenActual(miId, secreto, System.currentTimeMillis() / 1000)
        if (token == ultimoTokenAnunciado)
        {
            return
        }
        ultimoTokenAnunciado = token
        radio?.anunciar(Cripto.deBase64(token))
    }

    private fun cargarSecretos(app: AplicacionVixxer)
    {
        val privada = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: return
        val lista = runCatching { app.api.amigos() as? JSONArray }.getOrNull() ?: return
        val secretos = HashMap<String, ByteArray>()
        val nombres = HashMap<String, String>()
        for (i in 0 until lista.length())
        {
            val amigo = lista.optJSONObject(i) ?: continue
            val id = amigo.optString("id")
            val publica = amigo.optString("llave_publica")
            if (id.isEmpty() || publica.isEmpty())
            {
                continue
            }
            runCatching {
                secretos[id] = Cripto.secretoCompartido(Cripto.deBase64(publica), Cripto.deBase64(privada))
                nombres[id] = amigo.optString("usuario")
                app.llaves.sembrar(id, publica)
            }
        }
        secretosAmigos = secretos
        nombresAmigos = nombres
        limpiarTokens()
    }

    fun nombreDe(amigoId: String): String? = nombresAmigos[amigoId]?.takeIf { it.isNotEmpty() }

    private fun drenarAlAvistar(app: AplicacionVixxer, amigoId: String)
    {
        val socket = ConexionSocket.obtener()
        if (socket != null && socket.connected())
        {
            return
        }
        val ahora = System.currentTimeMillis()
        synchronized(drenados) {
            val ultimo = drenados[amigoId] ?: 0L
            if (ahora - ultimo < 30_000L)
            {
                return
            }
            drenados[amigoId] = ahora
        }
        val cuentaId = app.boveda.leer(ClavesSeguras.MI_ID) ?: return
        alcanceMesh.launch {
            runCatching { DrenadorOutbox.drenar(app, cuentaId, Outbox.Tipo.DIRECTO, amigoId, forzar = true) }
        }
    }

    fun macsDeAmigo(amigoId: String): List<String>
    {
        val limite = System.currentTimeMillis() - 120_000L
        return _peers.filter { it.amigoId == amigoId && it.visto >= limite }.map { it.id }
    }

    fun amigoVisible(amigoId: String): Boolean = macsDeAmigo(amigoId).isNotEmpty()

    fun amigoSoportaWifi(amigoId: String): Boolean
    {
        val limite = System.currentTimeMillis() - 120_000L
        return _peers.any { it.amigoId == amigoId && it.visto >= limite && (it.caps and 2) != 0 }
    }

    @Synchronized
    private fun resolverAmigo(token: String): String?
    {
        val ahora = System.currentTimeMillis() / 1000
        val ventana = IdentidadRotativa.ventanaActual(ahora)
        if (ventana != ventanaTokens || tokensVistos.size > TOPE_TOKENS)
        {
            tokensVistos.clear()
            ventanaTokens = ventana
        }
        return tokensVistos.getOrPut(token) { IdentidadRotativa.coincidir(token, secretosAmigos, ahora) }
    }

    @Synchronized
    private fun limpiarTokens()
    {
        tokensVistos.clear()
        ventanaTokens = -1L
    }

    private fun prepararRadio(contexto: Context): RadioBle
    {
        val actual = radio
        if (actual != null)
        {
            return actual
        }
        val nuevo = RadioBle(contexto.applicationContext)
        radio = nuevo
        return nuevo
    }

    private fun prepararMensajeria(app: AplicacionVixxer): MensajeriaBle
    {
        val actual = mensajeria
        if (actual != null)
        {
            return actual
        }
        val motor = prepararRadio(app.applicationContext)
        val nuevo = MensajeriaBle(app, motor)
        mensajeria = nuevo
        return nuevo
    }

    private fun refrescarPeers()
    {
        val instantanea = synchronized(vistosCercanos)
        {
            vistosCercanos.values.toList()
        }
        _peers.clear()
        _peers.addAll(instantanea)
    }

    private fun razonAnuncio(codigo: String): String = when (codigo)
    {
        "bt-apagado" -> "Enciende el Bluetooth e inténtalo de nuevo"
        "sin-bluetooth" -> "Este teléfono no tiene Bluetooth disponible"
        "sin-anunciante" -> "Enciende el Bluetooth (o tu teléfono no soporta anunciar BLE)"
        else -> "Enciende el Bluetooth e inténtalo de nuevo"
    }
}
