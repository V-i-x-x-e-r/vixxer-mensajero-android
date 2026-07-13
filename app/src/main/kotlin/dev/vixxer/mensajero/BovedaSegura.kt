package dev.vixxer.mensajero

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.vixxer.mensajero.nucleo.Almacen

@Suppress("DEPRECATION")
class BovedaSegura(contexto: Context) : Almacen
{
    private val preferencias = EncryptedSharedPreferences.create(
        contexto,
        "vixxer_boveda",
        MasterKey.Builder(contexto).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    override fun leer(clave: String): String? = preferencias.getString(clave, null)

    override fun escribir(clave: String, valor: String)
    {
        preferencias.edit().putString(clave, valor).apply()
    }

    override fun borrar(clave: String)
    {
        preferencias.edit().remove(clave).apply()
    }
}

class AlmacenPreferencias(contexto: Context) : Almacen
{
    private val preferencias = contexto.getSharedPreferences("vixxer_estado", Context.MODE_PRIVATE)

    override fun leer(clave: String): String? = preferencias.getString(clave, null)

    override fun escribir(clave: String, valor: String)
    {
        preferencias.edit().putString(clave, valor).apply()
    }

    override fun borrar(clave: String)
    {
        preferencias.edit().remove(clave).apply()
    }
}
