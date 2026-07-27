package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.AlmacenEnMemoria
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class CapturaBurbujasTest
{
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun conversacionEnOscuro()
    {
        capturarConversacion(oscuro = true, nombre = "burbujas-oscuro")
    }

    @Test
    fun conversacionEnClaro()
    {
        capturarConversacion(oscuro = false, nombre = "burbujas-claro")
    }

    private fun capturarConversacion(oscuro: Boolean, nombre: String)
    {
        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = oscuro)
            CompositionLocalProvider(LocalTema provides tema) {
                Conversacion(tema.colores)
            }
        }
        compose.onRoot().captureRoboImage("src/test/capturas/$nombre.png")
    }
}

private val MENSAJES = listOf(
    Mensaje(
        id = "1",
        remitenteId = "otro",
        texto = "¿Ya probaste el radar sin internet?",
        enviadoEn = "2026-07-27T10:00:00Z",
    ),
    Mensaje(
        id = "2",
        remitenteId = "yo",
        texto = "Sí, saltó por Bluetooth a los dos segundos",
        enviadoEn = "2026-07-27T10:01:00Z",
        entregado = true,
        leido = true,
    ),
    Mensaje(
        id = "3",
        remitenteId = "yo",
        texto = "Este lo edité",
        enviadoEn = "2026-07-27T10:02:00Z",
        entregado = true,
        editado = true,
    ),
    Mensaje(
        id = "4",
        remitenteId = "otro",
        texto = "Te respondo a ese",
        enviadoEn = "2026-07-27T10:03:00Z",
        respuestaA = "3",
        respuestaTexto = "Este lo edité",
        reacciones = mapOf("otro" to "🔥"),
    ),
    Mensaje(
        id = "5",
        remitenteId = "otro",
        texto = null,
        enviadoEn = "2026-07-27T10:04:00Z",
        borrado = true,
    ),
    Mensaje(
        id = "6",
        remitenteId = "yo",
        texto = "Este falló al enviar",
        enviadoEn = "2026-07-27T10:05:00Z",
        estado = "fallido",
    ),
    Mensaje(
        id = "7",
        remitenteId = "yo",
        texto = "Y este viajó por cercanía",
        enviadoEn = "2026-07-27T10:06:00Z",
        estado = "cercania",
    ),
)

@Composable
private fun Conversacion(colores: Paleta)
{
    val app = AplicacionVixxer()
    Column(
        modifier = Modifier
            .background(colores.fondo)
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    )
    {
        for (m in MENSAJES)
        {
            Burbuja(
                m = m,
                mio = m.remitenteId == "yo",
                colores = colores,
                app = app,
                alAbrirImagen = {},
                alAbrirVideo = {},
                miId = "yo",
                seleccionando = false,
                seleccionado = false,
                alReintentar = {},
                alPulsar = {},
                alMantener = {},
            )
        }
    }
}
