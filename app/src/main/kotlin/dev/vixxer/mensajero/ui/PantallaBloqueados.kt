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

@Composable
fun PantallaBloqueados(app: AplicacionVixxer, alVolver: () -> Unit)
{
    val colores = LocalTema.current.colores
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf(listOf<Amigo>()) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var errorAccion by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf<String?>(null) }

    suspend fun cargar()
    {
        error = false
        try
        {
            val datos = withContext(Dispatchers.IO) { app.api.bloqueados() as JSONArray }
            lista = (0 until datos.length()).map { i ->
                val u = datos.getJSONObject(i)
                Amigo(u.getString("id"), u.optString("usuario"), u.textoO("avatar_url"))
            }
        }
        catch (e: Exception)
        {
            error = true
        }
        finally
        {
            cargando = false
        }
    }

    LaunchedEffect(Unit) {
        cargar()
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
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alVolver() },
            )
            Text("Usuarios bloqueados", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }
        if (errorAccion.isNotEmpty())
        {
            Text(errorAccion, fontSize = 13.sp, color = colores.error)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (lista.isEmpty())
            {
                item {
                    EstadoLista(
                        cargando = cargando,
                        error = error,
                        vacio = "No tienes a nadie bloqueado.",
                        alReintentar = {
                            cargando = true
                            alcance.launch { cargar() }
                        },
                    )
                }
            }
            items(lista, key = { it.id }) { item ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    )
                    {
                        Avatar(nombre = item.usuario, uri = item.avatarUrl.ifEmpty { null }, tamano = 42.dp)
                        Text(item.usuario, fontSize = 15.sp, color = colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            if (ocupado == item.id) "…" else "Desbloquear",
                            fontSize = 13.sp,
                            fontFamily = FuenteOutfit,
                            fontWeight = FontWeight.Medium,
                            color = colores.texto,
                            modifier = Modifier
                                .border(1.dp, colores.borde, RoundedCornerShape(10.dp))
                                .clickable(enabled = ocupado == null, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                    ocupado = item.id
                                    errorAccion = ""
                                    alcance.launch {
                                        val desbloqueado = withContext(Dispatchers.IO) {
                                            runCatching { app.api.desbloquear(item.id) }.isSuccess
                                        }
                                        if (desbloqueado)
                                        {
                                            lista = lista.filter { it.id != item.id }
                                        }
                                        else
                                        {
                                            errorAccion = "No se pudo desbloquear. Revisa tu conexión."
                                        }
                                        ocupado = null
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(start = 54.dp).height(1.dp).background(colores.borde))
                }
            }
        }
    }
}
