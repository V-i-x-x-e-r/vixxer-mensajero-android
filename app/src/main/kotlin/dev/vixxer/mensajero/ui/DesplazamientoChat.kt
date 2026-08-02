package dev.vixxer.mensajero.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

internal suspend fun desplazarAlUltimoMensaje(estado: LazyListState, cantidad: Int)
{
    if (cantidad <= 0)
    {
        return
    }
    snapshotFlow { estado.layoutInfo.totalItemsCount }
        .first { medidos -> medidos >= cantidad }
    estado.scrollToItem(cantidad - 1)
}
