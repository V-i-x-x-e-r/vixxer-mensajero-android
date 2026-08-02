package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.ErrorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PantallaAgregar(
    app: AplicacionVixxer,
    codigoLeido: String? = null,
    alEscanear: () -> Unit = {},
    alVolver: () -> Unit,
)
{
    val colores = LocalTema.current.colores
    val alcance = rememberCoroutineScope()
    var codigo by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }

    fun enviar(valor: String? = null)
    {
        val codigoFinal = (valor ?: codigo).trim()
        if (codigoFinal.isEmpty())
        {
            return
        }
        estado = ""
        cargando = true
        alcance.launch {
            try
            {
                withContext(Dispatchers.IO) { app.api.solicitarAmigo(codigoFinal) }
                estado = "Solicitud enviada"
                codigo = ""
            }
            catch (e: Exception)
            {
                estado = when ((e as? ErrorApi)?.status)
                {
                    404 -> "Código no encontrado"
                    409 -> "Ya enviaste una solicitud"
                    400 -> "Ese es tu propio código"
                    else -> "No se pudo enviar la solicitud"
                }
            }
            finally
            {
                cargando = false
            }
        }
    }

    LaunchedEffect(codigoLeido)
    {
        if (!codigoLeido.isNullOrBlank())
        {
            codigo = codigoLeido
            enviar(codigoLeido)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fondoVixxer()
            .statusBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    )
    {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colores.texto,
                modifier = Modifier.pulsable { alVolver() },
            )
            Text("Agregar amigo", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }
        Text(
            "Pide a tu contacto su código de amigo y escríbelo, o escanea su código QR.",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = colores.muted,
        )
        Campo(valor = codigo, alCambiar = { codigo = it }, placeholder = "Código de amigo", enMayusculas = true)
        if (estado.isNotEmpty())
        {
            Text(estado, fontSize = 14.sp, color = if (estado == "Solicitud enviada") colores.texto else colores.error)
        }
        Boton(titulo = "Enviar solicitud", alPulsar = { enviar() }, cargando = cargando)

        Box(
            modifier = Modifier
                .pulsable { alEscanear() }
                .fillMaxWidth()
                .border(1.dp, colores.borde, RoundedCornerShape(12.dp))
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        )
        {
            Text("Escanear código QR", fontSize = 15.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.Medium, color = colores.texto)
        }
    }
}
