package dev.vixxer.mensajero.nucleo

object Enlaces
{
    data class Preview(val url: String, val titulo: String, val desc: String?, val imagen: String?)

    private val reUrl = Regex("(https?://[^\\s]+)", RegexOption.IGNORE_CASE)
    private val reDominio = Regex("^https?://([^/]+)", RegexOption.IGNORE_CASE)
    private val reCola = Regex("[),.;!?]+$")
    private val reTitulo = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
    private val reComilla = Regex("&#0?39;|&apos;")
    private val reEntidadDecimal = Regex("&#(\\d+);")

    fun extraerUrl(texto: String?): String?
    {
        if (texto.isNullOrEmpty() || texto[0] == '{')
        {
            return null
        }
        val m = reUrl.find(texto) ?: return null
        return m.groupValues[1].replace(reCola, "")
    }

    fun dominioDe(url: String): String
    {
        val m = reDominio.find(url) ?: return url
        return m.groupValues[1].removePrefix("www.")
    }

    fun previewDeHtml(url: String, htmlCrudo: String): Preview?
    {
        val html = htmlCrudo.take(150000)
        val titulo = meta(html, "og:title") ?: reTitulo.find(html)?.groupValues?.get(1) ?: return null
        val desc = meta(html, "og:description") ?: meta(html, "description")
        return Preview(url, decodificar(titulo), desc?.let { decodificar(it) }, meta(html, "og:image"))
    }

    private fun meta(html: String, prop: String): String?
    {
        val directo = Regex("<meta[^>]+(?:property|name)=[\"']$prop[\"'][^>]*content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val invertido = Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"']$prop[\"']", RegexOption.IGNORE_CASE)
        val m = directo.find(html) ?: invertido.find(html) ?: return null
        return m.groupValues[1]
    }

    private fun decodificar(t: String): String =
        t.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(reComilla, "'")
            .replace(reEntidadDecimal) { r -> Char(r.groupValues[1].toInt() and 0xFFFF).toString() }
            .trim()
}
