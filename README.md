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
