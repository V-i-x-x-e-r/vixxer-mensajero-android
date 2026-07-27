package dev.vixxer.mensajero.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PESTANAS = listOf("amigos" to "Amigos", "chats" to "Chats", "grupos" to "Grupos")

@Composable
fun BarraPestanas(actual: String, alCambiar: (String) -> Unit, modifier: Modifier = Modifier)
{
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 7.dp)
            .pildoraVidrio()
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        for ((clave, etiqueta) in PESTANAS)
        {
            val activa = actual == clave
            val color by animateColorAsState(
                targetValue = if (activa) colorPestanaActiva() else colorPestanaInactiva(),
                label = "colorPestana",
            )
            val fondo by animateColorAsState(
                targetValue = if (activa) colorBrilloPestana() else Color.Transparent,
                label = "fondoPestana",
            )
            Row(
                modifier = Modifier
                    .pulsable { alCambiar(clave) }
                    .background(fondo, RoundedCornerShape(Vidrio.radioPildora))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                when (clave)
                {
                    "amigos" -> IconoAmigos(color)
                    "chats" -> IconoChat(color)
                    else -> IconoGrupos(color)
                }
                Text(
                    etiqueta,
                    fontSize = 13.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = if (activa) FontWeight.SemiBold else FontWeight.Medium,
                    color = color,
                )
            }
        }
    }
}
