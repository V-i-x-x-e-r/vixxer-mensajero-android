package dev.vixxer.mensajero.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun BotonTema(modifier: Modifier = Modifier)
{
    val tema = LocalTema.current
    val colores = tema.coloresAuth
    val descripcion = if (tema.oscuro) "Activar tema claro" else "Activar tema oscuro"

    Box(
        modifier = modifier
            .size(48.dp)
            .pulsable { tema.alternar() }
            .semantics
            {
                contentDescription = descripcion
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    )
    {
        Box(
            modifier = Modifier
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
}
