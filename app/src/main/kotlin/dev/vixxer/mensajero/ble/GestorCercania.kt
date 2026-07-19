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

data class PeerCercano(val id: String, val rssi: Int, val visto: Long)

data class ResultadoActivar(val ok: Boolean, val razon: String? = null, val abrirAjustes: Boolean = false)

object GestorCercania
{
    private const val CLAVE = "vixxer_modo_cercania"

    private var radio: RadioBle? = null
    private var mensajeria: MensajeriaBle? = null
    private var detenerEscaneo: (() -> Unit)? = null
    private val vistosCercanos = LinkedHashMap<String, PeerCercano>()

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
        return if (Build.VERSION.SDK_INT >= 31)
        {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        }
        else
        {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
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
                vistosCercanos[cercano.id] = PeerCercano(cercano.id, cercano.rssi, System.currentTimeMillis())
                refrescarPeers()
            },
            onError = {},
        )
        corriendo = true
        return ResultadoActivar(true)
    }

    fun detener(app: AplicacionVixxer)
    {
        detenerEscaneo?.invoke()
        detenerEscaneo = null
        radio?.detenerAnuncio()
        radio?.detenerEscaneo()
        mensajeria?.detenerPuente()
        mensajeria?.olvidarPeers()
        vistosCercanos.clear()
        _peers.clear()
        corriendo = false
    }

    fun activar(app: AplicacionVixxer, contexto: Context, valor: Boolean): ResultadoActivar
    {
        guardarModo(app, valor)
        if (valor)
        {
            return iniciar(app, contexto)
        }
        detener(app)
        return ResultadoActivar(false)
    }

    fun arrancarSiActivo(app: AplicacionVixxer, contexto: Context)
    {
        if (modoGuardado(app) && permisosConcedidos(contexto))
        {
            iniciar(app, contexto)
        }
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
        _peers.clear()
        _peers.addAll(vistosCercanos.values)
    }

    private fun razonAnuncio(codigo: String): String = when (codigo)
    {
        "bt-apagado" -> "Enciende el Bluetooth e inténtalo de nuevo"
        "sin-bluetooth" -> "Este teléfono no tiene Bluetooth disponible"
        "sin-anunciante" -> "Enciende el Bluetooth (o tu teléfono no soporta anunciar BLE)"
        else -> "Enciende el Bluetooth e inténtalo de nuevo"
    }
}
