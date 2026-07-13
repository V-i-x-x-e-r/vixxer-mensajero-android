package dev.vixxer.mensajero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.ui.EstadoTema
import dev.vixxer.mensajero.ui.FuenteOutfit
import dev.vixxer.mensajero.ui.LocalTema
import dev.vixxer.mensajero.ui.PantallaLogin

class ActividadPrincipal : ComponentActivity()
{
    override fun onCreate(estado: Bundle?)
    {
        super.onCreate(estado)
        enableEdgeToEdge()
        val app = application as AplicacionVixxer
        setContent {
            val oscuroSistema = isSystemInDarkTheme()
            val estadoTema = remember { EstadoTema(app.estado, oscuroSistema) }
            var pantalla by remember { mutableStateOf("login") }
            app.alExpirarSesion = { runOnUiThread { pantalla = "login" } }

            CompositionLocalProvider(LocalTema provides estadoTema) {
                when (pantalla)
                {
                    "login" -> PantallaLogin(app) { pantalla = it }
                    else -> PantallaPendiente(pantalla)
                }
            }
        }
    }
}

@Composable
private fun PantallaPendiente(nombre: String)
{
    val colores = LocalTema.current.colores
    Column(
        modifier = Modifier.fillMaxSize().background(colores.fondo).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    )
    {
        Text(
            nombre.replaceFirstChar { it.uppercase() },
            fontSize = 24.sp,
            fontFamily = FuenteOutfit,
            fontWeight = FontWeight.SemiBold,
            color = colores.texto,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("En construcción · F2", fontSize = 14.sp, color = colores.muted)
    }
}
