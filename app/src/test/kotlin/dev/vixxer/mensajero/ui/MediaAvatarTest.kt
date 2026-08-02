package dev.vixxer.mensajero.ui

import android.graphics.Bitmap
import androidx.core.content.FileProvider
import dev.vixxer.mensajero.BuildConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], application = android.app.Application::class)
class MediaAvatarTest
{
    @Test
    fun avatarDecodificaUriConImageDecoder()
    {
        val contexto = RuntimeEnvironment.getApplication()
        val carpeta = File(contexto.cacheDir, "capturas").apply { mkdirs() }
        val archivo = File(carpeta, "avatar-prueba.png")
        val mapa = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        mapa.eraseColor(android.graphics.Color.rgb(122, 128, 136))
        archivo.outputStream().use {
            mapa.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        mapa.recycle()
        val uri = FileProvider.getUriForFile(
            contexto,
            BuildConfig.APPLICATION_ID + ".archivos",
            archivo,
        )

        val avatar = comprimirAvatar(contexto, uri)

        assertEquals(720, avatar.ancho)
        assertEquals(540, avatar.alto)
        assertTrue(avatar.bytes.isNotEmpty())
        archivo.delete()
    }

    @Test
    fun avatarSeReduceYRespetaElTopeDeCarga()
    {
        val mapa = Bitmap.createBitmap(1440, 900, Bitmap.Config.ARGB_8888)
        mapa.eraseColor(android.graphics.Color.rgb(122, 128, 136))

        val avatar = requireNotNull(codificarAvatar(mapa))

        assertEquals(720, avatar.ancho)
        assertEquals(450, avatar.alto)
        assertTrue(avatar.bytes.size <= 700 * 1024)
        mapa.recycle()
    }
}
