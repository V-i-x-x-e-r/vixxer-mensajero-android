package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EstadoLista(
    cargando: Boolean,
    error: Boolean,
    vacio: String,
    alReintentar: (() -> Unit)? = null,
    esqueleto: (@Composable () -> Unit)? = null,
)
{
    val colores = LocalTema.current.colores

    if (cargando)
    {
        if (esqueleto != null)
        {
            esqueleto()
            return
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        )
        {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colores.muted, strokeWidth = 2.dp)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    )
    {
        if (error)
        {
            Text("No se pudo conectar.", fontSize = 14.sp, color = colores.muted, textAlign = TextAlign.Center)
            if (alReintentar != null)
            {
                Text(
                    "Reintentar",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colores.texto,
                    modifier = Modifier
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alReintentar() }
                        .padding(vertical = 6.dp, horizontal = 12.dp),
                )
            }
        }
        else
        {
            Text(vacio, fontSize = 14.sp, color = colores.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
fun ListaChatsEsqueleto()
{
    val colores = LocalTema.current.colores
    Column {
        repeat(7) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                Box(modifier = Modifier.size(44.dp).background(colores.surface, CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.width(140.dp).height(14.dp).background(colores.surface, RoundedCornerShape(7.dp)))
                    Box(modifier = Modifier.width(200.dp).height(11.dp).background(colores.surface, RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}
