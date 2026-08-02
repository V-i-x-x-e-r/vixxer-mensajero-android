package dev.vixxer.mensajero.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.ble.EstadisticasMesh
import dev.vixxer.mensajero.ble.GestorCercania
import dev.vixxer.mensajero.ble.PeerCercano
import dev.vixxer.mensajero.nucleo.ConexionSocket
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AZUL = Color(0xFF38BDF8)
private val VERDE = Color(0xFF22C55E)
private val AMBAR = Color(0xFFFFD166)
private const val LADO = 320f
private val LADO_MARCA_RADAR = 112.dp

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
    val alcance = rememberCoroutineScope()
    val corriendo = GestorCercania.corriendo
    val peers = GestorCercania.peers
    val soportado = remember { GestorCercania.cercaniaSoportada(contexto) }
    var stats by remember { mutableStateOf(EstadisticasMesh()) }
    var enLinea by remember { mutableStateOf(ConexionSocket.obtener()?.connected() == true) }
    var diagnostico by remember { mutableStateOf(EstadoDiagnosticoCampo.vacio()) }
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

    fun exportarDiagnostico()
    {
        alcance.launch {
            val resultado = withContext(Dispatchers.IO)
            {
                runCatching {
                    val actual = leerDiagnosticoCampo(app)
                    Pair(actual, crearArchivoDiagnostico(contexto, app, actual))
                }
            }
            resultado.onSuccess { exportado ->
                diagnostico = exportado.first
                compartirDiagnostico(contexto, exportado.second)
            }
            resultado.onFailure {
                android.widget.Toast.makeText(
                    contexto,
                    "No se pudo exportar el diagnóstico.",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun limpiarDiagnostico()
    {
        alcance.launch {
            diagnostico = withContext(Dispatchers.IO)
            {
                app.diagnosticoMesh.limpiar()
                leerDiagnosticoCampo(app)
            }
        }
    }

    LaunchedEffect(Unit)
    {
        while (true)
        {
            enLinea = ConexionSocket.obtener()?.connected() == true
            stats = if (GestorCercania.corriendo) GestorCercania.mensajeria(app).estadisticas() else EstadisticasMesh()
            diagnostico = withContext(Dispatchers.IO)
            {
                leerDiagnosticoCampo(app)
            }
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
    val ultimaRutaVisible = stats.ultimaRuta?.takeIf { ruta -> peers.any { it.id == ruta } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fondoVixxer()
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
                modifier = Modifier.pulsable { alVolver() },
            )
            Text("Radar de cercanía", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        )
        {
            RadarLienzo(corriendo, salida.color, peers, colores) { alternar() }
            val amigosCerca = peers.count { it.amigoId != null }
            Text(
                when
                {
                    !corriendo -> "radar apagado"
                    amigosCerca > 0 -> "${peers.size} vixxer${if (peers.size == 1) "" else "s"} cerca · $amigosCerca amigo${if (amigosCerca == 1) "" else "s"}"
                    else -> "${peers.size} vixxer${if (peers.size == 1) "" else "s"} cerca"
                },
                fontSize = 15.sp,
                fontFamily = FuenteOutfit,
                fontWeight = FontWeight.SemiBold,
                color = colores.texto,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (soportado)
            {
                val superficieControl = if (corriendo)
                {
                    Modifier.panelVidrio(radio = 18.dp, desenfocar = true)
                }
                else
                {
                    Modifier.background(colores.botonFondo, RoundedCornerShape(18.dp))
                }
                Box(modifier = Modifier.padding(top = 10.dp))
                {
                    Text(
                        if (corriendo) "Apagar radar" else "Encender radar",
                        fontSize = 13.sp,
                        fontFamily = FuenteOutfit,
                        fontWeight = FontWeight.SemiBold,
                        color = if (corriendo) colores.texto else colores.botonTexto,
                        modifier = Modifier
                            .pulsable { alternar() }
                            .then(superficieControl)
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
            val aviso = mensaje.ifEmpty { GestorCercania.avisoEscaneo.orEmpty() }
            if (aviso.isNotEmpty())
            {
                Text(aviso, fontSize = 12.sp, color = colores.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                if (ofrecerAjustes)
                {
                    Text(
                        "Abrir ajustes",
                        fontSize = 13.sp,
                        fontFamily = FuenteOutfit,
                        fontWeight = FontWeight.SemiBold,
                        color = colores.texto,
                        modifier = Modifier
                            .pulsable { abrirAjustesApp() }
                            .border(1.dp, colores.borde, RoundedCornerShape(18.dp))
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
        }

        PanelSalida(salida, ultimaRutaVisible, colores)
        FilaStats(stats, colores)
        PanelDiagnosticoCampo(
            estado = diagnostico,
            alCompartir = { exportarDiagnostico() },
            alLimpiar = { limpiarDiagnostico() },
        )
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
internal fun RadarLienzo(
    corriendo: Boolean,
    color: Color,
    peers: List<PeerCercano>,
    colores: Paleta,
    modifier: Modifier = Modifier,
    alTocar: () -> Unit,
)
{
    val centro = LADO / 2f

    Box(modifier = modifier.size(LADO.dp))
    {
        FondoRadar(colores)
        if (corriendo)
        {
            OndasRadar(color)
        }
        for (peer in peers)
        {
            key(peer.id)
            {
                NodoRadar(peer, centro)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(LADO_MARCA_RADAR)
                .pulsable { alTocar() }
                .semantics
                {
                    contentDescription = if (corriendo) "Detener radar de cercanía" else "Iniciar radar de cercanía"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        )
        {
            MarcaOrbital(lado = LADO_MARCA_RADAR)
        }
    }
}

@Composable
private fun FondoRadar(colores: Paleta)
{
    Canvas(modifier = Modifier.fillMaxSize())
    {
        val centro = Offset(size.width / 2f, size.height / 2f)
        val radioMaximo = size.minDimension / 2f
        for (factor in listOf(0.32f, 0.62f, 0.94f))
        {
            drawCircle(colores.borde, radioMaximo * factor, centro, style = Stroke(1.dp.toPx()))
        }
        drawLine(colores.borde, Offset(centro.x, 6f), Offset(centro.x, size.height - 6f), 0.5.dp.toPx())
        drawLine(colores.borde, Offset(6f, centro.y), Offset(size.width - 6f, centro.y), 0.5.dp.toPx())
    }
}

@Composable
private fun OndasRadar(color: Color)
{
    val transicion = rememberInfiniteTransition(label = "ondasRadar")
    val onda1 by transicion.animateFloat(0f, 1f, animacionOnda(0), label = "ondaRadar1")
    val onda2 by transicion.animateFloat(0f, 1f, animacionOnda(870), label = "ondaRadar2")
    val onda3 by transicion.animateFloat(0f, 1f, animacionOnda(1740), label = "ondaRadar3")

    Canvas(modifier = Modifier.fillMaxSize())
    {
        val centro = Offset(size.width / 2f, size.height / 2f)
        val radioMaximo = size.minDimension / 2f
        for (avance in listOf(onda1, onda2, onda3))
        {
            val radio = (0.16f + avance * 0.88f) * radioMaximo
            drawCircle(
                color.copy(alpha = 0.42f * (1f - avance)),
                radio,
                centro,
                style = Stroke(1.5.dp.toPx()),
            )
        }
    }
}

private fun animacionOnda(retraso: Int) = infiniteRepeatable<Float>(
    animation = tween(2600, easing = EaseOut),
    repeatMode = RepeatMode.Restart,
    initialStartOffset = StartOffset(retraso),
)

@Composable
private fun NodoRadar(peer: PeerCercano, centro: Float)
{
    val angulo = anguloDe(peer.id)
    val radio = radioDe(peer.rssi, centro - 18f)
    val destinoX = centro + (radio * cos(angulo)).toFloat() - 9f
    val destinoY = centro + (radio * sin(angulo)).toFloat() - 9f
    val x by animateDpAsState(
        targetValue = destinoX.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "nodoRadarX",
    )
    val y by animateDpAsState(
        targetValue = destinoY.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "nodoRadarY",
    )
    val color = if (peer.amigoId != null) VERDE else AZUL

    Box(
        modifier = Modifier
            .offset(x, y)
            .size(18.dp)
            .background(color.copy(alpha = 0.18f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.82f), CircleShape),
        contentAlignment = Alignment.Center,
    )
    {
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
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
            .panelVidrio(radio = 14.dp, desenfocar = true)
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
        Pair(stats.enviados, "Enviados"),
        Pair(stats.recibidos, "Recibidos"),
        Pair(stats.reenviados, "Reenvíos"),
        Pair(stats.puente, "Puentes"),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
            .panelVidrio(radio = 12.dp),
    )
    {
        for ((indice, celda) in celdas.withIndex())
        {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            )
            {
                Text(celda.first.toString(), fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.Bold, color = colores.texto)
                Text(celda.second, fontSize = 9.sp, color = colores.muted, textAlign = TextAlign.Center)
            }
            if (indice < celdas.lastIndex)
            {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(width = 1.dp, height = 34.dp)
                        .background(colores.borde),
                )
            }
        }
    }
}

@Composable
private fun ListaPeers(peers: List<PeerCercano>, colores: Paleta)
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
                    p.nombre?.takeIf { it.isNotEmpty() } ?: "Nodo Vixxer · ${etiquetaPeer(p.id, 6)}",
                    fontSize = 14.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.Medium,
                    color = colores.texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (p.amigoId != null)
                {
                    Text("amigo", fontSize = 11.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = VERDE)
                }
                Text("${p.rssi} dBm", fontSize = 12.sp, color = colores.muted)
            }
        }
    }
}
