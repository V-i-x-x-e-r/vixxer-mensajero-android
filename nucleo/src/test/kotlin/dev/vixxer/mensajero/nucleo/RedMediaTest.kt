package dev.vixxer.mensajero.nucleo

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedMediaTest
{
    @Test
    fun bloqueaLoopbackLanLinkLocalYHostsInternos()
    {
        val bloqueadas = listOf(
            "http://127.0.0.1/admin",
            "http://10.0.0.8/",
            "http://172.16.2.3/",
            "http://192.168.1.20/",
            "http://169.254.169.254/latest/meta-data/",
            "http://100.64.1.1/",
            "http://localhost/",
            "http://impresora.local/",
            "http://servicio.internal/",
            "http://intranet/",
            "http://[::1]/",
            "http://[fe80::1]/",
            "http://[fc00::1]/",
            "http://[::ffff:127.0.0.1]/",
        )
        for (url in bloqueadas)
        {
            assertFalse(RedMedia.esUrlPublica(url), url)
        }
    }

    @Test
    fun aceptaUrlsPublicasSinCredenciales()
    {
        assertTrue(RedMedia.esUrlPublica("https://vixxer.com/noticias?id=1"))
        assertTrue(RedMedia.esUrlPublica("https://8.8.8.8/"))
        assertTrue(RedMedia.esUrlPublica("https://[2606:4700:4700::1111]/"))
        assertFalse(RedMedia.esUrlPublica("https://usuario:clave@vixxer.com/"))
        assertFalse(RedMedia.esUrlPublica("file:///etc/passwd"))
        assertFalse(RedMedia.esUrlPublica("javascript:alert(1)"))
    }

    @Test
    fun validaIpv6Tunel6to4SegunIpv4Embebida()
    {
        assertTrue(RedMedia.esDireccionPublica(InetAddress.getByName("2002:0808:0808::1")))
        assertFalse(RedMedia.esDireccionPublica(InetAddress.getByName("2002:0a00:0001::1")))
        assertFalse(RedMedia.esDireccionPublica(InetAddress.getByName("2002:c0a8:0101::1")))
    }

    @Test
    fun normalizaOgImageRelativaYRechazaDestinosPrivados()
    {
        assertEquals(
            "https://vixxer.com/media/portada.jpg",
            RedMedia.normalizarImagen("https://vixxer.com/blog/nota", "/media/portada.jpg"),
        )
        assertNull(RedMedia.normalizarImagen("https://vixxer.com/blog/nota", "http://127.0.0.1/foto"))
        assertNull(RedMedia.normalizarImagen("https://vixxer.com/blog/nota", "data:image/png;base64,AAAA"))
        assertNull(RedMedia.normalizarImagen("http://localhost/nota", "https://vixxer.com/foto"))
    }
}
