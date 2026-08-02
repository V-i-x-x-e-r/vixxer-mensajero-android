package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CabeceraPrincipal(
    titulo: String,
    subtitulo: String,
    descripcionAccion: String,
    alPulsarAccion: () -> Unit,
    accionPrimaria: Boolean = false,
    iconoAccion: @Composable (Color) -> Unit,
)
{
    val colores = LocalTema.current.colores

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .panelVidrio(radio = 18.dp, fuerte = true, desenfocar = true)
            .padding(start = 16.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        )
        {
            Text(
                titulo,
                fontSize = 19.sp,
                fontFamily = FuenteOutfit,
                fontWeight = FontWeight.SemiBold,
                color = colores.texto,
            )
            Text(subtitulo, fontSize = 12.sp, color = colores.muted)
        }
        if (accionPrimaria)
        {
            BotonCircularPrimario(
                descripcion = descripcionAccion,
                alPulsar = alPulsarAccion,
                tamano = 42.dp,
            )
            {
                iconoAccion(colores.botonTexto)
            }
        }
        else
        {
            BotonCircularVidrio(
                descripcion = descripcionAccion,
                alPulsar = alPulsarAccion,
                tamano = 42.dp,
            )
            {
                iconoAccion(colores.texto)
            }
        }
    }
}

@Composable
fun CabeceraMensajero(
    estado: String,
    conectado: Boolean,
    alAbrirAjustes: () -> Unit,
)
{
    val colores = LocalTema.current.colores

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .panelVidrio(radio = 18.dp, fuerte = true, desenfocar = true)
            .padding(start = 16.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        LogoPenduloFila(alto = 24.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        )
        {
            Text(
                "Mensajero",
                fontSize = 16.sp,
                fontFamily = FuenteOutfit,
                fontWeight = FontWeight.SemiBold,
                color = colores.texto,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                EstadoConexion(conectado)
                Text(estado, fontSize = 12.sp, color = colores.muted)
            }
        }
        BotonCircularVidrio(
            descripcion = "Abrir ajustes",
            alPulsar = alAbrirAjustes,
            tamano = 42.dp,
        )
        {
            Engrane(color = colores.texto)
        }
    }
}

@Composable
private fun EstadoConexion(conectado: Boolean)
{
    val colores = LocalTema.current.colores
    Box(
        modifier = Modifier
            .padding(top = 1.dp)
            .size(7.dp)
            .background(if (conectado) Color(0xFF22C55E) else colores.muted, CircleShape),
    )
}
