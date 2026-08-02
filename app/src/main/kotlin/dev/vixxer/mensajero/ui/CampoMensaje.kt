package dev.vixxer.mensajero.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CampoMensaje(
    valor: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Mensaje",
)
{
    val colores = LocalTema.current.colores

    Box(
        modifier = modifier
            .panelVidrio(radio = 22.dp, desenfocar = true)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
    {
        if (valor.isEmpty())
        {
            Text(placeholder, fontSize = 15.sp, color = colores.placeholder)
        }
        BasicTextField(
            value = valor,
            onValueChange = alCambiar,
            textStyle = TextStyle(fontSize = 15.sp, color = colores.texto),
            cursorBrush = SolidColor(colores.texto),
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
