package dev.vixxer.mensajero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import dev.vixxer.mensajero.ui.Amigo
import dev.vixxer.mensajero.ui.EstadoTema
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.vixxer.mensajero.ui.BarraPestanas
import dev.vixxer.mensajero.ui.FuenteOutfit
import dev.vixxer.mensajero.ui.LocalTema
import dev.vixxer.mensajero.ui.PantallaAgregar
import dev.vixxer.mensajero.ui.PantallaAjustes
import dev.vixxer.mensajero.ui.PantallaAmigos
import dev.vixxer.mensajero.ui.PantallaBloqueados
import dev.vixxer.mensajero.ui.PantallaCambiarContrasena
import dev.vixxer.mensajero.ui.PantallaCrearGrupo
import dev.vixxer.mensajero.ui.PantallaSolicitudes
import dev.vixxer.mensajero.ui.PantallaChat
import dev.vixxer.mensajero.ui.PantallaChatGrupo
import dev.vixxer.mensajero.ui.PantallaChats
import dev.vixxer.mensajero.ui.PantallaInfoGrupo
import dev.vixxer.mensajero.ui.PantallaGrupos
import dev.vixxer.mensajero.ui.PantallaLogin
import dev.vixxer.mensajero.ui.PantallaRecuperar
import dev.vixxer.mensajero.ui.PantallaRegistro

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
            var pantalla by remember { mutableStateOf("arranque") }
            var chatAbierto by remember { mutableStateOf<Amigo?>(null) }
            app.alExpirarSesion = { runOnUiThread { pantalla = "login" } }

            LaunchedEffect(Unit) {
                val token = withContext(Dispatchers.IO) { app.boveda.leer(ClavesSeguras.TOKEN) }
                if (pantalla == "arranque")
                {
                    pantalla = if (token != null) "chats" else "login"
                }
            }

            CompositionLocalProvider(LocalTema provides estadoTema) {
                val esPestana = pantalla == "amigos" || pantalla == "chats" || pantalla == "grupos"
                BackHandler(enabled = pantalla == "registro") { pantalla = "login" }
                BackHandler(enabled = pantalla == "chat" || pantalla == "ajustes") { pantalla = "chats" }
                BackHandler(enabled = esPestana && pantalla != "chats") { pantalla = "chats" }
                BackHandler(enabled = pantalla == "agregar" || pantalla == "solicitudes") { pantalla = "amigos" }
                BackHandler(enabled = pantalla == "bloqueados" || pantalla == "cambiar-contrasena") { pantalla = "ajustes" }
                BackHandler(enabled = pantalla == "grupo-crear") { pantalla = "grupos" }
                BackHandler(enabled = pantalla.startsWith("grupo/")) { pantalla = "grupos" }
                BackHandler(enabled = pantalla.startsWith("grupo-info/")) { pantalla = "grupo/${pantalla.removePrefix("grupo-info/")}" }
                Box(modifier = Modifier.fillMaxSize()) {
                    when (pantalla)
                    {
                        "arranque" -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(LocalTema.current.colores.fondo),
                        )
                        "login" -> PantallaLogin(app) { pantalla = it }
                        "registro" -> PantallaRegistro(app) { pantalla = it }
                        "recuperar" -> PantallaRecuperar(app) { pantalla = it }
                        "amigos" -> PantallaAmigos(
                            app,
                            alNavegar = { pantalla = it },
                            alAbrirChat = { amigo ->
                                chatAbierto = amigo
                                pantalla = "chat"
                            },
                        )
                        "chats" -> PantallaChats(
                            app,
                            alNavegar = { pantalla = it },
                            alAbrirChat = { amigo ->
                                chatAbierto = amigo
                                pantalla = "chat"
                            },
                        )
                        "grupos" -> PantallaGrupos(app) { pantalla = it }
                        "ajustes" -> PantallaAjustes(app) { pantalla = it }
                        "agregar" -> PantallaAgregar(app) { pantalla = "amigos" }
                        "solicitudes" -> PantallaSolicitudes(app) { pantalla = "amigos" }
                        "bloqueados" -> PantallaBloqueados(app) { pantalla = "ajustes" }
                        "cambiar-contrasena" -> PantallaCambiarContrasena(app) { pantalla = "ajustes" }
                        "grupo-crear" -> PantallaCrearGrupo(app) { pantalla = "grupos" }
                        "chat" ->
                        {
                            val amigo = chatAbierto
                            if (amigo != null)
                            {
                                PantallaChat(app, amigo) { pantalla = "chats" }
                            }
                            else
                            {
                                pantalla = "chats"
                            }
                        }
                        else -> when
                        {
                            pantalla.startsWith("grupo/") -> PantallaChatGrupo(app, pantalla.removePrefix("grupo/"), "") { pantalla = it }
                            pantalla.startsWith("grupo-info/") -> PantallaInfoGrupo(app, pantalla.removePrefix("grupo-info/")) { pantalla = it }
                            else -> PantallaPendiente(pantalla)
                        }
                    }
                    if (esPestana)
                    {
                        BarraPestanas(
                            actual = pantalla,
                            alCambiar = { pantalla = it },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
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
