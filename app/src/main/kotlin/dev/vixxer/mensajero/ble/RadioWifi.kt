package dev.vixxer.mensajero.ble

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object RadioWifi
{
    private const val IP_GRUPO = "192.168.49.1"
    private const val TOPE_BLOB = 17 * 1024 * 1024

    fun soportado(contexto: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 &&
            contexto.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI_DIRECT)

    class Sesion internal constructor(
        val ssid: String,
        val pass: String,
        val puerto: Int,
        private val servidor: ServerSocket,
        private val gestor: WifiP2pManager,
        private val canal: WifiP2pManager.Channel,
        private val caja: ByteArray,
    )
    {
        fun esperarEntrega(segundos: Long): Boolean
        {
            return try
            {
                servidor.soTimeout = (segundos * 1000).toInt()
                val socket = servidor.accept()
                socket.use {
                    it.soTimeout = 30000
                    val salida = DataOutputStream(it.outputStream.buffered())
                    salida.writeInt(caja.size)
                    salida.write(caja)
                    salida.flush()
                    it.inputStream.read() == 49
                }
            }
            catch (_: Exception)
            {
                false
            }
            finally
            {
                cerrar()
            }
        }

        fun cerrar()
        {
            runCatching { servidor.close() }
            runCatching { gestor.removeGroup(canal, null) }
        }
    }

    fun servir(contexto: Context, caja: ByteArray): Sesion?
    {
        if (!soportado(contexto))
        {
            return null
        }
        val gestor = contexto.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager ?: return null
        val canal = gestor.initialize(contexto, contexto.mainLooper, null) ?: return null
        val creado = CountDownLatch(1)
        var exito = false
        try
        {
            gestor.removeGroup(canal, object : WifiP2pManager.ActionListener
            {
                override fun onSuccess()
                {
                    crearGrupo(gestor, canal) { exito = it; creado.countDown() }
                }

                override fun onFailure(razon: Int)
                {
                    crearGrupo(gestor, canal) { exito = it; creado.countDown() }
                }
            })
        }
        catch (_: Exception)
        {
            return null
        }
        if (!runCatching { creado.await(10, TimeUnit.SECONDS) }.getOrDefault(false) || !exito)
        {
            runCatching { gestor.removeGroup(canal, null) }
            return null
        }
        val info = esperarInfoGrupo(gestor, canal) ?: run {
            runCatching { gestor.removeGroup(canal, null) }
            return null
        }
        val servidor = runCatching { ServerSocket(0) }.getOrNull() ?: run {
            runCatching { gestor.removeGroup(canal, null) }
            return null
        }
        return Sesion(info.first, info.second, servidor.localPort, servidor, gestor, canal, caja)
    }

    private fun crearGrupo(gestor: WifiP2pManager, canal: WifiP2pManager.Channel, listo: (Boolean) -> Unit)
    {
        try
        {
            gestor.createGroup(canal, object : WifiP2pManager.ActionListener
            {
                override fun onSuccess()
                {
                    listo(true)
                }

                override fun onFailure(razon: Int)
                {
                    listo(false)
                }
            })
        }
        catch (_: Exception)
        {
            listo(false)
        }
    }

    private fun esperarInfoGrupo(gestor: WifiP2pManager, canal: WifiP2pManager.Channel): Pair<String, String>?
    {
        for (intento in 0 until 12)
        {
            val listo = CountDownLatch(1)
            var datos: Pair<String, String>? = null
            try
            {
                gestor.requestGroupInfo(canal) { grupo ->
                    val ssid = grupo?.networkName
                    val pass = grupo?.passphrase
                    if (!ssid.isNullOrBlank() && !pass.isNullOrBlank())
                    {
                        datos = Pair(ssid, pass)
                    }
                    listo.countDown()
                }
            }
            catch (_: Exception)
            {
                return null
            }
            runCatching { listo.await(2, TimeUnit.SECONDS) }
            if (datos != null)
            {
                return datos
            }
            try
            {
                Thread.sleep(500)
            }
            catch (_: InterruptedException)
            {
                return null
            }
        }
        return null
    }

    fun recibir(contexto: Context, ssid: String, pass: String, puerto: Int, segundos: Long): ByteArray?
    {
        if (Build.VERSION.SDK_INT < 29)
        {
            return null
        }
        val conectividad = contexto.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val especificacion = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(pass)
            .build()
        val peticion = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(especificacion)
            .build()
        val listo = CountDownLatch(1)
        var redLista: Network? = null
        val callback = object : ConnectivityManager.NetworkCallback()
        {
            override fun onAvailable(red: Network)
            {
                redLista = red
                listo.countDown()
            }

            override fun onUnavailable()
            {
                listo.countDown()
            }
        }
        return try
        {
            conectividad.requestNetwork(peticion, callback, (segundos * 1000).toInt())
            runCatching { listo.await(segundos, TimeUnit.SECONDS) }
            val red = redLista ?: return null
            leerBlob(red, puerto)
        }
        catch (_: Exception)
        {
            null
        }
        finally
        {
            runCatching { conectividad.unregisterNetworkCallback(callback) }
        }
    }

    private fun leerBlob(red: Network, puerto: Int): ByteArray?
    {
        return try
        {
            val socket = red.socketFactory.createSocket()
            socket.use {
                it.connect(InetSocketAddress(IP_GRUPO, puerto), 10000)
                it.soTimeout = 60000
                val entrada = DataInputStream(it.inputStream.buffered())
                val tamano = entrada.readInt()
                if (tamano <= 0 || tamano > TOPE_BLOB)
                {
                    return null
                }
                val datos = ByteArray(tamano)
                entrada.readFully(datos)
                it.outputStream.write(49)
                it.outputStream.flush()
                datos
            }
        }
        catch (_: Throwable)
        {
            null
        }
    }
}
