package dev.vixxer.mensajero.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import dev.vixxer.mensajero.ActividadPrincipal
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.R

class ServicioCercania : Service()
{
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    {
        entrarPrimerPlano()
        val app = application as? AplicacionVixxer
        if (app == null)
        {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!GestorCercania.corriendo)
        {
            GestorCercania.iniciar(app, this)
        }
        if (!GestorCercania.corriendo)
        {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun entrarPrimerPlano()
    {
        val gestor = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        gestor.createNotificationChannel(
            NotificationChannel(CANAL, "Modo cercanía", NotificationManager.IMPORTANCE_LOW),
        )
        val abrir = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ActividadPrincipal::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacion = Notification.Builder(this, CANAL)
            .setContentTitle("Radar de cercanía activo")
            .setContentText("Puedes mensajear por Bluetooth sin internet")
            .setSmallIcon(R.mipmap.ic_lanzador)
            .setContentIntent(abrir)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34)
        {
            startForeground(ID_NOTIFICACION, notificacion, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        }
        else
        {
            startForeground(ID_NOTIFICACION, notificacion)
        }
    }

    companion object
    {
        private const val CANAL = "cercania"
        private const val ID_NOTIFICACION = 7001

        fun arrancar(contexto: Context)
        {
            ContextCompat.startForegroundService(contexto, Intent(contexto, ServicioCercania::class.java))
        }

        fun parar(contexto: Context)
        {
            contexto.stopService(Intent(contexto, ServicioCercania::class.java))
        }
    }
}
