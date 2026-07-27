package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            )
            {
                BotonDialogo(textoCancelar, colores.texto, colores.borde, Modifier.weight(1f), alCancelar)
                BotonDialogo(textoConfirmar, colorConfirmar, colores.borde, Modifier.weight(1f), alConfirmar)
            }
        }
    }
}

@Composable
private fun BotonDialogo(texto: String, color: androidx.compose.ui.graphics.Color, borde: androidx.compose.ui.graphics.Color, modifier: Modifier, alPulsar: () -> Unit)
{
    Box(
        modifier = modifier
            .fillMaxHeight()
            .pulsable(alPulsar = alPulsar)
            .border(1.dp, borde, RoundedCornerShape(10.dp))
            .heightIn(min = 44.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    )
    {
        Text(
            texto,
            fontSize = 15.sp,
            fontFamily = FuenteOutfit,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
    }
}
