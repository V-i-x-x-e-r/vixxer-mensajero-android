package dev.vixxer.mensajero.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class DesplazamientoChatTest
{
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun esperaLaMedicionYAbreEnElUltimoMensaje()
    {
        lateinit var estado: LazyListState
        val mensajes = (0 until 30).toList()

        compose.setContent {
            estado = rememberLazyListState()
            LaunchedEffect(mensajes.size)
            {
                desplazarAlUltimoMensaje(estado, mensajes.size)
            }
            LazyColumn(state = estado, modifier = Modifier.height(120.dp))
            {
                items(mensajes)
                {
                    Text("Mensaje $it", modifier = Modifier.height(40.dp))
                }
            }
        }

        compose.waitUntil(timeoutMillis = 5_000)
        {
            estado.layoutInfo.visibleItemsInfo.lastOrNull()?.index == mensajes.lastIndex
        }
        assertEquals(mensajes.lastIndex, estado.layoutInfo.visibleItemsInfo.last().index)
    }
}
