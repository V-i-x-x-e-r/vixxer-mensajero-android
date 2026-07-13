package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Campo(
    valor: String,
    alCambiar: (String) -> Unit,
    placeholder: String,
    esContrasena: Boolean = false,
    sinMayusculas: Boolean = false,
    enMayusculas: Boolean = false,
)
{
    val tema = LocalTema.current
    val colores = tema.coloresAuth
    var ver by remember { mutableStateOf(false) }
    val interaccion = remember { MutableInteractionSource() }
    val enfocado by interaccion.collectIsFocusedAsState()
    val oculto = esContrasena && !ver

    Box {
        BasicTextField(
            value = valor,
            onValueChange = alCambiar,
            singleLine = true,
            interactionSource = interaccion,
            textStyle = TextStyle(fontSize = 14.sp, color = colores.texto),
            cursorBrush = SolidColor(colores.texto),
            visualTransformation = if (oculto) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = when
                {
                    enMayusculas -> KeyboardCapitalization.Characters
                    sinMayusculas -> KeyboardCapitalization.None
                    else -> KeyboardCapitalization.Sentences
                },
                keyboardType = if (esContrasena) KeyboardType.Password else KeyboardType.Text,
                autoCorrectEnabled = !sinMayusculas && !esContrasena,
            ),
            decorationBox = { interno ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colores.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, if (enfocado) colores.bordeFoco else colores.borde, RoundedCornerShape(10.dp))
                        .padding(PaddingValues(start = 16.dp, end = if (esContrasena) 44.dp else 16.dp, top = 12.dp, bottom = 12.dp)),
                    contentAlignment = Alignment.CenterStart,
                )
                {
                    if (valor.isEmpty())
                    {
                        Text(placeholder, fontSize = 14.sp, color = colores.placeholder)
                    }
                    interno()
                }
            },
        )
        if (esContrasena)
        {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { ver = !ver },
            )
            {
                Ojo(mostrando = ver, color = colores.placeholder)
            }
        }
    }
}
