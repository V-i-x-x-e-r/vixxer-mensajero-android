package dev.vixxer.mensajero.debug

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.ble.GestorCercania
import java.util.Locale

class ReceptorPruebaCercania : BroadcastReceiver()
{
    override fun onReceive(contexto: Context, intent: Intent)
    {
        if (intent.action != ACCION)
        {
            return
        }
        val pendiente = goAsync()
        Thread {
            val resultado = ejecutar(contexto, intent)
            pendiente.setResultCode(if (resultado.ok) Activity.RESULT_OK else Activity.RESULT_CANCELED)
            pendiente.setResultData(resultado.detalle)
            pendiente.finish()
        }.start()
    }

    private fun ejecutar(contexto: Context, intent: Intent): ResultadoPrueba
    {
        val direccion = intent.getStringExtra(EXTRA_DIRECCION)
            ?.uppercase(Locale.ROOT)
            ?: return ResultadoPrueba(false, "Falta la direccion BLE")
        if (!BluetoothAdapter.checkBluetoothAddress(direccion))
        {
            return ResultadoPrueba(false, "La direccion BLE no es valida")
        }
        if (!GestorCercania.corriendo)
        {
            return ResultadoPrueba(false, "El radar no esta activo")
        }
        val app = contexto.applicationContext as AplicacionVixxer
        val resultado = runCatching {
            GestorCercania.mensajeria(app).enviarDirectoA(
                macs = listOf(direccion),
                destinatarioId = "diagnostico-campo",
                contenidoCifrado = "A".repeat(1200),
                nonce = "ZGlhZ25vc3RpY28tY2FtcG8=",
                clienteId = "diagnostico-campo",
            )
        }.getOrElse {
            return ResultadoPrueba(false, "Fallo ${it.javaClass.simpleName}")
        }
        val detalle = "entregados=${resultado.entregados} " +
            "enlace=${resultado.enlace ?: "ninguno"} " +
            "reintentos=${resultado.reintentos} " +
            "duracionMs=${resultado.duracionMs}"
        return ResultadoPrueba(resultado.entregados > 0, detalle)
    }

    private data class ResultadoPrueba(
        val ok: Boolean,
        val detalle: String,
    )

    companion object
    {
        const val ACCION = "dev.vixxer.mensajero.PRUEBA_CERCANIA"
        const val EXTRA_DIRECCION = "direccion"
    }
}
