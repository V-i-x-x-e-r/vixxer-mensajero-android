package dev.vixxer.mensajero

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject

object NotificadorMensajes
{
    private const val CANAL = "mensajes"

    @Volatile
    private var app: AplicacionVixxer? = null

    private val alDirecto = Emitter.Listener { args ->
        val aplicacion = app ?: return@Listener
        if (aplicacion.enPrimerPlano())
        {
            return@Listener
        }
        val fila = args.getOrNull(0) as? JSONObject ?: return@Listener
        val de = fila.optString("remitente_id")
        if (de.isEmpty())
        {
            return@Listener
        }
        notificar(aplicacion, de.hashCode(), nombreDe(aplicacion, de) ?: "Nuevo mensaje", "Te envió un mensaje", "chat/$de")
    }

    private val alGrupo = Emitter.Listener { args ->
        val aplicacion = app ?: return@Listener
        if (aplicacion.enPrimerPlano())
        {
            return@Listener
        }
        val fila = args.getOrNull(0) as? JSONObject ?: return@Listener
        val grupoId = fila.optString("grupo_id")
        if (grupoId.isEmpty() || fila.optString("remitente_id") == aplicacion.boveda.leer(ClavesSeguras.MI_ID))
        {
            return@Listener
        }
        notificar(aplicacion, grupoId.hashCode(), "Mensaje de grupo", "Tienes un mensaje nuevo", "grupo/$grupoId")
    }

    fun enganchar(aplicacion: AplicacionVixxer, socket: Socket)
    {
        app = aplicacion
        socket.off("mensaje:recibido", alDirecto)
        socket.on("mensaje:recibido", alDirecto)
        socket.off("grupo:mensaje", alGrupo)
        socket.on("grupo:mensaje", alGrupo)
    }

    private fun nombreDe(aplicacion: AplicacionVixxer, id: String): String?
    {
        return runCatching {
            val amigos = aplicacion.cacheChats.leerLista()?.optJSONArray("amigos") ?: return null
            for (i in 0 until amigos.length())
            {
                val amigo = amigos.optJSONObject(i) ?: continue
                if (amigo.optString("id") == id)
                {
                    return amigo.optString("usuario").takeIf { it.isNotEmpty() }
                }
            }
            null
        }.getOrNull()
    }

    private fun notificar(aplicacion: AplicacionVixxer, id: Int, titulo: String, cuerpo: String, destino: String)
    {
        try
        {
            val gestor = aplicacion.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            gestor.createNotificationChannel(
                NotificationChannel(CANAL, "Mensajes nuevos", NotificationManager.IMPORTANCE_HIGH),
            )
            val abrir = PendingIntent.getActivity(
                aplicacion,
                destino.hashCode(),
                Intent(aplicacion, ActividadPrincipal::class.java).putExtra("vixxer_destino", destino),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notificacion = Notification.Builder(aplicacion, CANAL)
                .setContentTitle(titulo)
                .setContentText(cuerpo)
                .setSmallIcon(R.mipmap.ic_lanzador)
                .setAutoCancel(true)
                .setContentIntent(abrir)
                .build()
            gestor.notify(id, notificacion)
        }
        catch (_: Exception)
        {
        }
    }
}
