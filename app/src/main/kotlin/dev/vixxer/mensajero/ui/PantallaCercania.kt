package dev.vixxer.mensajero.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.ble.EstadisticasMesh
import dev.vixxer.mensajero.ble.GestorCercania
import dev.vixxer.mensajero.nucleo.ConexionSocket
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private val AZUL = Color(0xFF38BDF8)
private val VERDE = Color(0xFF22C55E)
private val AMBAR = Color(0xFFFFD166)
private const val LADO = 320f

private data class Salida(val texto: String, val detalle: String, val color: Color)

private fun anguloDe(id: String): Double
{
    var h = 0
    for (c in id)
    {
        h = (h * 31 + c.code) % 360
    }
    return h * PI / 180.0
}

private fun radioDe(rssi: Int, maximo: Float): Float
{
    val fuerza = (-rssi - 40).coerceIn(0, 60) / 60f
    return 44f + fuerza * (maximo - 60f)
}

private fun barrasDe(rssi: Int): Int
{
    if (rssi > -60)
    {
        return 3
    }
    if (rssi > -80)
    {
        return 2
    }
    return 1
}

private fun etiquetaPeer(id: String, largo: Int): String =
    id.filter { it.isLetterOrDigit() }.takeLast(largo).uppercase()

@Composable
fun PantallaCercania(app: AplicacionVixxer, alVolver: () -> Unit)
{
    val colores = LocalTema.current.colores
    val contexto = LocalContext.current
    val corriendo = GestorCercania.corriendo
    val peers = GestorCercania.peers
    val soportado = remember { GestorCercania.cercaniaSoportada(contexto) }
    var stats by remember { mutableStateOf(EstadisticasMesh()) }
    var enLinea by remember { mutableStateOf(ConexionSocket.obtener()?.connected() == true) }
    var mensaje by remember { mutableStateOf("") }
    var ofrecerAjustes by remember { mutableStateOf(false) }

    fun encender()
    {
        val r = GestorCercania.activar(app, contexto, true)
        if (!r.ok && r.razon != null)
        {
            mensaje = r.razon
            ofrecerAjustes = r.abrirAjustes
        }
    }

    val lanzadorPermisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { concedidos ->
        if (concedidos.values.all { it })
        {
            encender()
        }
        else
        {
            mensaje = "Falta el permiso de Dispositivos cercanos o Ubicación."
            ofrecerAjustes = true
        }
    }

    fun abrirAjustesApp()
    {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexto.packageName, null))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        contexto.startActivity(intent)
    }

    fun alternar()
    {
        mensaje = ""
        ofrecerAjustes = false
        if (corriendo)
        {
            GestorCercania.activar(app, contexto, false)
            return
        }
        if (!GestorCercania.permisosConcedidos(contexto))
        {
            lanzadorPermisos.launch(GestorCercania.permisos())
            return
        }
        encender()
    }

    LaunchedEffect(Unit) {
        while (true)
        {
            enLinea = ConexionSocket.obtener()?.connected() == true
            stats = if (GestorCercania.corriendo) GestorCercania.mensajeria(app).estadisticas() else EstadisticasMesh()
            delay(2000)
        }
    }

    val salida = when
    {
        enLinea -> Salida("Servidor Vixxer", "Tienes internet: tus mensajes salen directo y actúas de puente para otros.", VERDE)
        corriendo && peers.isNotEmpty() -> Salida("Puente por cercanía", "Sin internet: tus mensajes saltan por Bluetooth hasta un teléfono con conexión.", AZUL)
        corriendo -> Salida("Buscando vixxers cerca…", "Sin internet y sin vixxers al alcance todavía.", AMBAR)
        else -> Salida("Modo cercanía apagado", "Enciéndelo para mensajear sin red.", colores.muted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colores.fondo)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 30.dp),
    )
    {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        )
        {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colores.texto,
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alVolver() },
            )
            Text("Radar de cercanía", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        )
        {
            RadarLienzo(corriendo, salida.color, peers, colores) { alternar() }
            Text(
                if (corriendo) "${peers.size} vixxer${if (peers.size == 1) "" else "s"} cerca" else "radar apagado",
                fontSize = 15.sp,
                fontFamily = FuenteOutfit,
                fontWeight = FontWeight.SemiBold,
                color = colores.texto,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (soportado)
            {
                Text(
                    if (corriendo) "Apagar radar" else "Encender radar",
                    fontSize = 13.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.SemiBold,
                    color = if (corriendo) colores.texto else colores.botonTexto,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .border(1.dp, colores.borde, RoundedCornerShape(18.dp))
                        .background(if (corriendo) Color.Transparent else colores.botonFondo, RoundedCornerShape(18.dp))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alternar() }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
            if (mensaje.isNotEmpty())
            {
                Text(mensaje, fontSize = 12.sp, color = colores.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                if (ofrecerAjustes)
                {
                    Text(
                        "Abrir ajustes",
                        fontSize = 13.sp,
                        fontFamily = FuenteOutfit,
                        fontWeight = FontWeight.SemiBold,
                        color = colores.texto,
                        modifier = Modifier
                            .border(1.dp, colores.borde, RoundedCornerShape(18.dp))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { abrirAjustesApp() }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
        }

        PanelSalida(salida, stats.ultimaRuta, colores)
        FilaStats(stats, colores)
        if (peers.isNotEmpty())
        {
            ListaPeers(peers, colores)
        }
        if (!soportado)
        {
            Text(
                "Este teléfono no trae Bluetooth de baja energía.",
                fontSize = 12.sp,
                color = colores.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            )
        }
    }
}

@Composable
private fun RadarLienzo(corriendo: Boolean, color: Color, peers: List<dev.vixxer.mensajero.ble.PeerCercano>, colores: Paleta, alTocar: () -> Unit)
{
    val transicion = rememberInfiniteTransition(label = "ondas")
    val onda1 by transicion.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = EaseOut), RepeatMode.Restart, StartOffset(0)), label = "o1")
    val onda2 by transicion.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = EaseOut), RepeatMode.Restart, StartOffset(870)), label = "o2")
    val onda3 by transicion.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = EaseOut), RepeatMode.Restart, StartOffset(1740)), label = "o3")
    val centro = LADO / 2f

    Box(modifier = Modifier.size(LADO.dp))
    {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val rmax = size.minDimension / 2f
            for (f in listOf(0.32f, 0.62f, 0.94f))
            {
                drawCircle(colores.borde, rmax * f, c, style = Stroke(1.dp.toPx()))
            }
            drawLine(colores.borde, Offset(c.x, 6f), Offset(c.x, size.height - 6f), 0.5.dp.toPx())
            drawLine(colores.borde, Offset(6f, c.y), Offset(size.width - 6f, c.y), 0.5.dp.toPx())
            if (corriendo)
            {
                for (t in listOf(onda1, onda2, onda3))
                {
                    val rad = (0.16f + t * (1.04f - 0.16f)) * rmax
                    drawCircle(color.copy(alpha = 0.42f * (1f - t)), rad, c, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
        for (p in peers)
        {
            val ang = anguloDe(p.id)
            val r = radioDe(p.rssi, centro - 16f)
            val x = centro + (r * cos(ang)).toFloat()
            val y = centro + (r * sin(ang)).toFloat()
            Box(
                modifier = Modifier
                    .offset((x - 7f).dp, (y - 7f).dp)
                    .size(14.dp)
                    .background(AZUL, CircleShape),
                contentAlignment = Alignment.Center,
            )
            {
                Box(modifier = Modifier.size(6.dp).background(Color.White, CircleShape))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(52.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alTocar() },
            contentAlignment = Alignment.Center,
        )
        {
            LogoPendulo(alto = 44.dp, colorTexto = colores.texto, colorBarra = color)
        }
    }
}

@Composable
private fun PanelSalida(salida: Salida, ultimaRuta: String?, colores: Paleta)
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp)
            .border(1.dp, colores.borde, RoundedCornerShape(14.dp))
            .background(colores.surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    )
    {
        Text("TU SALIDA A INTERNET", fontSize = 11.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp))
        {
            Box(modifier = Modifier.size(10.dp).background(salida.color, CircleShape))
            Text(salida.texto, fontSize = 16.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }
        Text(salida.detalle, fontSize = 12.sp, color = colores.muted)
        if (ultimaRuta != null)
        {
            Text("Último salto: dispositivo ${ultimaRuta.take(8)}…", fontSize = 12.sp, color = colores.muted)
        }
    }
}

@Composable
private fun FilaStats(stats: EstadisticasMesh, colores: Paleta)
{
    val celdas = listOf(
        Pair(stats.enviados, "enviados\npor mesh"),
        Pair(stats.recibidos, "recibidos\npor mesh"),
        Pair(stats.reenviados, "reenviados\npara otros"),
        Pair(stats.puente, "subidos como\npuente"),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    )
    {
        for (celda in celdas)
        {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, colores.borde, RoundedCornerShape(12.dp))
                    .background(colores.surface, RoundedCornerShape(12.dp))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            )
            {
                Text(celda.first.toString(), fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.Bold, color = colores.texto)
                Text(celda.second, fontSize = 9.sp, color = colores.muted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ListaPeers(peers: List<dev.vixxer.mensajero.ble.PeerCercano>, colores: Paleta)
{
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    )
    {
        for (p in peers.take(4))
        {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp))
            {
                Row(
                    modifier = Modifier.width(20.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                )
                {
                    for (b in 1..3)
                    {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height((4 + b * 3).dp)
                                .background(if (b <= barrasDe(p.rssi)) AZUL else colores.borde, RoundedCornerShape(1.dp)),
                        )
                    }
                }
                Text(
                    "Vixxer ${etiquetaPeer(p.id, 6)}",
                    fontSize = 14.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.Medium,
                    color = colores.texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("${p.rssi} dBm", fontSize = 12.sp, color = colores.muted)
            }
        }
    }
}
