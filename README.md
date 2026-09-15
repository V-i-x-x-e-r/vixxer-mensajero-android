# Vixxer Mensajero — Android

Cliente Android de Vixxer (Kotlin + Jetpack Compose).

La arquitectura, el stack y las guías del proyecto son documentación interna
del equipo.

## Compilar

```
./gradlew :nucleo:test
./gradlew :app:assembleDebug
```

Requiere `ANDROID_HOME` con `platforms;android-36` y `build-tools;35.0.0`. El
APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Licencia

Copyright (C) 2026 César Servín González.

GPL-3.0-or-later — ver [`LICENSE`](LICENSE). Quien distribuya este programa o
una versión modificada de él tiene que hacerlo bajo la misma licencia y con el
código fuente disponible.
