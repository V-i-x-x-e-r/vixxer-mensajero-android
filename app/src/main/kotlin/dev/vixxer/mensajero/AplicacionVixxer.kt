package dev.vixxer.mensajero

import android.app.Application
import com.goterl.lazysodium.SodiumAndroid
import dev.vixxer.mensajero.nucleo.Alias
import dev.vixxer.mensajero.nucleo.Almacen
import dev.vixxer.mensajero.nucleo.Borradores
import dev.vixxer.mensajero.nucleo.CacheChats
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ClienteApi
import dev.vixxer.mensajero.nucleo.Cripto
import dev.vixxer.mensajero.nucleo.EstadosChat
import dev.vixxer.mensajero.nucleo.Firma
import dev.vixxer.mensajero.nucleo.Identidad
import dev.vixxer.mensajero.nucleo.Llaves

class AplicacionVixxer : Application()
{
    lateinit var boveda: Almacen
        private set
    lateinit var estado: Almacen
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
    var alExpirarSesion: () -> Unit = {}

    override fun onCreate()
    {
        super.onCreate()
        Cripto.sodio = SodiumAndroid()
        boveda = BovedaSegura(this)
        estado = AlmacenPreferencias(this)
        api = ClienteApi(
            baseUrl = Config.API_URL,
            token = { boveda.leer(ClavesSeguras.TOKEN) },
            alExpirarSesion = { alExpirarSesion() },
        )
        firma = Firma(boveda, api)
        identidad = Identidad(boveda)
        llaves = Llaves(api)
        estadosChat = EstadosChat(estado)
        cacheChats = CacheChats(estado)
        borradores = Borradores(estado)
        aliasLocal = Alias(estado)
    }
}
