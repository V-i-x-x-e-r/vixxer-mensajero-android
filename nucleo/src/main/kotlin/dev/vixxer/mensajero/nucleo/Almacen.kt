package dev.vixxer.mensajero.nucleo

interface Almacen
{
    fun leer(clave: String): String?
    fun escribir(clave: String, valor: String)
    fun borrar(clave: String)
}

class AlmacenEnMemoria : Almacen
{
    private val datos = LinkedHashMap<String, String>()

    override fun leer(clave: String): String? = datos[clave]

    override fun escribir(clave: String, valor: String)
    {
        datos[clave] = valor
    }

    override fun borrar(clave: String)
    {
        datos.remove(clave)
    }
}
