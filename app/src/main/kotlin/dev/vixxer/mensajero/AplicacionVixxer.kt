package dev.vixxer.mensajero

import android.app.Application
import com.goterl.lazysodium.SodiumAndroid
import dev.vixxer.mensajero.nucleo.Almacen
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ClienteApi
import dev.vixxer.mensajero.nucleo.Cripto
import dev.vixxer.mensajero.nucleo.Firma

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
    }
}
