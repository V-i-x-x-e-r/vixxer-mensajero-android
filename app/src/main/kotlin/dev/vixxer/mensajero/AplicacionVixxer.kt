package dev.vixxer.mensajero

import android.app.Application
import com.goterl.lazysodium.SodiumAndroid
import dev.vixxer.mensajero.nucleo.Alias
import dev.vixxer.mensajero.nucleo.AlmacenPorCuenta
import dev.vixxer.mensajero.nucleo.Borradores
import dev.vixxer.mensajero.nucleo.CacheChats
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ClienteApi
import dev.vixxer.mensajero.nucleo.ConexionSocket
import dev.vixxer.mensajero.nucleo.Cripto
import dev.vixxer.mensajero.nucleo.DiagnosticoMesh
import dev.vixxer.mensajero.nucleo.EstadosChat
import dev.vixxer.mensajero.nucleo.Firma
import dev.vixxer.mensajero.nucleo.Identidad
import dev.vixxer.mensajero.nucleo.Llaves
import dev.vixxer.mensajero.nucleo.Outbox
import dev.vixxer.mensajero.ui.CacheMedia

class AplicacionVixxer : Application()
{
    lateinit var boveda: AlmacenPorCuenta
        private set
    lateinit var estado: AlmacenPorCuenta
        private set
    lateinit var api: ClienteApi
        private set
    lateinit var firma: Firma
        private set
    lateinit var identidad: Identidad
        private set
    lateinit var llaves: Llaves
        private set
    lateinit var estadosChat: EstadosChat
        private set
    lateinit var cacheChats: CacheChats
        private set
    lateinit var borradores: Borradores
        private set
    lateinit var aliasLocal: Alias
        private set
    lateinit var outbox: Outbox
        private set
    lateinit var diagnosticoMesh: DiagnosticoMesh
        private set
    var alExpirarSesion: () -> Unit = {}
    private lateinit var bovedaBase: BovedaSegura
    private lateinit var estadoBase: AlmacenPreferencias

    @Volatile
    var saltarBloqueo: Boolean = false

    override fun onCreate()
    {
        super.onCreate()
        Cripto.sodio = SodiumAndroid()
        bovedaBase = BovedaSegura(this)
        estadoBase = AlmacenPreferencias(this)
        val cuentaId = bovedaBase.leer(ClavesSeguras.MI_ID)
        boveda = AlmacenPorCuenta(bovedaBase, CLAVES_GLOBALES_BOVEDA)
        estado = AlmacenPorCuenta(estadoBase, CLAVES_GLOBALES_ESTADO)
        activarCuenta(cuentaId)
        if (cuentaId != null)
        {
            migrarLegado()
        }
        api = ClienteApi(
            baseUrl = Config.API_URL,
            token = { boveda.leer(ClavesSeguras.TOKEN) },
            alExpirarSesion = {
                cerrarSesionLocal()
                this.alExpirarSesion()
            },
        )
        firma = Firma(boveda, api)
        identidad = Identidad(boveda)
        llaves = Llaves(api)
        estadosChat = EstadosChat(estado)
        cacheChats = CacheChats(estado)
        borradores = Borradores(estado)
        aliasLocal = Alias(estado)
        outbox = Outbox(boveda)
        diagnosticoMesh = DiagnosticoMesh(estado)
        if (cuentaId != null)
        {
            migrarOutbox()
        }
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks
        {
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityStarted(a: android.app.Activity)
            {
                actividadesVisibles += 1
            }

            override fun onActivityResumed(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivityStopped(a: android.app.Activity)
            {
                actividadesVisibles -= 1
            }

            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
    }

    @Volatile
    private var actividadesVisibles = 0

    fun enPrimerPlano(): Boolean = actividadesVisibles > 0

    @Synchronized
    fun activarCuenta(cuentaId: String?)
    {
        boveda.cambiarCuenta(cuentaId)
        estado.cambiarCuenta(cuentaId)
    }

    @Synchronized
    fun guardarSesion(token: String, cuentaId: String)
    {
        val mismaCuenta = boveda.leer(ClavesSeguras.MI_ID) == cuentaId
        prepararSesion(cuentaId)
        if (mismaCuenta)
        {
            migrarLegado()
        }
        migrarOutbox()
        escribirSesion(token, cuentaId)
    }

    @Synchronized
    fun adoptarLegado(cuentaId: String, llavePublicaRemota: String): Boolean
    {
        if (cuentaId.isBlank() || llavePublicaRemota.isBlank() ||
            boveda.leer(ClavesSeguras.MI_ID) != cuentaId)
        {
            return false
        }
        val adoptado = boveda.migrarLegadoSiCoincide(
            ClavesSeguras.CLAVE_PUBLICA,
            llavePublicaRemota,
            bovedaBase.claves(),
        )
        if (!adoptado)
        {
            return false
        }
        estado.migrarLegado(estadoBase.claves())
        migrarOutbox()
        return true
    }

    @Synchronized
    fun leerOutbox(cuentaId: String): List<Outbox.Pendiente>
    {
        if (!esCuentaActiva(cuentaId)) return emptyList()
        return outbox.leerTodos()
    }

    @Synchronized
    fun contieneOutbox(cuentaId: String, pendiente: Outbox.Pendiente): Boolean
    {
        if (!esCuentaActiva(cuentaId)) return false
        return outbox.contiene(pendiente)
    }

    @Synchronized
    fun persistirResultadoOutbox(
        cuentaId: String,
        pendiente: Outbox.Pendiente,
        exitoso: Boolean,
    ): Boolean
    {
        if (!esCuentaActiva(cuentaId)) return false
        if (exitoso)
        {
            when (pendiente.tipo)
            {
                Outbox.Tipo.DIRECTO -> outbox.quitar(pendiente.destinoId, pendiente.clienteId)
                Outbox.Tipo.GRUPO -> outbox.quitarGrupo(pendiente.destinoId, pendiente.clienteId)
            }
        }
        else
        {
            outbox.registrarFallo(pendiente)
        }
        return true
    }

    private fun prepararSesion(cuentaId: String)
    {
        ConexionSocket.desconectar()
        if (boveda.leer(ClavesSeguras.MI_ID) != cuentaId)
        {
            CacheMedia.limpiar(this)
        }
        activarCuenta(cuentaId)
    }

    private fun escribirSesion(token: String, cuentaId: String)
    {
        boveda.escribir(ClavesSeguras.MI_ID, cuentaId)
        boveda.escribir(ClavesSeguras.TOKEN, token)
    }

    private fun esCuentaActiva(cuentaId: String): Boolean =
        cuentaId.isNotBlank() && boveda.leer(ClavesSeguras.MI_ID) == cuentaId

    @Synchronized
    fun cerrarSesionLocal()
    {
        ConexionSocket.desconectar()
        CacheMedia.limpiar(this)
        boveda.borrar(ClavesSeguras.TOKEN)
        boveda.borrar(ClavesSeguras.MI_ID)
        activarCuenta(null)
    }

    private fun migrarLegado()
    {
        boveda.migrarLegado(bovedaBase.claves())
        estado.migrarLegado(estadoBase.claves())
    }

    private fun migrarOutbox()
    {
        val anterior = Outbox(estado)
        val prefijo = "vixxer_outbox_"
        estado.clavesDeCuenta(estadoBase.claves())
            .filter { it.startsWith(prefijo) && it != "vixxer_outbox_v2" }
            .map { it.removePrefix(prefijo) }
            .filter { it.isNotBlank() }
            .forEach { anterior.leer(it) }
        outbox.importarDesde(anterior)
    }

    companion object
    {
        private val CLAVES_GLOBALES_BOVEDA = setOf(
            ClavesSeguras.TOKEN,
            ClavesSeguras.MI_ID,
            ClavesSeguras.REGISTRO_PENDIENTE,
            "vixxer_pin",
        )
        private val CLAVES_GLOBALES_ESTADO = setOf(
            "vixxer_tema",
            "vixxer_tema_acento",
            "vixxer_bloquear_capturas",
            "vixxer_bloqueo_activo",
            "vixxer_biometrico",
        )
    }
}
