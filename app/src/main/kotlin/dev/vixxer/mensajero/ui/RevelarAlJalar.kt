package dev.vixxer.mensajero.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

class ReveladoAlJalar(
    private val puedeRevelar: () -> Boolean,
    private val puedeOcultar: () -> Boolean,
    private val alOcultar: () -> Unit,
)
{
    var visible by mutableStateOf(false)
    private var revelando = false

    val conexion = object : NestedScrollConnection
    {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset
        {
            if (available.y < -6f && visible && puedeOcultar())
            {
                visible = false
                alOcultar()
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset
        {
            if (source != NestedScrollSource.UserInput || available.y <= 0f)
            {
                return Offset.Zero
            }
            if (!visible && available.y > 6f && puedeRevelar())
            {
                visible = true
                revelando = true
            }
            if (revelando)
            {
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity
        {
            revelando = false
            return Velocity.Zero
        }
    }
}

@Composable
fun recordarReveladoAlJalar(
    puedeRevelar: () -> Boolean = { true },
    puedeOcultar: () -> Boolean = { true },
    alOcultar: () -> Unit = {},
): ReveladoAlJalar = remember { ReveladoAlJalar(puedeRevelar, puedeOcultar, alOcultar) }
