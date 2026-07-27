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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private val REACCIONES = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

data class AccionesDe<T>(val mensaje: T, val limites: Rect)

@Composable
fun <T> AccionesMensaje(
    sel: AccionesDe<T>?,
    esMio: Boolean,
    fijado: Boolean = false,
    alReaccionar: ((T, String) -> Unit)? = null,
    alResponder: ((T) -> Unit)? = null,
    alReenviar: ((T) -> Unit)? = null,
    alSeleccionar: ((T) -> Unit)? = null,
    alCopiar: ((T) -> Unit)? = null,
    alEditar: ((T) -> Unit)? = null,
    alFijar: ((T) -> Unit)? = null,
    alInfo: ((T) -> Unit)? = null,
    alBorrar: ((T) -> Unit)? = null,
    alBorrarLocal: ((T) -> Unit)? = null,
    alCerrar: () -> Unit,
)
{
    if (sel == null)
    {
        return
    }
    val colores = LocalTema.current.colores
    val densidad = LocalDensity.current
    val mensaje = sel.mensaje

    androidx.activity.compose.BackHandler { alCerrar() }

    data class Accion(val etiqueta: String, val color: Color?, val icono: @Composable (Color) -> Unit, val correr: () -> Unit)

    val acciones = buildList {
        if (alResponder != null)
        {
            add(Accion("Responder", null, { Responder(it) }) { alResponder(mensaje) })
        }
        if (alReenviar != null)
        {
            add(Accion("Reenviar", null, { Reenviar(it) }) { alReenviar(mensaje) })
        }
        if (alSeleccionar != null)
        {
            add(Accion("Seleccionar", null, { Check(it) }) { alSeleccionar(mensaje) })
        }
        if (alFijar != null)
        {
            add(Accion(if (fijado) "Quitar" else "Fijar", null, { Pin(it, tamano = 18.dp) }) { alFijar(mensaje) })
        }
        if (alCopiar != null)
        {
            add(Accion("Copiar", null, { Copiar(it) }) { alCopiar(mensaje) })
        }
        if (esMio && alEditar != null)
        {
            add(Accion("Editar", null, { Lapiz(it) }) { alEditar(mensaje) })
        }
        if (esMio && alInfo != null)
        {
            add(Accion("Vistos", null, { Ojo(mostrando = true, color = it) }) { alInfo(mensaje) })
        }
        if (alBorrarLocal != null)
        {
            add(Accion("Para mí", null, { Bote(it, tamano = 18.dp) }) { alBorrarLocal(mensaje) })
        }
        if (esMio && alBorrar != null)
        {
            add(Accion("Borrar", colores.error, { Bote(colores.error, tamano = 18.dp) }) { alBorrar(mensaje) })
        }
    }

    val ancho = 232.dp
    val porFila = 4
    val filas = maxOf(1, (acciones.size + porFila - 1) / porFila)
    val altoPx = with(densidad) { ((if (alReaccionar != null) 40 else 6) + filas * 52).dp.toPx() }
    val anchoPx = with(densidad) { ancho.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alCerrar() },
    )
    {
        val arriba = sel.limites.top - altoPx - 24 > with(densidad) { 90.dp.toPx() }
        val top = if (arriba) sel.limites.top - altoPx - 24 else sel.limites.bottom + 24
        val maxIzq = with(densidad) { 12.dp.toPx() }
        val izquierda = if (esMio) sel.limites.right - anchoPx else sel.limites.left

        Column(
            modifier = Modifier
                .offset { IntOffset(izquierda.coerceAtLeast(maxIzq).roundToInt(), top.roundToInt()) }
                .width(ancho)
                .panelVidrio(radio = 16.dp, desenfocar = true)
                .padding(4.dp),
        )
        {
            if (alReaccionar != null)
            {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    for (emoji in REACCIONES)
                    {
                        Text(
                            emoji,
                            fontSize = 17.sp,
                            modifier = Modifier.pulsable {
                                alReaccionar(mensaje, emoji)
                            },
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).background(colores.borde).padding(top = 0.5.dp))
            }
            for (fila in acciones.chunked(porFila))
            {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (accion in fila)
                    {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .pulsable { accion.correr() }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        )
                        {
                            accion.icono(accion.color ?: colores.texto)
                            Text(accion.etiqueta, fontSize = 10.sp, color = accion.color ?: colores.texto)
                        }
                    }
                    repeat(porFila - fila.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun SelectorContacto(app: AplicacionVixxer, visible: Boolean, titulo: String, alElegir: (Amigo) -> Unit, alCerrar: () -> Unit)
{
    if (!visible)
    {
        return
    }
    val colores = LocalTema.current.colores
    var amigos by remember { mutableStateOf(listOf<Amigo>()) }

    androidx.activity.compose.BackHandler { alCerrar() }

    LaunchedEffect(Unit) {
        runCatching {
            val datos = withContext(Dispatchers.IO) { app.api.amigos() as JSONArray }
            amigos = (0 until datos.length()).map { i ->
                val a = datos.getJSONObject(i)
                Amigo(a.getString("id"), a.optString("usuario"), a.textoO("avatar_url"))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alCerrar() },
        contentAlignment = Alignment.BottomCenter,
    )
    {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .panelVidrio(radio = 20.dp, fuerte = true, desenfocar = true)
                .padding(bottom = 24.dp),
        )
        {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                Text(titulo, fontSize = 16.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
                Text(
                    "✕",
                    fontSize = 18.sp,
                    color = colores.muted,
                    modifier = Modifier.pulsable { alCerrar() },
                )
            }
            LazyColumn(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (amigos.isEmpty())
                {
                    item {
                        Text(
                            "No tienes contactos.",
                            fontSize = 14.sp,
                            color = colores.muted,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                items(amigos, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pulsable { alElegir(item) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    )
                    {
                        Avatar(nombre = item.usuario, uri = item.avatarUrl.ifEmpty { null }, tamano = 40.dp)
                        Text(item.usuario, fontSize = 16.sp, color = colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
