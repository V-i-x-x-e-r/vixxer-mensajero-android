package dev.vixxer.mensajero.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import dev.vixxer.mensajero.nucleo.Cripto
import dev.vixxer.mensajero.nucleo.MeshCercania
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class Cercano(val id: String, val nombre: String, val rssi: Int, val token: String? = null)

@SuppressLint("MissingPermission")
class RadioBle(private val contexto: Context)
{
    private val servicioUuid = UUID.fromString(MeshCercania.SERVICIO_UUID)
    private val caracteristicaUuid = UUID.fromString(MeshCercania.CARACTERISTICA_UUID)
    private val datoCercaniaUuid = ParcelUuid.fromString(MeshCercania.DATO_CERCANIA_UUID)
    private val trozo = 180

    private var anunciante: BluetoothLeAdvertiser? = null
    private var callbackAnuncio: AdvertiseCallback? = null
    private var servidor: BluetoothGattServer? = null
    private var escaner: BluetoothLeScanner? = null
    private var callbackEscaneo: ScanCallback? = null
    private val buffers = HashMap<String, ByteArrayOutputStream>()
    private var alMensaje: ((String) -> Unit)? = null

    private val gestor: BluetoothManager?
        get() = contexto.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    fun alRecibir(cb: (String) -> Unit)
    {
        alMensaje = cb
    }

    fun anunciar(tokenCercania: ByteArray? = null): String
    {
        return try
        {
            val gestorBt = gestor ?: return "sin-bluetooth"
            val adaptador = gestorBt.adapter ?: return "sin-bluetooth"
            if (!adaptador.isEnabled)
            {
                return "bt-apagado"
            }
            abrirServidor(gestorBt)
            val adv = adaptador.bluetoothLeAdvertiser ?: return "sin-anunciante"
            anunciante = adv
            callbackAnuncio?.let { adv.stopAdvertising(it) }
            val ajustes = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .build()
            val datos = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(servicioUuid))
                .build()
            val cb = object : AdvertiseCallback() {}
            callbackAnuncio = cb
            val respuesta = tokenCercania?.let {
                AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceData(datoCercaniaUuid, it)
                    .build()
            }
            if (respuesta != null)
            {
                adv.startAdvertising(ajustes, datos, respuesta, cb)
            }
            else
            {
                adv.startAdvertising(ajustes, datos, cb)
            }
            "ok"
        }
        catch (e: Exception)
        {
            "error: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    fun detenerAnuncio()
    {
        try
        {
            callbackAnuncio?.let { anunciante?.stopAdvertising(it) }
        }
        catch (_: Exception)
        {
        }
        callbackAnuncio = null
        try
        {
            servidor?.close()
        }
        catch (_: Exception)
        {
        }
        servidor = null
        buffers.clear()
    }

    fun escanear(soloVixxer: Boolean, alEncontrar: (Cercano) -> Unit, onError: (String) -> Unit): () -> Unit
    {
        val gestorBt = gestor
        val adaptador = gestorBt?.adapter
        if (adaptador == null)
        {
            onError("sin-bluetooth")
            return {}
        }
        if (!adaptador.isEnabled)
        {
            onError("bt-apagado")
            return {}
        }
        val motor = adaptador.bluetoothLeScanner
        if (motor == null)
        {
            onError("sin-escaner")
            return {}
        }
        escaner = motor
        val filtros = if (soloVixxer)
        {
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(servicioUuid)).build())
        }
        else
        {
            emptyList()
        }
        val ajustes = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val cb = object : ScanCallback()
        {
            override fun onScanResult(tipo: Int, resultado: ScanResult)
            {
                val dispositivo = resultado.device ?: return
                val nombre = dispositivo.name
                    ?: resultado.scanRecord?.deviceName
                    ?: "Vixxer"
                val token = resultado.scanRecord?.getServiceData(datoCercaniaUuid)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { Cripto.aBase64(it) }
                alEncontrar(Cercano(dispositivo.address, nombre, resultado.rssi, token))
            }

            override fun onScanFailed(codigo: Int)
            {
                onError("error: $codigo")
            }
        }
        callbackEscaneo = cb
        try
        {
            motor.startScan(filtros, ajustes, cb)
        }
        catch (e: Exception)
        {
            onError("error: " + (e.message ?: e.javaClass.simpleName))
        }
        return { detenerEscaneo() }
    }

    fun detenerEscaneo()
    {
        try
        {
            callbackEscaneo?.let { escaner?.stopScan(it) }
        }
        catch (_: Exception)
        {
        }
        callbackEscaneo = null
    }

    fun conectarYEnviar(direccion: String, texto: String): Boolean
    {
        val gestorBt = gestor ?: return false
        val adaptador = gestorBt.adapter ?: return false
        val dispositivo = try
        {
            adaptador.getRemoteDevice(direccion)
        }
        catch (_: Exception)
        {
            return false
        }
        val sesion = SesionEnvio(texto)
        val gatt = try
        {
            dispositivo.connectGatt(contexto, false, sesion.callback)
        }
        catch (_: Exception)
        {
            return false
        }
        sesion.gatt = gatt
        val ok = sesion.esperar()
        try
        {
            gatt.close()
        }
        catch (_: Exception)
        {
        }
        return ok
    }

    private fun abrirServidor(gestorBt: BluetoothManager)
    {
        if (servidor != null)
        {
            return
        }
        val cb = object : BluetoothGattServerCallback()
        {
            override fun onCharacteristicWriteRequest(
                dispositivo: BluetoothDevice,
                requestId: Int,
                caracteristica: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                valor: ByteArray,
            )
            {
                acumular(dispositivo.address, valor)
                if (responseNeeded)
                {
                    try
                    {
                        servidor?.sendResponse(dispositivo, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                    }
                    catch (_: Exception)
                    {
                    }
                }
            }
        }
        val gatt = gestorBt.openGattServer(contexto, cb)
        val servicio = BluetoothGattService(servicioUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val caracteristica = BluetoothGattCharacteristic(
            caracteristicaUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        servicio.addCharacteristic(caracteristica)
        gatt.addService(servicio)
        servidor = gatt
    }

    private fun acumular(direccion: String, trozoBytes: ByteArray)
    {
        val buffer = buffers.getOrPut(direccion) { ByteArrayOutputStream() }
        for (b in trozoBytes)
        {
            if (b.toInt() == 10)
            {
                val completo = buffer.toByteArray()
                buffer.reset()
                if (completo.isNotEmpty())
                {
                    alMensaje?.invoke(String(completo, Charsets.UTF_8))
                }
            }
            else
            {
                buffer.write(b.toInt())
            }
        }
    }

    private inner class SesionEnvio(texto: String)
    {
        var gatt: BluetoothGatt? = null
        private val cierre = CountDownLatch(1)
        private var exito = false
        private val bytes = (texto + "\n").toByteArray(Charsets.UTF_8)
        private var cursor = 0
        private var trozoSesion = trozo
        private var caracteristica: BluetoothGattCharacteristic? = null

        val callback = object : BluetoothGattCallback()
        {
            override fun onConnectionStateChange(g: BluetoothGatt, estado: Int, nuevoEstado: Int)
            {
                if (nuevoEstado == BluetoothProfile.STATE_CONNECTED)
                {
                    try
                    {
                        g.requestMtu(512)
                    }
                    catch (_: Exception)
                    {
                        terminar(false)
                    }
                }
                else if (nuevoEstado == BluetoothProfile.STATE_DISCONNECTED)
                {
                    terminar(exito)
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, estado: Int)
            {
                if (estado == BluetoothGatt.GATT_SUCCESS)
                {
                    trozoSesion = (mtu - 3).coerceAtLeast(trozo)
                }
                try
                {
                    g.discoverServices()
                }
                catch (_: Exception)
                {
                    terminar(false)
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, estado: Int)
            {
                if (estado != BluetoothGatt.GATT_SUCCESS)
                {
                    terminar(false)
                    return
                }
                val servicio = g.getService(servicioUuid)
                val car = servicio?.getCharacteristic(caracteristicaUuid)
                if (car == null)
                {
                    terminar(false)
                    return
                }
                caracteristica = car
                enviarSiguiente(g)
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, car: BluetoothGattCharacteristic, estado: Int)
            {
                if (estado != BluetoothGatt.GATT_SUCCESS)
                {
                    terminar(false)
                    return
                }
                enviarSiguiente(g)
            }
        }

        private fun enviarSiguiente(g: BluetoothGatt)
        {
            val car = caracteristica ?: return terminar(false)
            if (cursor >= bytes.size)
            {
                exito = true
                try
                {
                    g.disconnect()
                }
                catch (_: Exception)
                {
                    terminar(true)
                }
                return
            }
            val fin = minOf(cursor + trozoSesion, bytes.size)
            val pedazo = bytes.copyOfRange(cursor, fin)
            cursor = fin
            val ok = try
            {
                escribirTrozo(g, car, pedazo, fin >= bytes.size)
            }
            catch (_: Exception)
            {
                false
            }
            if (!ok)
            {
                terminar(false)
            }
        }

        @Suppress("DEPRECATION")
        private fun escribirTrozo(g: BluetoothGatt, car: BluetoothGattCharacteristic, pedazo: ByteArray, ultimo: Boolean): Boolean
        {
            val tipo = if (ultimo)
            {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            else
            {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            car.writeType = tipo
            if (android.os.Build.VERSION.SDK_INT >= 33)
            {
                return g.writeCharacteristic(car, pedazo, tipo) == BluetoothGatt.GATT_SUCCESS
            }
            car.value = pedazo
            return g.writeCharacteristic(car)
        }

        private fun terminar(resultado: Boolean)
        {
            exito = resultado
            cierre.countDown()
        }

        fun esperar(): Boolean
        {
            return try
            {
                cierre.await(15, TimeUnit.SECONDS)
                exito
            }
            catch (_: Exception)
            {
                false
            }
        }
    }
}
