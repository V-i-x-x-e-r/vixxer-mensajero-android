package dev.vixxer.mensajero

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.vixxer.mensajero.nucleo.ClavesSeguras

class ServicioFcm : FirebaseMessagingService()
{
    override fun onNewToken(token: String)
    {
        val app = application as? AplicacionVixxer ?: return
        subirToken(app, token)
    }

    override fun onMessageReceived(mensaje: RemoteMessage)
    {
    }

    companion object
    {
        fun registrar(app: AplicacionVixxer)
        {
            try
            {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    if (!token.isNullOrBlank())
                    {
                        subirToken(app, token)
                    }
                }
            }
            catch (_: Exception)
            {
            }
        }

        private fun subirToken(app: AplicacionVixxer, token: String)
        {
            val hilo = Thread {
                if (app.boveda.leer(ClavesSeguras.TOKEN) != null)
                {
                    runCatching { app.api.guardarPushToken(token, "android") }
                }
            }
            hilo.isDaemon = true
            hilo.start()
        }
    }
}
