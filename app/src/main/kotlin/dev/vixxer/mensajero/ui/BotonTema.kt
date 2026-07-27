package dev.vixxer.mensajero.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BotonTema()
{
    val tema = LocalTema.current
    val colores = tema.coloresAuth

    Box(
        modifier = Modifier
            .pulsable { tema.alternar() }
            .size(38.dp)
            .border(1.dp, colores.borde, CircleShape),
        contentAlignment = Alignment.Center,
    )
    {
        if (tema.oscuro)
        {
            Sol(color = colores.texto)
        }
        else
        {
            Luna(color = colores.texto)
        }
    }
}
