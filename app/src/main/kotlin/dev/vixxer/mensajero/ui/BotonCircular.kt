package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun BotonCircularVidrio(
    descripcion: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    tamano: Dp = 44.dp,
    habilitado: Boolean = true,
    contenido: @Composable () -> Unit,
)
{
    Box(
        modifier = modifier
            .pulsable(habilitado = habilitado, alPulsar = alPulsar)
            .semantics
            {
                contentDescription = descripcion
                role = Role.Button
            }
            .size(tamano)
            .circuloVidrio(),
        contentAlignment = Alignment.Center,
    )
    {
        contenido()
    }
}

@Composable
fun BotonCircularPrimario(
    descripcion: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    tamano: Dp = 44.dp,
    habilitado: Boolean = true,
    contenido: @Composable () -> Unit,
)
{
    val fondo = LocalTema.current.colores.botonFondo

    Box(
        modifier = modifier
            .pulsable(habilitado = habilitado, alPulsar = alPulsar)
            .semantics
            {
                contentDescription = descripcion
                role = Role.Button
            }
            .zIndex(1f)
            .size(tamano)
            .background(fondo, CircleShape),
        contentAlignment = Alignment.Center,
    )
    {
        contenido()
    }
}
