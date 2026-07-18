package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.ErrorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun PantallaRegistro(app: AplicacionVixxer, alNavegar: (String) -> Unit)
{
    val tema = LocalTema.current
    val colores = tema.coloresAuth
    val alcance = rememberCoroutineScope()
    var usuario by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var codigo by remember { mutableStateOf("") }

    fun crear()
    {
        val u = usuario.trim()
        if (u.length < 3 || u.length > 20)
        {
            error = "El usuario debe tener entre 3 y 20 caracteres"
            return
        }
        if (contrasena.length < 6)
        {
            error = "La contraseña debe tener al menos 6 caracteres"
            return
        }
        error = ""
        cargando = true
        alcance.launch {
            var cuentaCreada = false
            try
            {
                val nuevoCodigo = withContext(Dispatchers.IO) {
                    val pendiente = app.identidad.prepararRegistro(u)
                    val registro = app.api.registrar(
                        u,
                        contrasena,
                        pendiente.identidad.publicKey,
                        pendiente.firma.publicKey,
                        pendiente.identidad.respaldo,
                    ) as JSONObject
                    val cuentaId = registro.getString("id")
                    cuentaCreada = true
                    app.activarCuenta(cuentaId)
                    app.identidad.confirmarRegistro(pendiente)
                    app.identidad.confirmarRespaldoSubido()
                    app.identidad.borrarRegistroPendiente()
                    val data = app.api.login(u, contrasena) as JSONObject
                    val sesionId = data.getJSONObject("usuario").getString("id")
                    check(sesionId == cuentaId)
                    app.guardarSesion(data.getString("token"), cuentaId)
                    pendiente.identidad.codigo
                }
                codigo = nuevoCodigo
            }
            catch (e: Exception)
            {
                if (cuentaCreada)
                {
                    withContext(Dispatchers.IO) { app.cerrarSesionLocal() }
                }
                error = if (cuentaCreada)
                {
                    "La cuenta se creó, pero no terminó de configurarse. Inicia sesión para continuar."
                }
                else when ((e as? ErrorApi)?.status)
                {
                    409 -> "Ese usuario ya existe"
                    422 -> "Usuario (3-20) y contraseña (mín. 6)"
                    else -> "No se pudo registrar. ¿Está arriba el backend?"
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        )
        {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LogoPenduloFila(alto = 22.dp)
                Text("Vixxer", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
            }
            BotonTema()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 90.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.Center,
        )
        {
            Column(modifier = Modifier.padding(bottom = 36.dp)) {
                Text(
                    "Crear cuenta",
                    fontSize = 24.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp,
                    color = colores.texto,
                )
                Text("Sin correo, sin teléfono", fontSize = 14.sp, color = colores.muted, modifier = Modifier.padding(top = 4.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Campo(valor = usuario, alCambiar = { usuario = it }, placeholder = "Usuario", sinMayusculas = true)
                Campo(valor = contrasena, alCambiar = { contrasena = it }, placeholder = "Contraseña", esContrasena = true)
                if (error.isNotEmpty())
                {
                    Text(error, fontSize = 13.sp, color = colores.error)
                }
                Boton(titulo = "Registrarme", alPulsar = { crear() }, cargando = cargando)
            }
            val pie = buildAnnotatedString {
                append("¿Ya tienes cuenta? ")
                withLink(LinkAnnotation.Clickable("login") { alNavegar("login") }) {
                    withStyle(SpanStyle(color = colores.enlace, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) {
                        append("Inicia sesión")
                    }
                }
            }
            Text(
                pie,
                fontSize = 14.sp,
                color = colores.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            )
        }

        RespaldoCodigo(
            visible = codigo.isNotEmpty(),
            codigo = codigo,
            alCerrar = {
                alcance.launch {
                    withContext(Dispatchers.IO) { app.identidad.confirmarCodigoGuardado() }
                    alNavegar("chats")
                }
            },
        )
    }
}
