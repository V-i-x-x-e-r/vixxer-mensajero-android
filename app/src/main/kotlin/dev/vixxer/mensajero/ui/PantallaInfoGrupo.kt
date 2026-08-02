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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Miembro(val id: String, val usuario: String, val avatarUrl: String, val rol: String)

@Composable
fun PantallaInfoGrupo(app: AplicacionVixxer, grupoId: String, alNavegar: (String) -> Unit)
{
    val colores = LocalTema.current.colores
    val alcance = rememberCoroutineScope()
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var nombre by remember { mutableStateOf("") }
    var avatarGrupo by remember { mutableStateOf<String?>(null) }
    var miembros by remember { mutableStateOf(listOf<Miembro>()) }
    var miId by remember { mutableStateOf("") }
    var confirmarSalir by remember { mutableStateOf(false) }
    var renombrando by remember { mutableStateOf(false) }
    var borradorNombre by remember { mutableStateOf("") }
    var miembroSel by remember { mutableStateOf<Miembro?>(null) }
    var agregando by remember { mutableStateOf(false) }
    var candidatos by remember { mutableStateOf(listOf<Amigo>()) }
    var elegidos by remember { mutableStateOf(listOf<String>()) }
    var subiendoFoto by remember { mutableStateOf(false) }

    suspend fun cargar()
    {
        withContext(Dispatchers.IO) {
            runCatching {
                val g = app.api.infoGrupo(grupoId) as JSONObject
                nombre = g.optString("nombre")
                avatarGrupo = g.textoO("avatar_url").ifEmpty { null }
                val lista = g.optJSONArray("miembros") ?: JSONArray()
                miembros = (0 until lista.length()).map { i ->
                    val m = lista.getJSONObject(i)
                    Miembro(m.getString("id"), m.optString("usuario"), m.textoO("avatar_url"), m.optString("rol"))
                }
            }
        }
    }

    LaunchedEffect(grupoId) {
        miId = withContext(Dispatchers.IO) { app.boveda.leer(ClavesSeguras.MI_ID) ?: "" }
        cargar()
    }

    val soyAdmin = miembros.any { it.id == miId && it.rol == "admin" }

    val selectorFoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null)
        {
            subiendoFoto = true
            alcance.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val imagen = comprimirImagen(contexto, uri) ?: return@runCatching
                        val b64 = android.util.Base64.encodeToString(imagen.bytes, android.util.Base64.NO_WRAP)
                        app.api.avatarGrupo(grupoId, b64, "image/jpeg")
                    }
                }
                subiendoFoto = false
                cargar()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fondoVixxer()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    )
    {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colores.texto,
                modifier = Modifier.pulsable { alNavegar("grupo/$grupoId") },
            )
            Text("Info del grupo", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        )
        {
            Box(
                modifier = Modifier.pulsable(habilitado = soyAdmin) {
                    app.saltarBloqueo = true
                    selectorFoto.launch(androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ))
                },
            )
            {
                Avatar(nombre = nombre, uri = avatarGrupo, tamano = 84.dp)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pulsable(habilitado = soyAdmin) {
                    borradorNombre = nombre
                    renombrando = true
                },
            )
            {
                Text(nombre, fontSize = 20.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
                if (soyAdmin)
                {
                    Lapiz(color = colores.muted, tamano = 15.dp)
                }
            }
            Text(
                "${miembros.size} miembros${if (subiendoFoto) " · subiendo foto…" else ""}",
                fontSize = 13.sp,
                color = colores.muted,
            )
        }

        Text(
            "MIEMBROS",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = colores.muted,
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (soyAdmin)
            {
                item {
                    Text(
                        "+ Agregar miembros",
                        fontSize = 15.sp,
                        fontFamily = FuenteOutfit,
                        fontWeight = FontWeight.Medium,
                        color = colores.enlace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pulsable {
                                alcance.launch {
                                    val amigos = withContext(Dispatchers.IO) {
                                        runCatching { app.api.amigos() as JSONArray }.getOrNull()
                                    } ?: return@launch
                                    val dentro = miembros.map { it.id }.toSet()
                                    candidatos = (0 until amigos.length()).map { i ->
                                        val a = amigos.getJSONObject(i)
                                        Amigo(a.getString("id"), a.optString("usuario"), a.textoO("avatar_url"))
                                    }.filter { !dentro.contains(it.id) }
                                    elegidos = emptyList()
                                    agregando = true
                                }
                            }
                            .padding(vertical = 12.dp),
                    )
                }
            }
            items(miembros, key = { it.id }) { m ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pulsable(habilitado = soyAdmin && m.id != miId) { miembroSel = m }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    )
                    {
                        Avatar(nombre = m.usuario, uri = m.avatarUrl.ifEmpty { null }, tamano = 42.dp)
                        Text(
                            if (m.id == miId) "${m.usuario} (tú)" else m.usuario,
                            fontSize = 15.sp,
                            color = colores.texto,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (m.rol == "admin")
                        {
                            Text(
                                "admin",
                                fontSize = 11.sp,
                                fontFamily = FuenteOutfit,
                                fontWeight = FontWeight.Medium,
                                color = colores.muted,
                                modifier = Modifier
                                    .background(colores.surface, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(start = 54.dp).height(1.dp).background(colores.borde))
                }
            }
            item {
                Text(
                    "Salir del grupo",
                    fontSize = 15.sp,
                    color = colores.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pulsable { confirmarSalir = true }
                        .padding(vertical = 18.dp),
                )
            }
        }
    }

    val sel = miembroSel
    if (sel != null)
    {
        androidx.activity.compose.BackHandler { miembroSel = null }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { miembroSel = null },
            contentAlignment = Alignment.BottomCenter,
        )
        {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .panelVidrio(radio = 20.dp, fuerte = true)
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 28.dp),
            )
            {
                Text(
                    sel.usuario.uppercase(),
                    fontSize = 12.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = colores.muted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Text(
                    if (sel.rol == "admin") "Quitar admin" else "Hacer admin",
                    fontSize = 16.sp,
                    color = colores.texto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pulsable {
                            miembroSel = null
                            alcance.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching { app.api.cambiarRol(grupoId, sel.id, if (sel.rol == "admin") "miembro" else "admin") }
                                }
                                cargar()
                            }
                        }
                        .padding(vertical = 14.dp, horizontal = 24.dp),
                )
                Text(
                    "Expulsar del grupo",
                    fontSize = 16.sp,
                    color = colores.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pulsable {
                            miembroSel = null
                            alcance.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching { app.api.expulsarMiembro(grupoId, sel.id) }
                                }
                                cargar()
                            }
                        }
                        .padding(vertical = 14.dp, horizontal = 24.dp),
                )
            }
        }
    }

    if (agregando)
    {
        androidx.activity.compose.BackHandler { agregando = false }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { agregando = false },
            contentAlignment = Alignment.BottomCenter,
        )
        {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .panelVidrio(radio = 20.dp, fuerte = true)
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 20.dp),
            )
            {
                Text(
                    "AGREGAR MIEMBROS",
                    fontSize = 12.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = colores.muted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.weight(1f, fill = false).padding(horizontal = 16.dp)) {
                    if (candidatos.isEmpty())
                    {
                        item {
                            Text(
                                "Todos tus amigos ya están en el grupo.",
                                fontSize = 13.sp,
                                color = colores.muted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            )
                        }
                    }
                    items(candidatos, key = { it.id }) { a ->
                        val marcado = elegidos.contains(a.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pulsable {
                                    elegidos = if (marcado) elegidos - a.id else elegidos + a.id
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        )
                        {
                            Avatar(nombre = a.usuario, uri = a.avatarUrl.ifEmpty { null }, tamano = 38.dp)
                            Text(a.usuario, fontSize = 15.sp, color = colores.texto, modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(if (marcado) colores.botonFondo else Color.Transparent, CircleShape)
                                    .border(2.dp, if (marcado) colores.botonFondo else colores.borde, CircleShape),
                                contentAlignment = Alignment.Center,
                            )
                            {
                                if (marcado)
                                {
                                    Visto(color = colores.botonTexto, tamano = 11.dp)
                                }
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .background(colores.botonFondo.copy(alpha = if (elegidos.isEmpty()) 0.5f else 1f), RoundedCornerShape(12.dp))
                        .pulsable(habilitado = elegidos.isNotEmpty()) {
                            agregando = false
                            alcance.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching { app.api.agregarMiembros(grupoId, elegidos) }
                                }
                                cargar()
                            }
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                )
                {
                    Text(
                        "Agregar${if (elegidos.isNotEmpty()) " (${elegidos.size})" else ""}",
                        fontSize = 15.sp,
                        fontFamily = FuenteOutfit,
                        fontWeight = FontWeight.SemiBold,
                        color = colores.botonTexto,
                    )
                }
            }
        }
    }

    if (renombrando)
    {
        Dialog(onDismissRequest = { renombrando = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .panelVidrio(fuerte = true)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            )
            {
                Text("Nombre del grupo", fontSize = 16.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colores.fondo.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                )
                {
                    BasicTextField(
                        value = borradorNombre,
                        onValueChange = { if (it.length <= 40) borradorNombre = it },
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
                            .pulsable { renombrando = false }
                            .weight(1f)
                            .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(10.dp))
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
                            .pulsable {
                                val limpio = borradorNombre.trim()
                                renombrando = false
                                if (limpio.isNotEmpty() && limpio != nombre)
                                {
                                    alcance.launch {
                                        withContext(Dispatchers.IO) {
                                            runCatching { app.api.renombrarGrupo(grupoId, limpio) }
                                        }
                                        cargar()
                                    }
                                }
                            }
                            .padding(vertical = 11.dp),
                    )
                }
            }
        }
    }

    Confirmacion(
        visible = confirmarSalir,
        titulo = "Salir del grupo",
        mensaje = "Dejarás de recibir sus mensajes. Podrán volver a agregarte más adelante.",
        textoConfirmar = "Salir",
        destructivo = true,
        alConfirmar = {
            confirmarSalir = false
            alcance.launch {
                withContext(Dispatchers.IO) { runCatching { app.api.salirGrupo(grupoId) } }
                alNavegar("grupos")
            }
        },
        alCancelar = { confirmarSalir = false },
    )
}
