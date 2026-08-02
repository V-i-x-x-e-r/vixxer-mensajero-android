package dev.vixxer.mensajero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CampoBusqueda(
    valor: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Buscar",
)
{
    val colores = LocalTema.current.colores

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .pildoraVidrio()
            .padding(start = 18.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        Lupa(color = colores.muted, tamano = 17.dp)
        Box(modifier = Modifier.weight(1f))
        {
            if (valor.isEmpty())
            {
                Text(placeholder, fontSize = 15.sp, color = colores.placeholder)
            }
            BasicTextField(
                value = valor,
                onValueChange = alCambiar,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = colores.texto),
                cursorBrush = SolidColor(colores.texto),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (valor.isNotEmpty())
        {
            Box(
                modifier = Modifier
                    .pulsable { alCambiar("") }
                    .semantics
                    {
                        contentDescription = "Limpiar búsqueda"
                        role = Role.Button
                    }
                    .size(36.dp),
                contentAlignment = Alignment.Center,
            )
            {
                Cerrar(color = colores.muted, tamano = 17.dp)
            }
        }
    }
}
