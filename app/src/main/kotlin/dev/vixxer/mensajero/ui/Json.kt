package dev.vixxer.mensajero.ui

import org.json.JSONObject

fun JSONObject.textoO(clave: String): String
{
    if (isNull(clave))
    {
        return ""
    }
    return optString(clave)
}
