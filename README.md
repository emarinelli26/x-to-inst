# X to Insta

App Android que toma un post de X con video y lo convierte en un video vertical (1080x1920) listo para compartir en Instagram como Reel o historia, sin guardarlo en la galería.

## Cómo se usa

1. En X, toca **Compartir → Compartir vía…** y elige **X to Insta**, o pega el enlace del post en la app.
2. La app lee el post, baja el video a su memoria temporal y arma el video vertical con el autor, el texto y el video.
3. Toca **Compartir como Reel** o **Compartir como historia**: Instagram se abre con el video cargado y tú tocas "Compartir".

## Cómo funciona

| Parte | Archivo |
| --- | --- |
| Lee el post (FxTwitter y, si falla, el endpoint de inserción de X) | `TweetFetcher.kt` |
| Dibuja la tarjeta 9:16 con un hueco para el video | `CardRenderer.kt` |
| Monta el video dentro del hueco y exporta el MP4 (Media3 Transformer) | `VideoComposer.kt` |
| Abre Instagram con el video | `InstagramSharer.kt` |

Las fuentes de datos de X no son oficiales y pueden cambiar sin aviso.

## Instagram y el Meta App ID

Instagram tiene intents específicos para historias y Reels, pero piden un **Meta App ID** (se crea gratis en developers.facebook.com). Sin él, la app usa el menú genérico de Instagram, donde eliges Reel o historia.

Para usarlo, pon el ID en `META_APP_ID` dentro de `gradle.properties`, o en una variable del repositorio llamada `META_APP_ID` para los builds de GitHub Actions.

## Compilar

- **GitHub Actions:** cada push a `main` compila el APK de prueba. Descárgalo desde la pestaña *Actions → Build APK → Artifacts*.
- **Local:** abre el proyecto en Android Studio o ejecuta `./gradlew assembleDebug`.
