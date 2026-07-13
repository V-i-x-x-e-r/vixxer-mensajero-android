package dev.vixxer.mensajero.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Boton(
    titulo: String,
    alPulsar: () -> Unit,
    cargando: Boolean = false,
    deshabilitado: Boolean = false,
    glass: Boolean = false,
)
{
    val tema = LocalTema.current
    val colores = tema.coloresAuth
    val interaccion = remember { MutableInteractionSource() }
    val presionado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (presionado) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "escalaBoton",
    )
    val inactivo = deshabilitado || cargando
    val forma = RoundedCornerShape(if (glass) 12.dp else 10.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(escala)
            .then(if (glass) Modifier.shadow(6.dp, forma) else Modifier)
            .background(colores.botonFondo.copy(alpha = if (inactivo) 0.6f else 1f), forma)
            .then(if (glass) Modifier.border(1.dp, colores.bordeFoco, forma) else Modifier)
            .clickable(enabled = !inactivo, interactionSource = interaccion, indication = null) { alPulsar() }
            .padding(vertical = if (glass) 12.dp else 11.dp),
        contentAlignment = Alignment.Center,
    )
    {
        if (cargando)
        {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colores.botonTexto,
                strokeWidth = 2.dp,
            )
        }
        else
        {
            Text(
                titulo,
                color = colores.botonTexto,
                fontFamily = FuenteOutfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (glass) 15.sp else 14.sp,
            )
        }
    }
}
