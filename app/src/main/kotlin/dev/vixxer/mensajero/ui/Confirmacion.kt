package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun Confirmacion(
    visible: Boolean,
    titulo: String,
    mensaje: String? = null,
    textoConfirmar: String = "Aceptar",
    textoCancelar: String = "Cancelar",
    destructivo: Boolean = false,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit,
)
{
    if (!visible)
    {
        return
    }
    val tema = LocalTema.current
    val colores = tema.coloresAuth
    val colorConfirmar = if (destructivo) colores.error else colores.texto

    Dialog(onDismissRequest = alCancelar) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .background(colores.surface.copy(alpha = 0.98f), RoundedCornerShape(16.dp))
                .border(1.dp, colores.borde, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        )
        {
            Text(titulo, fontSize = 17.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
            if (mensaje != null)
            {
                Text(mensaje, fontSize = 14.sp, lineHeight = 20.sp, color = colores.muted)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonDialogo(textoCancelar, colores.texto, colores.borde, Modifier.weight(1f), alCancelar)
                BotonDialogo(textoConfirmar, colorConfirmar, colores.borde, Modifier.weight(1f), alConfirmar)
            }
        }
    }
}

@Composable
private fun BotonDialogo(texto: String, color: androidx.compose.ui.graphics.Color, borde: androidx.compose.ui.graphics.Color, modifier: Modifier, alPulsar: () -> Unit)
{
    Text(
        texto,
        fontSize = 15.sp,
        fontFamily = FuenteOutfit,
        fontWeight = FontWeight.SemiBold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
            .border(1.dp, borde, RoundedCornerShape(10.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alPulsar() }
            .padding(vertical = 12.dp),
    )
}
