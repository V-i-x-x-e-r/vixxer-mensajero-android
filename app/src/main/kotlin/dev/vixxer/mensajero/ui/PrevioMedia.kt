package dev.vixxer.mensajero.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun PrevioMediaMulti(
    items: List<PrevioEnvio>,
    colores: Paleta,
    onCancelar: () -> Unit,
    onEnviar: (List<Pair<PrevioEnvio, String?>>) -> Unit,
)
{
    var indice by remember(items) { mutableStateOf(0) }
    val caps = remember(items) { mutableStateMapOf<Int, String>() }
    val actualIndice = indice.coerceIn(0, items.size - 1)
    val actual = items.getOrNull(actualIndice) ?: return

    BackHandler { onCancelar() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    )
    {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        )
        {
            if (items.size > 1)
            {
                Text("${actualIndice + 1} de ${items.size}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
            }
            else
            {
                Box(modifier = Modifier)
            }
            Text(
                "✕",
                fontSize = 22.sp,
                color = Color.White,
                modifier = Modifier.pulsable { onCancelar() },
            )
        }

        VistaPrevio(previo = actual, modifier = Modifier.weight(1f).fillMaxWidth())

        if (items.size > 1)
        {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            )
            {
                itemsIndexed(items) { i, item ->
                    Box(
                        modifier = Modifier
                            .pulsable { indice = i }
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .then(if (i == actualIndice) Modifier.border(2.dp, colores.botonFondo, RoundedCornerShape(10.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    )
                    {
                        AsyncImage(
                            model = item.miniatura ?: item.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                        )
                        if (item.esVideo)
                        {
                            Text("▶", fontSize = 14.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        )
        {
            CampoMensaje(
                valor = caps[actualIndice] ?: "",
                alCambiar = { caps[actualIndice] = it },
                modifier = Modifier.weight(1f),
                placeholder = "Añade un comentario…",
            )
            BotonCircularPrimario(
                descripcion = "Enviar archivos",
                alPulsar =
                {
                    onEnviar(items.mapIndexed { i, item -> item to (caps[i]?.trim()?.ifEmpty { null }) })
                },
            )
            {
                Enviar(color = colores.botonTexto)
            }
        }
    }
}
