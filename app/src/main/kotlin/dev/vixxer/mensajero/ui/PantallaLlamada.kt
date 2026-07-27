package dev.vixxer.mensajero.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.llamadas.EstadoLlamada
import dev.vixxer.mensajero.llamadas.FaseLlamada
import dev.vixxer.mensajero.llamadas.GestorLlamadas
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private data class DestinoLlamada(
    val id: String,
    val usuario: String,
    val video: Boolean,
    val entrante: Boolean,
)

private fun leerDestino(ruta: String): DestinoLlamada
{
    val partes = ruta.removePrefix("llamada/").split("|")
    return DestinoLlamada(
        id = partes.getOrNull(0) ?: "",
        usuario = partes.getOrNull(1) ?: "",
        video = partes.getOrNull(2) == "1",
        entrante = partes.getOrNull(3) == "1",
    )
}

private fun duracionTexto(segundos: Int): String
{
    val m = segundos / 60
    val s = segundos % 60
    return "%02d:%02d".format(m, s)
}

@Composable
fun PantallaLlamada(app: AplicacionVixxer, ruta: String, alVolver: () -> Unit)
{
    val destino = remember(ruta) { leerDestino(ruta) }
    val contexto = LocalContext.current
    var llamada by remember { mutableStateOf(GestorLlamadas.estadoLlamada()) }
    var silencio by remember { mutableStateOf(false) }
    var altavoz by remember { mutableStateOf(GestorLlamadas.altavozActivo()) }
    var camaraApagada by remember { mutableStateOf(false) }
    var segundos by remember { mutableStateOf(0) }
    var permisos by remember { mutableStateOf(false) }

    val solicitud = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { concedidos ->
        val micOk = concedidos[Manifest.permission.RECORD_AUDIO] == true
        val camOk = !destino.video || concedidos[Manifest.permission.CAMERA] == true
        if (micOk && camOk)
        {
            permisos = true
        }
        else
        {
            alVolver()
        }
    }

    DisposableEffect(Unit) {
        val quitar = GestorLlamadas.alLlamada { llamada = it }
        onDispose { quitar() }
    }

    LaunchedEffect(Unit) {
        if (!GestorLlamadas.llamadasDisponibles())
        {
            alVolver()
            return@LaunchedEffect
        }
        val faltantes = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
        {
            faltantes.add(Manifest.permission.RECORD_AUDIO)
        }
        if (destino.video && ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        {
            faltantes.add(Manifest.permission.CAMERA)
        }
        app.saltarBloqueo = true
        if (faltantes.isEmpty())
        {
            permisos = true
        }
        else
        {
            solicitud.launch(faltantes.toTypedArray())
        }
    }

    LaunchedEffect(permisos) {
        if (permisos && !destino.entrante && destino.id.isNotBlank() &&
            GestorLlamadas.estadoLlamada().fase == FaseLlamada.LIBRE)
        {
            GestorLlamadas.iniciarLlamada(destino.id, destino.usuario, destino.video)
        }
    }

    LaunchedEffect(llamada.fase) {
        if (llamada.fase == FaseLlamada.ACTIVA)
        {
            while (true)
            {
                delay(1000)
                segundos += 1
            }
        }
        else
        {
            segundos = 0
        }
    }

    LaunchedEffect(llamada.fase) {
        if (llamada.fase == FaseLlamada.LIBRE && permisos)
        {
            alVolver()
        }
    }

    val nombre = llamada.nombre.ifBlank { destino.usuario }
    val esEntrante = llamada.fase == FaseLlamada.ENTRANTE
    val conVideo = llamada.video
    val etiqueta = when
    {
        llamada.fase == FaseLlamada.LLAMANDO -> "Llamando…"
        esEntrante -> if (conVideo) "Videollamada entrante" else "Llamada entrante"
        else -> duracionTexto(segundos)
    }
    val insetsSuperior = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val insetsInferior = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
        val remoto = llamada.remoto
        if (conVideo && remoto != null)
        {
            VistaVideo(
                pista = remoto,
                espejo = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        else
        {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            )
            {
                Avatar(nombre = nombre, tamano = 120.dp)
                if (conVideo && llamada.fase == FaseLlamada.ACTIVA)
                {
                    Column(modifier = Modifier.height(20.dp)) {}
                    Text("Esperando su video…", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
        }

        val local = llamada.local
        if (conVideo && local != null && !camaraApagada)
        {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = insetsSuperior + 16.dp, end = 16.dp)
                    .size(width = 108.dp, height = 160.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
            )
            {
                VistaVideo(
                    pista = local,
                    espejo = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = insetsSuperior + 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        )
        {
            Text(nombre, color = Color.White, fontSize = 24.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold)
            Text(etiqueta, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = insetsInferior + 28.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        )
        {
            if (esEntrante)
            {
                BotonLlamada(fondo = Color(0xFFE5484D), alPulsar = { GestorLlamadas.colgar() }) {
                    Telefono(color = Color.White, tamano = 26.dp)
                }
                BotonLlamada(fondo = Color(0xFF22C55E), alPulsar = { GestorLlamadas.contestar() }) {
                    if (conVideo)
                    {
                        IconoVideo(color = Color.White, tamano = 26.dp)
                    }
                    else
                    {
                        Telefono(color = Color.White, tamano = 26.dp)
                    }
                }
            }
            else
            {
                BotonLlamada(claro = silencio, alPulsar = { silencio = GestorLlamadas.alternarSilencio() }) {
                    Microfono(color = if (silencio) Color(0xFF0B0B0F) else Color.White, tamano = 22.dp)
                }
                BotonLlamada(claro = altavoz, alPulsar = { altavoz = GestorLlamadas.alternarAltavoz() }) {
                    Bocina(color = if (altavoz) Color(0xFF0B0B0F) else Color.White, tamano = 22.dp)
                }
                if (conVideo)
                {
                    BotonLlamada(claro = camaraApagada, alPulsar = { camaraApagada = GestorLlamadas.alternarCamara() }) {
                        IconoVideo(color = if (camaraApagada) Color(0xFF0B0B0F) else Color.White, tamano = 22.dp)
                    }
                    BotonLlamada(alPulsar = { GestorLlamadas.cambiarCamara() }) {
                        Text("↺", color = Color.White, fontSize = 26.sp)
                    }
                }
                BotonLlamada(fondo = Color(0xFFE5484D), alPulsar = { GestorLlamadas.colgar() }) {
                    Telefono(color = Color.White, tamano = 26.dp)
                }
            }
        }
    }
}

@Composable
private fun BotonLlamada(
    fondo: Color? = null,
    claro: Boolean = false,
    alPulsar: () -> Unit,
    contenido: @Composable () -> Unit,
)
{
    val color = fondo ?: if (claro) Color.White else Color.White.copy(alpha = 0.18f)
    Box(
        modifier = Modifier
            .pulsable { alPulsar() }
            .size(62.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    )
    {
        contenido()
    }
}

@Composable
private fun VistaVideo(pista: VideoTrack, espejo: Boolean, modifier: Modifier)
{
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(GestorLlamadas.eglBase.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(espejo)
                setEnableHardwareScaler(true)
                pista.addSink(this)
            }
        },
        onRelease = { vista ->
            pista.removeSink(vista)
            vista.release()
        },
    )
}
