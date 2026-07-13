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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
fun PantallaCrearGrupo(app: AplicacionVixxer, alVolver: () -> Unit)
{
    val colores = LocalTema.current.colores
    val alcance = rememberCoroutineScope()
    var nombre by remember { mutableStateOf("") }
    var amigos by remember { mutableStateOf(listOf<Amigo>()) }
    var elegidos by remember { mutableStateOf(listOf<String>()) }
    var creando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            val datos = withContext(Dispatchers.IO) { app.api.amigos() as JSONArray }
            amigos = (0 until datos.length()).map { i ->
                val a = datos.getJSONObject(i)
                Amigo(a.getString("id"), a.optString("usuario"), a.textoO("avatar_url"))
            }
        }
    }

    fun crear()
    {
        val n = nombre.trim()
        if (n.isEmpty())
        {
            error = "Ponle un nombre al grupo"
            return
        }
        if (elegidos.isEmpty())
        {
            error = "Elige al menos un integrante"
            return
        }
        error = ""
        creando = true
        alcance.launch {
            try
            {
                withContext(Dispatchers.IO) { app.api.crearGrupo(n, elegidos) }
                alVolver()
            }
            catch (e: Exception)
            {
                error = "No se pudo crear el grupo"
                creando = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colores.fondo)
            .statusBarsPadding()
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    )
    {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colores.texto,
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alVolver() },
            )
            Text("Nuevo grupo", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }
        Campo(valor = nombre, alCambiar = { if (it.length <= 40) nombre = it }, placeholder = "Nombre del grupo")
        Text(
            "INTEGRANTES${if (elegidos.isNotEmpty()) " (${elegidos.size})" else ""}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = colores.muted,
        )
        if (error.isNotEmpty())
        {
            Text(error, fontSize = 13.sp, color = colores.error)
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (amigos.isEmpty())
            {
                item {
                    Text(
                        "Agrega amigos primero para poder armar un grupo.",
                        fontSize = 13.sp,
                        color = colores.muted,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 30.dp, end = 30.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            items(amigos, key = { it.id }) { item ->
                val marcado = elegidos.contains(item.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            elegidos = if (marcado) elegidos - item.id else elegidos + item.id
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    Avatar(nombre = item.usuario, uri = item.avatarUrl.ifEmpty { null }, tamano = 40.dp)
                    Text(item.usuario, fontSize = 15.sp, color = colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(if (marcado) colores.botonFondo else Color.Transparent, CircleShape)
                            .border(2.dp, if (marcado) colores.botonFondo else colores.borde, CircleShape),
                        contentAlignment = Alignment.Center,
                    )
                    {
                        if (marcado)
                        {
                            Visto(color = colores.botonTexto, tamano = 12.dp)
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.navigationBarsPadding()) {
            Boton(titulo = if (creando) "Creando…" else "Crear grupo", alPulsar = { crear() }, cargando = creando)
        }
    }
}
