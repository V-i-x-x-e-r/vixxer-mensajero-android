package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
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
    var nombre by remember { mutableStateOf("") }
    var miembros by remember { mutableStateOf(listOf<Miembro>()) }
    var confirmarSalir by remember { mutableStateOf(false) }

    LaunchedEffect(grupoId) {
        withContext(Dispatchers.IO) {
            runCatching {
                val g = app.api.infoGrupo(grupoId) as JSONObject
                nombre = g.optString("nombre")
                val lista = g.optJSONArray("miembros") ?: JSONArray()
                miembros = (0 until lista.length()).map { i ->
                    val m = lista.getJSONObject(i)
                    Miembro(
                        m.getString("id"),
                        m.optString("usuario"),
                        m.textoO("avatar_url"),
                        m.optString("rol"),
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colores.fondo)
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
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alNavegar("grupo/$grupoId") },
            )
            Text("Info del grupo", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        )
        {
            Avatar(nombre = nombre, tamano = 84.dp)
            Text(nombre, fontSize = 20.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
            Text("${miembros.size} miembros", fontSize = 13.sp, color = colores.muted)
        }

        Text(
            "MIEMBROS",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = colores.muted,
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(miembros, key = { it.id }) { m ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    )
                    {
                        Avatar(nombre = m.usuario, uri = m.avatarUrl.ifEmpty { null }, tamano = 42.dp)
                        Text(m.usuario, fontSize = 15.sp, color = colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { confirmarSalir = true }
                        .padding(vertical = 18.dp),
                )
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
