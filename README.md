# Marcos de Arte — Modo Arte

Aplicación de galería para Android TV inspirada en el Modo Arte de televisores tipo Samsung The Frame.

## Novedades de esta versión
- Marcos con bisel visual: dorado, nogal, negro museo, blanco, plata, roble y sin marco.
- Paspartú de museo y etiqueta de título discreta.
- Añadir varias imágenes desde el selector de Android.
- Añadir carpetas completas con subcarpetas.
- Compatible con proveedores de documentos que exponga el selector (por ejemplo, Drive si está instalado y disponible en el televisor).
- Modo obra fija o cambio automático configurable.
- Orden alfabético o aleatorio.
- Gestión de decodificación en segundo plano y reducción de resolución para limitar memoria.
- Preferencias persistentes.

## Compilar APK
1. Abre el proyecto en Android Studio o súbelo a GitHub.
2. Ejecuta `gradle assembleDebug` o usa **Actions > Construir APK > Run workflow**.
3. Descarga el artefacto `MarcosArte-apk`.
4. Instala el APK en Android TV/TCL.

## Mandos
- Izquierda / Derecha: obra anterior / siguiente.
- Arriba / Abajo: cambiar marco.
- OK corto: mostrar / ocultar título.
- OK mantenido o botón rojo: ajustar / llenar.
- Play/Pausa o tecla 1: activar / pausar cambio automático.
- MENU: colecciones y ajustes.
- Botón verde: añadir imágenes.
- Botón azul: añadir carpeta.

## Fuentes y servicios online
El selector Android Storage Access Framework permite seleccionar archivos o carpetas de los proveedores de documentos disponibles en el dispositivo. La integración depende de que el proveedor (por ejemplo Google Drive) esté instalado y exponga el contenido en Android TV.

Esta versión **no incluye todavía** autenticación OAuth propia, sincronización programada con Drive API ni integración directa con Google Photos Picker. Google Fotos no debe considerarse disponible desde el televisor hasta probar el proveedor y/o implementar el flujo oficial compatible. El enlace compartido de una carpeta de Drive no se importa automáticamente por sí solo.

Las imágenes seleccionadas se leen desde el proveedor elegido; si el dispositivo pierde el permiso, el proveedor deja de estar disponible o el archivo es retirado, la obra podría no abrirse. No se descarga todavía una copia offline administrada por la app.

## Metadatos
En esta versión se muestra el título derivado del nombre del archivo. El autor, año histórico y descripción por pintura requieren un catálogo de metadatos que queda para la siguiente iteración.
