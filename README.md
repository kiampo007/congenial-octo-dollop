# Editor de Videos Profesional — Android 0.3.0

Prototipo Android nativo, gratuito y orientado al procesamiento local. Esta entrega mejora el proyecto 0.2 añadiendo la dependencia explícita de `media3-ui-compose` requerida por `PlayerSurface`, incrementa la versión y añade una compilación CI reproducible.

## Requisitos
- Android Studio reciente.
- JDK 17.
- Android SDK Platform 35 y Build Tools 35.0.0.
- Conexión inicial para resolver dependencias Maven/Gradle. La aplicación no requiere un servidor remoto.

## Compilar localmente
1. Abre la carpeta raíz `EditorVideosAndroid` en Android Studio.
2. Sincroniza Gradle.
3. Ejecuta `gradle assembleDebug` desde la raíz (o usa la tarea `assembleDebug` de Android Studio).
4. El APK de depuración, si compila correctamente, estará en `app/build/outputs/apk/debug/app-debug.apk`.

Se incluye `.github/workflows/android-ci.yml` para compilar en GitHub Actions y publicar el APK como artefacto de una ejecución de CI. No se afirma que CI haya corrido: debe activarse en un repositorio GitHub.

## Funciones presentes en el código
- Interfaz nativa en español con Jetpack Compose.
- Importación de vídeo mediante Storage Access Framework (`OpenDocument`).
- Vista previa con ExoPlayer / `PlayerSurface`.
- Lista lineal de clips y selección.
- Límites de recorte por clip y división lógica en dos segmentos.
- Eliminación de segmentos del proyecto sin borrar originales.
- Guardado local de metadatos en JSON mediante SharedPreferences.
- Exportación del clip seleccionado mediante Media3 Transformer a un MP4 elegido por el usuario.
- Sin anuncios, cuentas, analítica ni backend propio.

## Limitaciones conocidas
- **No compilado ni probado en dispositivo por esta entrega**: debe verificarse la resolución de dependencias y las APIs de Media3 en Android Studio/CI.
- La exportación procesa un solo clip seleccionado; no compone varios clips de la línea de tiempo.
- La división es lógica: dos segmentos referencian el mismo archivo fuente. La previsualización no salta automáticamente al rango del segmento.
- Brillo y contraste son controles demostrativos; no se aplican al MP4 exportado.
- Velocidad sólo se aplica a la previsualización.
- No hay audio multipista, transiciones, títulos renderizados, fotogramas clave, subtítulos, undo/redo ni mezcla de varios medios.
- SharedPreferences conserva URI; el acceso puede perderse si el proveedor revoca permisos o mueve el archivo.
- No hay APK de release firmado, pruebas instrumentadas, pruebas de rendimiento ni auditoría de seguridad formal.

## Licencias y privacidad
La aplicación no declara permisos de Internet y usa el selector del sistema para acceder a medios elegidos por la persona. Revisar las licencias de todas las dependencias antes de redistribuir. “Gratuito” no implica que todas las bibliotecas tengan la misma licencia.
