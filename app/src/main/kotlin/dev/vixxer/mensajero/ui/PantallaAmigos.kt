package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAmigos(app: AplicacionVixxer, alNavegar: (String) -> Unit, alAbrirChat: (Amigo) -> Unit)
{
    val colores = LocalTema.current.colores
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf(listOf<Amigo>()) }
    var cargando by remember { mutableStateOf(true) }
    var refrescando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var pendientes by remember { mutableStateOf(0) }
    var sel by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf(false) }

    suspend fun cargar()
    {
        error = false
        try
        {
            val resultado = withContext(Dispatchers.IO) {
                val datos = app.api.amigos() as JSONArray
                app.estado.escribir("vixxer_lista_amigos", datos.toString())
                val solicitudes = runCatching { (app.api.solicitudes() as JSONArray).length() }.getOrDefault(0)
                Pair(datos, solicitudes)
            }
            lista = (0 until resultado.first.length()).map { i ->
                val a = resultado.first.getJSONObject(i)
                Amigo(a.getString("id"), a.optString("usuario"), a.textoO("avatar_url"))
            }
            pendientes = resultado.second
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
        val cache = withContext(Dispatchers.IO) { app.estado.leer("vixxer_lista_amigos") }
        if (cache != null)
        {
            runCatching {
                val datos = JSONArray(cache)
                lista = (0 until datos.length()).map { i ->
                    val a = datos.getJSONObject(i)
                    Amigo(a.getString("id"), a.optString("usuario"), a.textoO("avatar_url"))
                }
                cargando = false
            }
        }
        cargar()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fondoVixxer()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    )
    {
        val seleccionado = sel
        if (seleccionado != null)
        {
            Row(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                Text(
                    "✕",
                    fontSize = 22.sp,
                    color = colores.texto,
                    modifier = Modifier.pulsable { sel = null },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.pulsable { confirmar = true },
                )
                {
                    Bote(color = colores.error)
                    Text("Borrar amigo", fontSize = 15.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.Medium, color = colores.error)
                }
            }
        }
        else
        {
            CabeceraPrincipal(
                titulo = "Amigos",
                subtitulo = if (lista.size == 1) "1 contacto" else "${lista.size} contactos",
                descripcionAccion = "Abrir ajustes",
                alPulsarAccion = { alNavegar("ajustes") },
            )
            {
                Engrane(color = it)
            }
        }

        PullToRefreshBox(
            isRefreshing = refrescando,
            onRefresh = {
                alcance.launch {
                    refrescando = true
                    cargar()
                    refrescando = false
                }
            },
            modifier = Modifier.weight(1f),
        )
        {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        AccionAmigos("Agregar por código", { PersonaMas(it) }, colores, Modifier.weight(1f)) { alNavegar("agregar") }
                        Box(modifier = Modifier.weight(1f)) {
                            AccionAmigos("Solicitudes", { Campana(it) }, colores, Modifier.fillMaxWidth()) { alNavegar("solicitudes") }
                            if (pendientes > 0)
                            {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 8.dp, end = 10.dp)
                                        .defaultMinSize(minWidth = 18.dp)
                                        .height(18.dp)
                                        .background(colores.error, CircleShape)
                                        .padding(horizontal = 5.dp),
                                    contentAlignment = Alignment.Center,
                                )
                                {
                                    Text("$pendientes", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Text(
                        "TUS CONTACTOS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = colores.muted,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                if (lista.isEmpty())
                {
                    item {
                        EstadoLista(
                            cargando = cargando,
                            error = error,
                            vacio = "Aún no tienes contactos. Agrega a alguien por su código.",
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (sel == item.id) colores.surface else androidx.compose.ui.graphics.Color.Transparent,
                                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                )
                                .pulsableLargo(
                                    alMantener = { sel = item.id },
                                    alPulsar = { if (sel != null) sel = item.id else alAbrirChat(item) },
                                )
                                .padding(vertical = 12.dp, horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        )
                        {
                            Avatar(nombre = item.usuario, uri = item.avatarUrl.ifEmpty { null }, tamano = 44.dp)
                            Text(item.usuario, fontSize = 16.sp, color = colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(modifier = Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(colores.borde))
                    }
                }
                item {
                    Box(modifier = Modifier.height(90.dp))
                }
            }
        }
    }

    Confirmacion(
        visible = confirmar,
        titulo = "Borrar amigo",
        mensaje = "Dejarán de ser amigos y la conversación se borrará de tu lista. La otra persona conserva su copia.",
        textoConfirmar = "Borrar",
        destructivo = true,
        alConfirmar = {
            val id = sel
            confirmar = false
            sel = null
            if (id != null)
            {
                alcance.launch {
                    withContext(Dispatchers.IO) { runCatching { app.api.eliminarAmigo(id) } }
                    cargar()
                }
            }
        },
        alCancelar = { confirmar = false },
    )
}

@Composable
private fun AccionAmigos(texto: String, icono: @Composable (androidx.compose.ui.graphics.Color) -> Unit, colores: Paleta, modifier: Modifier, alPulsar: () -> Unit)
{
    Row(
        modifier = modifier
            .pulsable { alPulsar() }
            .panelVidrio(radio = 10.dp)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        icono(colores.texto)
        Text(texto, fontSize = 14.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.Medium, color = colores.texto)
    }
}
