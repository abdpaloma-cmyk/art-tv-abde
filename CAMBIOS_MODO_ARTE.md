# Cambios realizados en la versión 1.1

- Reemplazado `MainActivity.kt`: selector múltiple de imágenes y selector de carpetas, permisos persistentes, exploración recursiva, orden aleatorio, temporizador configurable, modo obra fija, gestión de carga con executor único y control de errores.
- Reemplazado `FrameView.kt`: estilos de marco con bisel, paspartú y etiqueta discreta en el margen inferior.
- Incrementada la versión Android a 1.1 (versionCode 2).
- Actualizado README con mandos y limitaciones reales de integración cloud.

## Nota de compilación
No se pudo ejecutar Gradle en este entorno porque no hay instalación de Gradle ni wrapper `gradlew` en el ZIP. El workflow existente sí instala Gradle 8.7 y está preparado para compilar en GitHub Actions. Esta entrega no debe considerarse compilada ni probada en una TCL física.
