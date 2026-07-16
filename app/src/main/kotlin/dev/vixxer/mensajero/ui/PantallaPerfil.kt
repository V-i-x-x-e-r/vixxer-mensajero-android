package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.Cripto
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun PantallaPerfil(app: AplicacionVixxer, amigo: Amigo, alNavegar: (String) -> Unit)
{
    val colores = LocalTema.current.colores
    val alcance = rememberCoroutineScope()
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var alias by remember { mutableStateOf<String?>(null) }
    var presencia by remember { mutableStateOf<JSONObject?>(null) }
    var seguridad by remember { mutableStateOf<String?>(null) }
    var refrescando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf(false) }
    var borradorAlias by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf<String?>(null) }
    var media by remember { mutableStateOf(listOf<MediaMensaje>()) }

    fun calcularSeguridad(forzar: Boolean)
    {
        alcance.launch {
            refrescando = true
            val numero = withContext(Dispatchers.IO) {
                runCatching {
                    val mia = app.boveda.leer(ClavesSeguras.CLAVE_PUBLICA) ?: return@runCatching null
                    val suya = app.llaves.llavePublicaDe(amigo.id, forzar)
                    Cripto.numeroSeguridad(mia, suya)
                }.getOrNull()
            }
            refrescando = false
            if (numero != null)
            {
                seguridad = numero
            }
        }
    }

    LaunchedEffect(amigo.id) {
        withContext(Dispatchers.IO) {
            alias = app.aliasLocal.de(amigo.id)
            runCatching { presencia = app.api.presencia(amigo.id) as? JSONObject }
        }
        calcularSeguridad(false)
        withContext(Dispatchers.IO) {
            runCatching {
                val filas = app.api.historial(amigo.id) as JSONArray
                val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: return@runCatching
                val pub = app.llaves.llavePublicaDe(amigo.id)
                val fotos = ArrayList<MediaMensaje>()
                for (i in filas.length() - 1 downTo 0)
                {
                    val f = filas.getJSONObject(i)
                    if (f.optString("contenido_cifrado") == "BORRADO")
                    {
                        continue
                    }
                    val claro = Cripto.descifrarTexto(f.getString("contenido_cifrado"), f.getString("nonce"), pub, priv)
                    val m = leerMedia(claro)
                    if (m != null && m.t == "img")
                    {
                        fotos.add(m)
                    }
                }
                media = fotos
            }
        }
    }

    val nombre = alias ?: amigo.usuario
    val enLinea = presencia?.optBoolean("en_linea") == true
    val sub = when
    {
        enLinea -> "en línea"
        presencia?.isNull("ultima_conexion") == false ->
            "últ. vez ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date.from(dev.vixxer.mensajero.nucleo.Fechas.aInstante(presencia?.optString("ultima_conexion"))))}"
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colores.fondo)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    )
    {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colores.texto,
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alNavegar("chat") },
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        )
        {
            Avatar(nombre = nombre, uri = amigo.avatarUrl.ifEmpty { null }, tamano = 108.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    borradorAlias = alias ?: ""
                    editando = true
                },
            )
            {
                Text(nombre, fontSize = 22.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
                Lapiz(color = colores.muted, tamano = 16.dp)
            }
            if (alias != null)
            {
                Text("@${amigo.usuario}", fontSize = 13.sp, color = colores.muted)
            }
            if (sub != null)
            {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (enLinea)
                    {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF22C55E), CircleShape))
                    }
                    Text(sub, fontSize = 13.sp, color = colores.muted)
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                Text(
                    "MULTIMEDIA",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = colores.muted,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
                if (media.isNotEmpty())
                {
                    Text(
                        "Ver todo ›",
                        fontSize = 13.sp,
                        color = colores.enlace,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alNavegar("multimedia/${amigo.id}") },
                    )
                }
            }
            if (media.isNotEmpty())
            {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(media, key = { it.path }) { m ->
                        var archivo by remember(m.path) { mutableStateOf<java.io.File?>(null) }
                        LaunchedEffect(m.path) {
                            archivo = withContext(Dispatchers.IO) { CacheMedia.obtener(contexto, app, m) }
                        }
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colores.surface),
                        )
                        {
                            if (archivo != null)
                            {
                                AsyncImage(
                                    model = archivo,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
            else
            {
                Text("Fotos y videos de esta conversación.", fontSize = 13.sp, color = colores.muted)
            }

            Seccion("CONVERSACIÓN", colores)
            Column(modifier = Modifier.fillMaxWidth().panelVidrio()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { confirmar = "borrar" }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    Text("Borrar conversación", fontSize = 15.sp, color = colores.texto)
                    Bote(color = colores.muted, tamano = 18.dp)
                }
            }

            val numero = seguridad
            if (numero != null)
            {
                Seccion("SEGURIDAD", colores)
                Box(modifier = Modifier.fillMaxWidth().panelVidrio().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        numero,
                        fontSize = 15.sp,
                        fontFamily = FuenteOutfit,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        color = colores.texto,
                    )
                }
                Text(
                    "Verifícalo con tu contacto en persona o por llamada. Si coinciden, nadie más puede leer sus mensajes.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = colores.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(12.dp))
                        .clickable(enabled = !refrescando, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            calcularSeguridad(true)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                )
                {
                    Text(
                        if (refrescando) "Refrescando…" else "Refrescar llave",
                        fontSize = 14.sp,
                        fontFamily = FuenteOutfit,
                        fontWeight = FontWeight.Medium,
                        color = colores.texto,
                    )
                }
            }

            Seccion("PRIVACIDAD", colores)
            Column(modifier = Modifier.fillMaxWidth().panelVidrio()) {
                Text(
                    "Bloquear contacto",
                    fontSize = 15.sp,
                    color = colores.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { confirmar = "bloquear" }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            Text(
                "Al bloquear, esta persona no podrá escribirte y se quitará de tus chats.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colores.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (editando)
    {
        Dialog(onDismissRequest = { editando = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .panelVidrio(fuerte = true)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            )
            {
                Text("Nombre para mostrar", fontSize = 16.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colores.fondo.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                )
                {
                    if (borradorAlias.isEmpty())
                    {
                        Text(amigo.usuario, fontSize = 14.sp, color = colores.placeholder)
                    }
                    BasicTextField(
                        value = borradorAlias,
                        onValueChange = { borradorAlias = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = colores.texto),
                        cursorBrush = SolidColor(colores.texto),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Cancelar",
                        fontSize = 14.sp,
                        color = colores.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(10.dp))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { editando = false }
                            .padding(vertical = 11.dp),
                    )
                    Text(
                        "Guardar",
                        fontSize = 14.sp,
                        fontFamily = FuenteOutfit,
                        fontWeight = FontWeight.SemiBold,
                        color = colores.botonTexto,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .background(colores.botonFondo, RoundedCornerShape(10.dp))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                val nuevo = borradorAlias.trim().ifEmpty { null }
                                alcance.launch(Dispatchers.IO) { app.aliasLocal.guardar(amigo.id, nuevo) }
                                alias = nuevo
                                editando = false
                            }
                            .padding(vertical = 11.dp),
                    )
                }
            }
        }
    }

    Confirmacion(
        visible = confirmar == "borrar",
        titulo = "Borrar conversación",
        mensaje = "Se borra solo para ti. La otra persona conserva su copia.",
        textoConfirmar = "Borrar",
        destructivo = true,
        alConfirmar = {
            confirmar = null
            alcance.launch {
                withContext(Dispatchers.IO) { runCatching { app.api.limpiarConversacion(amigo.id) } }
                alNavegar("chats")
            }
        },
        alCancelar = { confirmar = null },
    )

    Confirmacion(
        visible = confirmar == "bloquear",
        titulo = "Bloquear contacto",
        mensaje = "No podrá escribirte y se quitará de tus chats. Puedes desbloquearle desde Ajustes.",
        textoConfirmar = "Bloquear",
        destructivo = true,
        alConfirmar = {
            confirmar = null
            alcance.launch {
                withContext(Dispatchers.IO) { runCatching { app.api.bloquear(amigo.id) } }
                alNavegar("chats")
            }
        },
        alCancelar = { confirmar = null },
    )
}

@Composable
private fun Seccion(titulo: String, colores: Paleta)
{
    Text(
        titulo,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = colores.muted,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}
