package dev.vixxer.mensajero

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import dev.vixxer.mensajero.ui.PantallaBloqueo
import dev.vixxer.mensajero.ui.Seguridad
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.ui.Amigo
import dev.vixxer.mensajero.ui.EstadoTema
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.vixxer.mensajero.ui.BarraPestanas
import dev.vixxer.mensajero.ui.FuenteOutfit
import dev.vixxer.mensajero.ui.LocalHazeState
import dev.vixxer.mensajero.ui.LocalTema
import dev.vixxer.mensajero.ui.fondoDesenfocable
import dev.vixxer.mensajero.ui.recordarHaze
import dev.vixxer.mensajero.ui.PantallaAgregar
import dev.vixxer.mensajero.ui.PantallaAjustes
import dev.vixxer.mensajero.ui.PantallaAmigos
import dev.vixxer.mensajero.ui.PantallaBloqueados
import dev.vixxer.mensajero.ui.PantallaCercania
import dev.vixxer.mensajero.ui.PantallaCambiarContrasena
import dev.vixxer.mensajero.ui.PantallaCrearGrupo
import dev.vixxer.mensajero.ui.PantallaEscaner
import dev.vixxer.mensajero.ui.PantallaSolicitudes
import dev.vixxer.mensajero.ui.PantallaChat
import dev.vixxer.mensajero.ui.PantallaChatGrupo
import dev.vixxer.mensajero.ui.PantallaChats
import dev.vixxer.mensajero.ui.PantallaInfoGrupo
import dev.vixxer.mensajero.ui.PantallaGrupos
import dev.vixxer.mensajero.ui.PantallaLogin
import dev.vixxer.mensajero.ui.PantallaMultimedia
import dev.vixxer.mensajero.ui.PantallaPerfil
import dev.vixxer.mensajero.ui.PantallaRecuperar
import dev.vixxer.mensajero.ui.PantallaRegistro
import dev.vixxer.mensajero.ui.SplashOrbita
import dev.vixxer.mensajero.nucleo.ConexionSocket
import dev.vixxer.mensajero.llamadas.EscuchaLlamadas
import dev.vixxer.mensajero.llamadas.GestorLlamadas
import dev.vixxer.mensajero.ui.PantallaLlamada
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActividadPrincipal : FragmentActivity()
{
    @androidx.camera.core.ExperimentalGetImage
    override fun onCreate(estado: Bundle?)
    {
        super.onCreate(estado)
        enableEdgeToEdge()
        val app = application as AplicacionVixxer
        GestorLlamadas.preparar(app)
        if (Seguridad.capturasBloqueadas(app.estado))
        {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        setContent {
            val oscuroSistema = isSystemInDarkTheme()
            val estadoTema = remember { EstadoTema(app.estado, oscuroSistema) }
            val haze = recordarHaze()
            var pantalla by remember { mutableStateOf("arranque") }
            var chatAbierto by remember { mutableStateOf<Amigo?>(null) }
            var origenEscaner by remember { mutableStateOf("agregar") }
            var codigoEscaneado by remember { mutableStateOf<String?>(null) }
            var bloqueado by remember { mutableStateOf(false) }
            var sesionVerificada by remember { mutableStateOf(false) }
            var animacionSplashLista by remember { mutableStateOf(false) }
            var socketMensajeria by remember { mutableStateOf<Socket?>(null) }
            var cuentaMensajeria by remember { mutableStateOf("") }
            val alcanceMensajeria = rememberCoroutineScope()
            app.alExpirarSesion = { runOnUiThread { pantalla = "login" } }

            LaunchedEffect(Unit) {
                val estadoSesion = withContext(Dispatchers.IO) {
                    Triple(
                        app.boveda.leer(ClavesSeguras.TOKEN),
                        runCatching { app.identidad.tieneRespaldoPendiente() }.getOrElse { true },
                        runCatching { app.identidad.codigoPendiente() != null }.getOrDefault(false),
                    )
                }
                val token = estadoSesion.first
                if (token != null && estadoSesion.second)
                {
                    withContext(Dispatchers.IO) { app.cerrarSesionLocal() }
                    pantalla = "login"
                    sesionVerificada = true
                    return@LaunchedEffect
                }
                if (pantalla == "arranque")
                {
                    pantalla = when
                    {
                        token == null -> "login"
                        estadoSesion.third -> "recuperar"
                        else -> "chats"
                    }
                }
                if (Seguridad.candadoHabilitado(app.boveda, app.estado))
                {
                    bloqueado = true
                }
                sesionVerificada = true
            }

            LaunchedEffect(pantalla, sesionVerificada) {
                if (!sesionVerificada)
                {
                    return@LaunchedEffect
                }
                val sesion = withContext(Dispatchers.IO) {
                    Pair(
                        app.boveda.leer(ClavesSeguras.TOKEN),
                        app.boveda.leer(ClavesSeguras.MI_ID),
                    )
                }
                val token = sesion.first
                val cuentaId = sesion.second
                if (token == null || cuentaId.isNullOrBlank())
                {
                    socketMensajeria = null
                    cuentaMensajeria = ""
                    return@LaunchedEffect
                }
                val socket = withContext(Dispatchers.IO) {
                    ConexionSocket.conectar(Config.SOCKET_URL, token)
                }
                socketMensajeria = socket
                cuentaMensajeria = cuentaId
                DrenadorOutbox.drenar(app, cuentaId)
            }

            DisposableEffect(socketMensajeria, cuentaMensajeria) {
                val socket = socketMensajeria
                val cuentaId = cuentaMensajeria
                val alConectar = Emitter.Listener {
                    if (cuentaId.isNotBlank())
                    {
                        alcanceMensajeria.launch {
                            DrenadorOutbox.drenar(app, cuentaId, forzar = true)
                        }
                    }
                }
                socket?.on(Socket.EVENT_CONNECT, alConectar)
                onDispose { socket?.off(Socket.EVENT_CONNECT, alConectar) }
            }

            DisposableEffect(socketMensajeria) {
                val socket = socketMensajeria
                if (socket != null)
                {
                    EscuchaLlamadas.enganchar(socket) {
                        runOnUiThread { pantalla = "llamada" }
                    }
                }
                onDispose { }
            }

            LaunchedEffect(socketMensajeria, cuentaMensajeria) {
                val cuentaId = cuentaMensajeria
                if (cuentaId.isBlank())
                {
                    return@LaunchedEffect
                }
                while (true)
                {
                    DrenadorOutbox.drenar(app, cuentaId)
                    delay(15_000L)
                }
            }

            DisposableEffect(Unit) {
                val observador = LifecycleEventObserver { _, evento ->
                    if (evento == Lifecycle.Event.ON_STOP)
                    {
                        if (app.saltarBloqueo)
                        {
                            app.saltarBloqueo = false
                        }
                        else if (Seguridad.candadoHabilitado(app.boveda, app.estado))
                        {
                            bloqueado = true
                        }
                    }
                }
                lifecycle.addObserver(observador)
                onDispose { lifecycle.removeObserver(observador) }
            }

            CompositionLocalProvider(LocalTema provides estadoTema, LocalHazeState provides haze) {
                val esPestana = pantalla == "amigos" || pantalla == "chats" || pantalla == "grupos"
                BackHandler(enabled = pantalla == "registro") { pantalla = "login" }
                BackHandler(enabled = pantalla == "chat" || pantalla == "ajustes") { pantalla = "chats" }
                BackHandler(enabled = pantalla == "perfil") { pantalla = "chat" }
                BackHandler(enabled = esPestana && pantalla != "chats") { pantalla = "chats" }
                BackHandler(enabled = pantalla == "agregar" || pantalla == "solicitudes") { pantalla = "amigos" }
                BackHandler(enabled = pantalla == "escaner") { pantalla = origenEscaner }
                BackHandler(enabled = pantalla == "bloqueados" || pantalla == "cambiar-contrasena") { pantalla = "ajustes" }
                BackHandler(enabled = pantalla == "grupo-crear") { pantalla = "grupos" }
                BackHandler(enabled = pantalla.startsWith("grupo/")) { pantalla = "grupos" }
                BackHandler(enabled = pantalla.startsWith("grupo-info/")) { pantalla = "grupo/${pantalla.removePrefix("grupo-info/")}" }
                BackHandler(enabled = pantalla.startsWith("multimedia/")) { pantalla = "perfil" }
                BackHandler(enabled = pantalla == "llamada" || pantalla.startsWith("llamada/")) { GestorLlamadas.colgar() }
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = pantalla,
                        modifier = Modifier.fondoDesenfocable(haze),
                        transitionSpec = {
                            (fadeIn(tween(180)) + slideInVertically(tween(180)) { alto -> alto / 24 })
                                .togetherWith(fadeOut(tween(120)))
                        },
                        label = "pantallas",
                    ) { destino ->
                    when (destino)
                    {
                        "arranque" -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(LocalTema.current.colores.fondo),
                        )
                        "login" -> PantallaLogin(app) { pantalla = it }
                        "registro" -> PantallaRegistro(app) { pantalla = it }
                        "recuperar" -> PantallaRecuperar(
                            app,
                            codigoLeido = if (origenEscaner == "recuperar") codigoEscaneado else null,
                            alEscanear = {
                                origenEscaner = "recuperar"
                                codigoEscaneado = null
                                pantalla = "escaner"
                            },
                            alNavegar = { pantalla = it },
                        )
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
                        "agregar" -> PantallaAgregar(
                            app,
                            codigoLeido = if (origenEscaner == "agregar") codigoEscaneado else null,
                            alEscanear = {
                                origenEscaner = "agregar"
                                codigoEscaneado = null
                                pantalla = "escaner"
                            },
                            alVolver = { pantalla = "amigos" },
                        )
                        "escaner" -> PantallaEscaner(
                            app,
                            alLeer = { valor ->
                                codigoEscaneado = valor
                                pantalla = origenEscaner
                            },
                            alCerrar = { pantalla = origenEscaner },
                        )
                        "solicitudes" -> PantallaSolicitudes(app) { pantalla = "amigos" }
                        "bloqueados" -> PantallaBloqueados(app) { pantalla = "ajustes" }
                        "cercania" -> PantallaCercania(app) { pantalla = "ajustes" }
                        "cambiar-contrasena" -> PantallaCambiarContrasena(app) { pantalla = "ajustes" }
                        "grupo-crear" -> PantallaCrearGrupo(app) { pantalla = "grupos" }
                        "perfil" ->
                        {
                            val amigo = chatAbierto
                            if (amigo != null)
                            {
                                PantallaPerfil(app, amigo) { pantalla = it }
                            }
                        }
                        "chat" ->
                        {
                            val amigo = chatAbierto
                            if (amigo != null)
                            {
                                PantallaChat(app, amigo, alNavegar = { pantalla = it }) { pantalla = "chats" }
                            }
                            else
                            {
                                pantalla = "chats"
                            }
                        }
                        "llamada" -> PantallaLlamada(app, "llamada") { pantalla = "chats" }
                        else -> when
                        {
                            destino.startsWith("grupo/") -> PantallaChatGrupo(app, destino.removePrefix("grupo/"), "") { pantalla = it }
                            destino.startsWith("grupo-info/") -> PantallaInfoGrupo(app, destino.removePrefix("grupo-info/")) { pantalla = it }
                            destino.startsWith("multimedia/") -> PantallaMultimedia(app, destino.removePrefix("multimedia/")) { pantalla = "perfil" }
                            destino.startsWith("llamada/") -> PantallaLlamada(app, destino) { pantalla = "chat" }
                            else -> PantallaPendiente(destino)
                        }
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
                    if (bloqueado)
                    {
                        PantallaBloqueo(app) { bloqueado = false }
                    }
                    if (!animacionSplashLista)
                    {
                        SplashOrbita(fondo = estadoTema.colores.fondo, listoParaSalir = sesionVerificada) { animacionSplashLista = true }
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
