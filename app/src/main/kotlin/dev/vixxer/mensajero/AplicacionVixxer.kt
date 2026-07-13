package dev.vixxer.mensajero

import android.app.Application
import com.goterl.lazysodium.SodiumAndroid
import dev.vixxer.mensajero.nucleo.Cripto

class AplicacionVixxer : Application()
{
    override fun onCreate()
    {
        super.onCreate()
        Cripto.sodio = SodiumAndroid()
    }
}
