package dev.vixxer.mensajero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.nucleo.Cripto

class ActividadPrincipal : ComponentActivity()
{
    override fun onCreate(estado: Bundle?)
    {
        super.onCreate(estado)
        val diagnostico = probarNucleo()
        setContent {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0C1015)).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Vixxer", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Cliente nativo · F1", color = Color(0xFF8A93A6), fontSize = 16.sp)
                Spacer(Modifier.height(32.dp))
                Text(diagnostico, color = Color(0xFF7ED9A0), fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    }

    private fun probarNucleo(): String
    {
        return try
        {
            val clave = ByteArray(32) { (it + 1).toByte() }
            val nonce = ByteArray(24) { (it + 50).toByte() }
            val mensaje = "vixxer nativo".toByteArray()
            val abierto = Cripto.abrir(Cripto.sellar(mensaje, nonce, clave), nonce, clave)
            if (abierto != null && abierto.contentEquals(mensaje))
            {
                "libsodium OK: secretbox ida y vuelta en este dispositivo"
            }
            else
            {
                "libsodium FALLO: no abrio el sellado"
            }
        }
        catch (e: Throwable)
        {
            "libsodium FALLO: ${e.message}"
        }
    }
}
