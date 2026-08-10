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
import dev.vixxer.mensajero.nucleo.DifusionCercania
import dev.vixxer.mensajero.nucleo.MeshCercania
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class Cercano(val id: String, val nombre: String, val rssi: Int, val token: String? = null, val caps: Int = 0)

data class CapsVecino(val psm: Int, val llaveDifusion: String?, val cuando: Long)

data class ResultadoRadio(
    val exito: Boolean,
    val enlace: String,
    val reintentos: Int,
    val duracionMs: Long,
)

@SuppressLint("MissingPermission")
class RadioBle(private val contexto: Context)
{
    private val servicioUuid = UUID.fromString(MeshCercania.SERVICIO_UUID)
    private val caracteristicaUuid = UUID.fromString(MeshCercania.CARACTERISTICA_UUID)
    private val capsUuid = UUID.fromString(MeshCercania.CARACTERISTICA_CAPS_UUID)
    private val datoCercaniaUuid = ParcelUuid.fromString(MeshCercania.DATO_CERCANIA_UUID)
    private val datoCapsUuid = ParcelUuid.fromString(MeshCercania.DATO_CAPS_UUID)
    private val trozo = 180
    private val umbralL2cap = 4096
    private val vidaCapsMs = 5 * 60 * 1000L
    private val topeSobre = 512 * 1024
    private val topeBuffers = 16
    private val topeCaps = 64

    private var anunciante: BluetoothLeAdvertiser? = null
    private var callbackAnuncio: AdvertiseCallback? = null
    private var servidor: BluetoothGattServer? = null
    private var servidorL2cap: android.bluetooth.BluetoothServerSocket? = null
    private var escaner: BluetoothLeScanner? = null
    private var callbackEscaneo: ScanCallback? = null
    private val buffers = LinkedHashMap<String, ByteArrayOutputStream>()
    private val desbordados = HashSet<String>()
    private val capsConocidas = LinkedHashMap<String, CapsVecino>()
    var llaveDifusionLocal: (() -> String?)? = null
    private var alMensaje: ((String) -> Unit)? = null
    private var entrega: ExecutorService? = null

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
            abrirL2cap(adaptador)
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
            val constructorRespuesta = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceData(datoCapsUuid, byteArrayOf(capsRadio()))
            tokenCercania?.let { constructorRespuesta.addServiceData(datoCercaniaUuid, it) }
            adv.startAdvertising(ajustes, datos, constructorRespuesta.build(), cb)
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
        try
        {
            servidorL2cap?.close()
        }
        catch (_: Exception)
        {
        }
        servidorL2cap = null
        synchronized(buffers)
        {
            buffers.clear()
            desbordados.clear()
        }
        cerrarCarril()
    }

    fun escanear(soloVixxer: Boolean, alEncontrar: (Cercano) -> Unit, onError: (String) -> Unit): () -> Unit
    {
        detenerEscaneo()
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
                val caps = resultado.scanRecord?.getServiceData(datoCapsUuid)
                    ?.firstOrNull()?.toInt()?.and(0xFF) ?: 0
                alEncontrar(Cercano(dispositivo.address, nombre, resultado.rssi, token, caps))
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
        escaner = null
    }

    fun conectarYEnviar(direccion: String, texto: String): ResultadoRadio
    {
        val inicio = System.nanoTime()
        val gestorBt = gestor ?: return resultadoRadio(false, "ble", 0, inicio)
        val adaptador = gestorBt.adapter ?: return resultadoRadio(false, "ble", 0, inicio)
        val dispositivo = try
        {
            adaptador.getRemoteDevice(direccion)
        }
        catch (_: Exception)
        {
            return resultadoRadio(false, "ble", 0, inicio)
        }
        val bytes = (texto + "\n").toByteArray(Charsets.UTF_8)
        var reintentos = 0
        if (bytes.size > umbralL2cap && android.os.Build.VERSION.SDK_INT >= 29)
        {
            val psm = psmCacheado(direccion)
            if (psm != null)
            {
                if (enviarPorL2cap(dispositivo, psm, bytes))
                {
                    return resultadoRadio(true, "l2cap", reintentos, inicio)
                }
                olvidarCaps(direccion)
                reintentos += 1
            }
        }
        val (ok, reintentar) = correrSesion(dispositivo, bytes, forzarGatt = false)
        if (ok || !reintentar)
        {
            return resultadoRadio(ok, "gatt", reintentos, inicio)
        }
        reintentos += 1
        val segundo = correrSesion(dispositivo, bytes, forzarGatt = true).first
        return resultadoRadio(segundo, "gatt", reintentos, inicio)
    }

    private fun resultadoRadio(exito: Boolean, enlace: String, reintentos: Int, inicio: Long): ResultadoRadio
    {
        val duracion = (System.nanoTime() - inicio).coerceAtLeast(0L) / 1_000_000L
        return ResultadoRadio(exito, enlace, reintentos, duracion)
    }

    private fun correrSesion(dispositivo: BluetoothDevice, bytes: ByteArray, forzarGatt: Boolean): Pair<Boolean, Boolean>
    {
        val sesion = SesionEnvio(dispositivo, bytes, forzarGatt)
        val gatt = try
        {
            dispositivo.connectGatt(contexto, false, sesion.callback)
        }
        catch (_: Exception)
        {
            return Pair(false, false)
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
        return Pair(ok, sesion.reintentarPorGatt)
    }

    private fun enviarPorL2cap(dispositivo: BluetoothDevice, psm: Int, bytes: ByteArray): Boolean
    {
        if (android.os.Build.VERSION.SDK_INT < 29 || psm <= 0)
        {
            return false
        }
        var socket: android.bluetooth.BluetoothSocket? = null
        var guardian: Thread? = null
        return try
        {
            val abierto = dispositivo.createInsecureL2capChannel(psm)
            socket = abierto
            val vigilante = Thread {
                try
                {
                    Thread.sleep(20000)
                }
                catch (_: InterruptedException)
                {
                    return@Thread
                }
                try
                {
                    abierto.close()
                }
                catch (_: Exception)
                {
                }
            }
            vigilante.isDaemon = true
            vigilante.start()
            guardian = vigilante
            abierto.connect()
            abierto.outputStream.write(bytes)
            abierto.outputStream.flush()
            val ack = abierto.inputStream.read()
            ack == 49
        }
        catch (_: Exception)
        {
            false
        }
        finally
        {
            guardian?.interrupt()
            try
            {
                socket?.close()
            }
            catch (_: Exception)
            {
            }
        }
    }

    private fun capsVigentes(direccion: String): CapsVecino?
    {
        synchronized(capsConocidas) {
            val guardadas = capsConocidas[direccion] ?: return null
            if (System.currentTimeMillis() - guardadas.cuando > vidaCapsMs)
            {
                capsConocidas.remove(direccion)
                return null
            }
            return guardadas
        }
    }

    private fun psmCacheado(direccion: String): Int?
    {
        return capsVigentes(direccion)?.psm?.takeIf { it > 0 }
    }

    fun llaveDifusionDe(direccion: String): String? = capsVigentes(direccion)?.llaveDifusion

    private fun recordarCaps(direccion: String, psm: Int, llaveDifusion: String?)
    {
        synchronized(capsConocidas) {
            capsConocidas.remove(direccion)
            capsConocidas[direccion] = CapsVecino(psm, llaveDifusion, System.currentTimeMillis())
            while (capsConocidas.size > topeCaps)
            {
                capsConocidas.remove(capsConocidas.keys.first())
            }
        }
    }

    private fun olvidarCaps(direccion: String)
    {
        synchronized(capsConocidas) {
            capsConocidas.remove(direccion)
        }
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

            override fun onCharacteristicReadRequest(
                dispositivo: BluetoothDevice,
                requestId: Int,
                offset: Int,
                caracteristica: BluetoothGattCharacteristic,
            )
            {
                val valor = if (caracteristica.uuid == capsUuid) capsLocales() else ByteArray(0)
                val pedazo = if (offset >= valor.size) ByteArray(0) else valor.copyOfRange(offset, valor.size)
                try
                {
                    servidor?.sendResponse(dispositivo, requestId, BluetoothGatt.GATT_SUCCESS, offset, pedazo)
                }
                catch (_: Exception)
                {
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
        val caps = BluetoothGattCharacteristic(
            capsUuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        servicio.addCharacteristic(caps)
        gatt.addService(servicio)
        servidor = gatt
    }

    private fun capsRadio(): Byte
    {
        var flags = 0
        if (android.os.Build.VERSION.SDK_INT >= 29)
        {
            flags = flags or 1
            if (RadioWifi.soportado(contexto))
            {
                flags = flags or 2
            }
        }
        return flags.toByte()
    }

    private fun capsLocales(): ByteArray
    {
        val psm = try
        {
            if (android.os.Build.VERSION.SDK_INT >= 29) servidorL2cap?.psm ?: 0 else 0
        }
        catch (_: Exception)
        {
            0
        }
        return DifusionCercania.codificarCapacidades(psm, llaveDifusionLocal?.invoke())
    }

    private fun abrirL2cap(adaptador: android.bluetooth.BluetoothAdapter)
    {
        if (android.os.Build.VERSION.SDK_INT < 29 || servidorL2cap != null)
        {
            return
        }
        val servidorSocket = try
        {
            adaptador.listenUsingInsecureL2capChannel()
        }
        catch (_: Exception)
        {
            return
        }
        servidorL2cap = servidorSocket
        val hilo = Thread {
            while (servidorL2cap === servidorSocket)
            {
                val socket = try
                {
                    servidorSocket.accept()
                }
                catch (_: Exception)
                {
                    break
                }
                Thread { atenderL2cap(socket) }.apply { isDaemon = true }.start()
            }
        }
        hilo.isDaemon = true
        hilo.start()
    }

    private fun atenderL2cap(socket: android.bluetooth.BluetoothSocket)
    {
        try
        {
            val entrada = socket.inputStream.buffered()
            val salida = socket.outputStream
            val buffer = ByteArrayOutputStream()
            var desbordado = false
            while (true)
            {
                val b = entrada.read()
                if (b < 0)
                {
                    break
                }
                if (b == 10)
                {
                    val completo = buffer.toByteArray()
                    buffer.reset()
                    if (completo.isNotEmpty() && !desbordado)
                    {
                        salida.write(49)
                        salida.flush()
                        despachar(String(completo, Charsets.UTF_8))
                    }
                    desbordado = false
                }
                else if (buffer.size() >= topeSobre)
                {
                    buffer.reset()
                    desbordado = true
                }
                else
                {
                    buffer.write(b)
                }
            }
        }
        catch (_: Exception)
        {
        }
        finally
        {
            try
            {
                socket.close()
            }
            catch (_: Exception)
            {
            }
        }
    }

    @Synchronized
    private fun carril(): ExecutorService
    {
        val actual = entrega
        if (actual != null && !actual.isShutdown)
        {
            return actual
        }
        val nuevo = Executors.newSingleThreadExecutor { tarea ->
            Thread(tarea, "vixxer-mesh-entrega").apply { isDaemon = true }
        }
        entrega = nuevo
        return nuevo
    }

    @Synchronized
    private fun cerrarCarril()
    {
        entrega?.shutdownNow()
        entrega = null
    }

    private fun despachar(texto: String)
    {
        val cb = alMensaje ?: return
        try
        {
            carril().execute { runCatching { cb(texto) } }
        }
        catch (_: Exception)
        {
        }
    }

    private fun acumular(direccion: String, trozoBytes: ByteArray)
    {
        val completados = ArrayList<ByteArray>()
        synchronized(buffers)
        {
            val buffer = buffers.getOrPut(direccion) { ByteArrayOutputStream() }
            while (buffers.size > topeBuffers)
            {
                val masViejo = buffers.keys.firstOrNull { it != direccion } ?: break
                buffers.remove(masViejo)
                desbordados.remove(masViejo)
            }
            for (b in trozoBytes)
            {
                if (b.toInt() == 10)
                {
                    val completo = buffer.toByteArray()
                    buffer.reset()
                    if (completo.isNotEmpty() && !desbordados.remove(direccion))
                    {
                        completados.add(completo)
                    }
                }
                else if (buffer.size() >= topeSobre)
                {
                    buffer.reset()
                    desbordados.add(direccion)
                }
                else
                {
                    buffer.write(b.toInt())
                }
            }
        }
        for (completo in completados)
        {
            despachar(String(completo, Charsets.UTF_8))
        }
    }

    private inner class SesionEnvio(
        private val dispositivo: BluetoothDevice,
        private val bytes: ByteArray,
        forzarGatt: Boolean,
    )
    {
        var gatt: BluetoothGatt? = null
        var reintentarPorGatt = false
            private set
        private val cierre = CountDownLatch(1)
        @Volatile
        private var exito = false
        private var cursor = 0
        private var trozoSesion = trozo
        private var caracteristica: BluetoothGattCharacteristic? = null
        private val intentarL2cap =
            !forzarGatt && bytes.size > umbralL2cap && android.os.Build.VERSION.SDK_INT >= 29

        @Volatile
        private var psmRemoto = 0

        @Volatile
        private var capsProcesadas = false

        @Volatile
        private var l2capLanzado = false

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
                    if (psmRemoto > 0 && !exito && !l2capLanzado)
                    {
                        lanzarL2cap()
                    }
                    else
                    {
                        terminar(exito)
                    }
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
                if (intentarL2cap)
                {
                    val caps = servicio.getCharacteristic(capsUuid)
                    if (caps != null && runCatching { g.readCharacteristic(caps) }.getOrDefault(false))
                    {
                        return
                    }
                }
                enviarSiguiente(g)
            }

            @Deprecated("Compat con API < 33")
            override fun onCharacteristicRead(g: BluetoothGatt, car: BluetoothGattCharacteristic, estado: Int)
            {
                if (android.os.Build.VERSION.SDK_INT < 33)
                {
                    alLeerCaps(g, car.value, estado)
                }
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                car: BluetoothGattCharacteristic,
                valor: ByteArray,
                estado: Int,
            )
            {
                alLeerCaps(g, valor, estado)
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

        private fun alLeerCaps(g: BluetoothGatt, valor: ByteArray?, estado: Int)
        {
            if (capsProcesadas)
            {
                return
            }
            capsProcesadas = true
            val leidas = if (estado == BluetoothGatt.GATT_SUCCESS)
            {
                DifusionCercania.leerCapacidades(valor)
            }
            else
            {
                null
            }
            val psm = leidas?.psm ?: 0
            if (leidas != null)
            {
                recordarCaps(dispositivo.address, psm, leidas.llaveDifusion)
            }
            if (psm > 0)
            {
                psmRemoto = psm
                try
                {
                    g.disconnect()
                }
                catch (_: Exception)
                {
                    lanzarL2cap()
                }
            }
            else
            {
                enviarSiguiente(g)
            }
        }

        private fun lanzarL2cap()
        {
            if (l2capLanzado)
            {
                return
            }
            l2capLanzado = true
            val hilo = Thread {
                if (enviarPorL2cap(dispositivo, psmRemoto, bytes))
                {
                    terminar(true)
                }
                else
                {
                    olvidarCaps(dispositivo.address)
                    reintentarPorGatt = true
                    terminar(false)
                }
            }
            hilo.isDaemon = true
            hilo.start()
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
            val limite = if (bytes.size > umbralL2cap) 60L else 15L
            return try
            {
                cierre.await(limite, TimeUnit.SECONDS)
                exito
            }
            catch (_: Exception)
            {
                false
            }
        }
    }
}
