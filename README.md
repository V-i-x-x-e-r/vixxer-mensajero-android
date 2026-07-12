# vixxer-mensajero-android

Cliente Android nativo de Vixxer (Kotlin + Jetpack Compose), en migración
por fases desde el cliente React Native. El backend es el mismo; los dos
clientes hablan el mismo protocolo E2EE.

## Módulos

- `nucleo`: lógica sin UI (cripto E2EE con libsodium, formato de media por
  trozos, mensaje canónico de firma). JVM puro: se prueba sin emulador.

## Interoperabilidad

`nucleo/src/test/resources/vectores-interop.json` contiene vectores
generados con tweetnacl desde el cliente React Native. Los tests de
`InteropTest` exigen igualdad byte a byte en ambas direcciones; si un test
de interop falla, el cambio rompe compatibilidad con los clientes en campo
y no se mergea.

```
./gradlew :nucleo:test
```

## Convenciones

- Identificadores en español, sin comentarios.
- Llaves estilo Allman en declaraciones y control de flujo; las trailing
  lambdas de Kotlin llevan la llave en la misma línea porque el lenguaje
  no permite bajarla.
