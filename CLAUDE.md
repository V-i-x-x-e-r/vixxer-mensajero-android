# Vixxer Mensajero — app nativa Android

Mensajería E2EE con mesh de cercanía por Bluetooth. Kotlin + Compose.
La fuente de verdad de qué falta es `vixxer-docs/producto/ruta-nativo.md`.

## Módulos

- `nucleo/` — lógica pura sobre JVM: cripto, cliente de API, outbox, mesh,
  almacén. Sin dependencias de Android. Aquí van los tests de lógica.
- `app/` — todo lo de Android y la UI en Compose.

Si algo se puede probar sin Android, va en `nucleo`.

## Estilo

- **Allman**: la llave abre en su propia línea. También en `when`, `if`, `try`.
- **Sin comentarios.** El nombre de la función cuenta la historia. Si hace
  falta un comentario para entender el código, el código está mal escrito.
- Funciones cortas, con un solo trabajo.
- Nombres en español, como el resto del proyecto.
- Repo público: ningún nombre real, token ni dato interno en el código.

## Antes de dar algo por terminado

```
./gradlew :nucleo:test                                  # 113 tests de lógica
./gradlew :app:testDebugUnitTest -Pcapturas=verificar    # nada movió un pixel
./gradlew :app:compileReleaseKotlin                      # R8 no se rompe
```

Los tres corren solos en CI en cada push y PR. Si tocaste UI a propósito,
regraba con `-Pcapturas=grabar` y **mira los PNG** antes de subirlos: son la
única forma de ver la app sin instalarla.

## Convenciones de UI

- Todo lo que se toca lleva `Modifier.pulsable` o `pulsableLargo`. Nunca
  `clickable(indication = null)` a secas: apaga el ripple sin poner nada en su
  lugar y la app se siente muerta.
- `pulsable` va **al frente** de la cadena de modificadores. Un modificador
  escala lo que va después de él, así que ponerlo tras `.background()` deja el
  fondo quieto y solo encoge el contenido.
- Excepciones legítimas, ya decididas: scrims de cierre a pantalla completa,
  burbujas de mensaje (un toque ahí no hace nada) y el tragaclics de la hoja
  de stickers.
- Los avatares siempre pasan por `Avatar`, que cae a inicial con color. Nunca
  un glifo genérico distinto por pantalla.
- Abajo de Android 12 no hay blur (`hayBlur = SDK >= 31`): ahí el vidrio es
  un relleno casi opaco sobre el fondo estático, nunca translucidez turbia
  sobre contenido en movimiento.
- El vidrio no lleva marco en ningún tema: la definición sale del contraste
  del relleno, el brillo del tope y la sombra. El único contorno vivo es la
  lente de la pestaña activa.
- Tema claro invertido tipo iOS: vidrio blanco sobre fondo gris.

## Trampas que ya costaron

- El remoto **debe ser SSH**. Por HTTPS, el token OAuth de `gh` no trae el
  scope `workflow` y GitHub rechaza cualquier push que toque
  `.github/workflows/`.
- Las capturas necesitan `application = android.app.Application::class` en
  `@Config`: la `AplicacionVixxer` real revienta al cargar libsodium por JNA.
- `ui-test-manifest` va como `debugImplementation`, no `testImplementation`.
- El plugin de Gradle de Roborazzi no sirve con AGP 9 (usa `TestedExtension`,
  que ya no existe). Se usa sin plugin, con propiedades de sistema.

## Flujo

Rama desde `develop`, PR con squash de vuelta a `develop`. César autoriza
mergear sin pedir confirmación. **No compilar APKs de release salvo que los
pida**: para verificar visualmente están las capturas.
