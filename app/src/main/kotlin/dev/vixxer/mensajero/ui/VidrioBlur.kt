package dev.vixxer.mensajero.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

val hayBlurNativo: Boolean = Build.VERSION.SDK_INT >= 31

@Composable
fun recordarHaze(): HazeState = remember {
    HazeState(initialBlurEnabled = hayBlurNativo)
}

fun Modifier.fondoDesenfocable(estado: HazeState): Modifier = this.hazeSource(estado)
