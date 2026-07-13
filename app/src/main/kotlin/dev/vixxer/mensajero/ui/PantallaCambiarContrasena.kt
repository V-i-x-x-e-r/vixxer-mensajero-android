package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PantallaCambiarContrasena(app: AplicacionVixxer, alVolver: () -> Unit)
{
    val colores = LocalTema.current.colores
    val alcance = rememberCoroutineScope()
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var repetir by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var listo by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }

    LaunchedEffect(listo) {
        if (listo)
        {
            delay(1200)
            alVolver()
        }
    }

    fun confirmar()
    {
        if (nueva.length < 6)
        {
            error = "La nueva contraseña debe tener al menos 6 caracteres"
            return
        }
        if (nueva != repetir)
        {
            error = "Las contraseñas no coinciden"
            return
        }
        error = ""
        ocupado = true
        alcance.launch {
            try
            {
                withContext(Dispatchers.IO) { app.api.cambiarContrasena(actual, nueva) }
                listo = true
            }
            catch (e: Exception)
            {
                error = if ((e as? ErrorApi)?.status == 400)
                {
                    "La contraseña actual no es correcta"
                }
                else
                {
                    "No se pudo cambiar. Intenta de nuevo."
                }
            }
            finally
            {
                ocupado = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colores.fondo)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    )
    {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colores.texto,
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alVolver() },
            )
            Text("Cambiar contraseña", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }
        Campo(valor = actual, alCambiar = { actual = it }, placeholder = "Contraseña actual", esContrasena = true)
        Campo(valor = nueva, alCambiar = { nueva = it }, placeholder = "Contraseña nueva", esContrasena = true)
        Campo(valor = repetir, alCambiar = { repetir = it }, placeholder = "Repetir contraseña nueva", esContrasena = true)
        if (error.isNotEmpty())
        {
            Text(error, fontSize = 13.sp, color = colores.error)
        }
        if (listo)
        {
            Text("Contraseña cambiada ✓", fontSize = 14.sp, color = colores.texto)
        }
        Boton(titulo = "Cambiar", alPulsar = { confirmar() }, cargando = ocupado)
    }
}
