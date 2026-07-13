package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ErrorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun PantallaLogin(app: AplicacionVixxer, alNavegar: (String) -> Unit)
{
    val tema = LocalTema.current
    val colores = tema.coloresAuth
    val alcance = rememberCoroutineScope()
    var usuario by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var olvido by remember { mutableStateOf(false) }

    suspend fun entrarTrasSesion()
    {
        val destino = withContext(Dispatchers.IO) {
            val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA)
            if (priv != null)
            {
                val pub = app.boveda.leer(ClavesSeguras.CLAVE_PUBLICA)
                runCatching { app.api.actualizarLlavePublica(pub ?: "") }
                runCatching { app.firma.publicarLlaveFirma() }
                "chats"
            }
            else
            {
                "recuperar"
            }
        }
        alNavegar(destino)
    }

    LaunchedEffect(Unit) {
        val token = withContext(Dispatchers.IO) { app.boveda.leer(ClavesSeguras.TOKEN) }
        if (token != null)
        {
            entrarTrasSesion()
        }
    }

    fun entrar()
    {
        if (usuario.trim().isEmpty() || contrasena.isEmpty())
        {
            error = "Escribe tu usuario y contraseña"
            return
        }
        error = ""
        cargando = true
        alcance.launch {
            try
            {
                withContext(Dispatchers.IO) {
                    val data = app.api.login(usuario.trim(), contrasena) as JSONObject
                    app.boveda.escribir(ClavesSeguras.TOKEN, data.getString("token"))
                    app.boveda.escribir(ClavesSeguras.MI_ID, data.getJSONObject("usuario").getString("id"))
                }
                entrarTrasSesion()
            }
            catch (e: Exception)
            {
                error = if ((e as? ErrorApi)?.status == 401)
                {
                    "Usuario o contraseña incorrectos"
                }
                else
                {
                    "No se pudo conectar. ¿Está arriba el backend?"
                }
            }
            finally
            {
                cargando = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colores.fondo),
    )
    {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, start = 28.dp, end = 28.dp),
            horizontalArrangement = Arrangement.End,
        )
        {
            BotonTema()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 28.dp)
                .padding(bottom = 104.dp),
            verticalArrangement = Arrangement.Center,
        )
        {
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp), contentAlignment = Alignment.Center) {
                LogoPendulo(alto = 132.dp, colorTexto = colores.texto)
            }
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    "Iniciar sesión",
                    fontSize = 24.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp,
                    color = colores.texto,
                )
                Text("Bienvenido de vuelta", fontSize = 14.sp, color = colores.muted, modifier = Modifier.padding(top = 4.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Campo(valor = usuario, alCambiar = { usuario = it }, placeholder = "Usuario", sinMayusculas = true)
                Campo(valor = contrasena, alCambiar = { contrasena = it }, placeholder = "Contraseña", esContrasena = true)
                Text(
                    "¿Olvidaste tu contraseña?",
                    fontSize = 12.sp,
                    color = colores.muted,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { olvido = true },
                )
                if (error.isNotEmpty())
                {
                    Text(error, fontSize = 13.sp, color = colores.error)
                }
                Boton(titulo = "Entrar", alPulsar = { entrar() }, cargando = cargando, glass = true)
            }
            val pie = buildAnnotatedString {
                append("¿No tienes cuenta? ")
                withLink(LinkAnnotation.Clickable("registro") { alNavegar("registro") }) {
                    withStyle(SpanStyle(color = colores.enlace, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) {
                        append("Regístrate")
                    }
                }
            }
            Text(
                pie,
                fontSize = 14.sp,
                color = colores.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            )
        }

        Confirmacion(
            visible = olvido,
            titulo = "¿Olvidaste tu contraseña?",
            mensaje = "Vixxer no pide correo ni teléfono, así que nadie puede restablecerla por ti. Si la recuerdas más tarde, entra normal. Si no, crea una cuenta nueva y comparte tu código de amigo otra vez.",
            textoConfirmar = "Crear cuenta",
            textoCancelar = "Entendido",
            alConfirmar = {
                olvido = false
                alNavegar("registro")
            },
            alCancelar = { olvido = false },
        )
    }
}
