package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun RespaldoCodigo(visible: Boolean, codigo: String, alCerrar: () -> Unit)
{
    if (!visible)
    {
        return
    }
    val tema = LocalTema.current
    val colores = tema.colores
    val portapapeles = LocalClipboardManager.current
    var copiado by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    )
    {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .background(colores.surface, RoundedCornerShape(16.dp))
                .border(1.dp, colores.borde, RoundedCornerShape(16.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        )
        {
            Text(
                "Tu código de recuperación",
                fontSize = 18.sp,
                fontFamily = FuenteOutfit,
                fontWeight = FontWeight.SemiBold,
                color = colores.texto,
            )
            Text(
                "Guárdalo en un lugar seguro. Es lo único que recupera tus chats si reinstalas o cambias de teléfono. No se vuelve a mostrar y nadie más lo conoce.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colores.muted,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colores.borde, RoundedCornerShape(12.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        portapapeles.setText(AnnotatedString(codigo))
                        copiado = true
                    }
                    .padding(vertical = 18.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            )
            {
                Text(
                    codigo,
                    fontSize = 15.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = colores.texto,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(if (copiado) "copiado" else "tocar para copiar", fontSize = 12.sp, color = colores.muted)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (copiado) 1f else 0.4f)
                    .background(colores.botonFondo, RoundedCornerShape(12.dp))
                    .clickable(enabled = copiado, indication = null, interactionSource = remember { MutableInteractionSource() }) { alCerrar() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            )
            {
                Text(
                    "Ya lo guardé",
                    color = colores.botonTexto,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
