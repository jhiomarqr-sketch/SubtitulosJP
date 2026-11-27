# Subtítulos JP→ES: guía para compilar e instalar

La app escucha el audio que suena en el celular (YouTube, TikTok, videos guardados…),
reconoce el japonés y muestra la traducción al español en una burbuja flotante encima del video.
El reconocimiento y la traducción funcionan **sin internet** después de descargar los modelos una vez.

## A. En tu laptop (solo la primera vez)
1. Descarga e instala **Android Studio** desde https://developer.android.com/studio (acepta todo por defecto; descarga ~1–2 GB).
2. Descomprime `SubtitulosJP.zip` en una carpeta **sin tildes ni espacios** (ejemplo: `C:\proyectos\SubtitulosJP`).
3. En Android Studio: **File → Open** → elige la carpeta `SubtitulosJP` → **Trust project**.
4. Espera a que termine la barra de abajo («Gradle sync»). La primera vez tarda varios minutos.
   - Si aparece un aviso para actualizar el «Android Gradle Plugin», puedes ignorarlo.

## B. Preparar tu Samsung A56 (solo la primera vez)
1. **Ajustes → Acerca del teléfono → Información de software** → toca **7 veces «Número de compilación»**.
2. Vuelve a **Ajustes → Opciones de desarrollador** → activa **Depuración USB**.
3. Conecta el celular a la laptop con cable USB y acepta el mensaje «¿Permitir depuración USB?».

## C. Instalar
- En Android Studio, arriba aparecerá tu «Samsung SM-A566…». Pulsa el botón verde **▶ Run**.
- La app se instala y se abre sola en el celular.
- (Alternativa sin cable: **Build → Build App Bundle(s)/APK(s) → Build APK(s)** → «locate» → pasa
  `app-debug.apk` al celular e instálalo, permitiendo «instalar apps desconocidas».)

## D. Usar la app
1. **Dar permisos**: acepta el audio y las notificaciones; luego activa «Mostrar sobre otras apps».
2. **Descargar modelos** (con Wi-Fi, ~80 MB en total; solo una vez).
3. **Iniciar subtítulos** → cuando Android pregunte, elige **compartir pantalla completa** → «Iniciar».
4. Abre tu video. La burbuja muestra el japonés (gris, pequeño) y el español (blanco).
   Arrástrala para moverla. Para detener, usa el botón «Detener» de la notificación.

## Límites a tener en cuenta
- Netflix, Prime Video, Crunchyroll y otras apps con protección (DRM) **bloquean la captura**: no saldrá nada.
- El modelo de voz es el pequeño (rápido y offline). Va bien con diálogo claro;
  con música de fondo, gritos o varias personas hablando a la vez, pierde precisión.
- La traducción japonés→español es automática: sirve para entender la idea, no es perfecta.
- Hay 1–2 segundos de retraso respecto al audio.

## Archivos principales
- `MainActivity.kt`: pantalla con los botones.
- `CaptionService.kt`: captura de audio + reconocimiento (Vosk) + traducción (ML Kit).
- `CaptionOverlay.kt`: la burbuja flotante.
- `ModelManager.kt`: descarga de modelos.
