<div align="center">

  <img src="assets/brand/nuvio-latino-logo-dark.png" alt="Nuvio Latino" width="440" />

# Nuvio Latino

**Nuvio TV, optimizado para quien busca audio latino.**

*Una versión no oficial de [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) que te dice, antes de reproducir, cuáles fuentes tienen de verdad audio en español latino — y te da la de mejor calidad.*

[![Licencia: GPL-3.0](https://img.shields.io/badge/licencia-GPL--3.0-blue.svg)](./LICENSE)

</div>

---

## ¿Qué es Nuvio Latino?

Nuvio Latino es un **fork comunitario y no oficial** de Nuvio TV, centrado en una sola idea: **encontrar audio en español latino es difícil, y los nombres de los archivos mienten.**

Nuvio Latino **verifica el audio real** de cada fuente leyendo el encabezado del contenedor (MKV, MP4 o HLS) antes de que presiones reproducir, y te muestra cuáles tienen **audio latino verificado**, cuáles solo Castellano, y cuáles no tienen español. Así dejas de adivinar y empiezas a reproducir.

No es un competidor de Nuvio TV y no pretende reemplazarlo. Es un banco de pruebas comunitario: las ideas sólidas y bien probadas pueden, eventualmente, proponerse al proyecto oficial.

> **Nuvio Latino no está afiliado ni respaldado por el proyecto Nuvio TV.** Por favor **no reportes problemas de esta versión** en el GitHub o Discord oficial de Nuvio — no pueden ayudarte con cambios que no hicieron.

---

## ¿Para quién es?

Para personas que quieren **escuchar el doblaje latino** de sus películas y series, y que hoy pierden tiempo abriendo fuente por fuente para descubrir que "LATINO" en el nombre era solo publicidad.

Funciona en los mismos dispositivos que Nuvio TV (Fire TV Stick, Onn, Mi Box, Chromecast / Google TV, y cajas Android TV en general), incluidas cajas económicas de 1.5–2 GB de RAM.

---

## Qué agrega este fork

- **Audio latino verificado (MKV, MP4 y HLS)** — lectura del encabezado real del archivo (primeros 256 KB; para MP4 también la cola cuando el `moov` está al final). No descarga el video ni usa `MediaExtractor` sobre URLs remotas.
- **Origen de la insignia, sin ambigüedad:**
  - `LATINO ✓` — el archivo declara una pista latina (`es-419`, `LA`, `LATAM`, "Latino", etc.).
  - `LATINO ?` — solo el nombre del release sugiere latino; no está verificado.
  - `ESPAÑOL` — hay audio en español, pero el archivo no aclara si es latino o Castellano.
  - `SIN LATINO` — el probe leyó las pistas y no encontró español. Se oculta si ya hay una insignia de español para no mostrar información contradictoria.
- **Filtro `Solo Latino`** — solo fuentes con latino verificado. El chip muestra `verificados / comprobados` (por ejemplo `1/8`) y un aviso con `Verificar más` para seguir con el siguiente lote de REMUX en caché.
- **Filtro `Español / Latino`** — heurística por nombre/metadatos para fuentes que no se han probado, útil sobre todo en 1080p.
- **Orden `Mejor con Latino`** — prioriza latino verificado, luego Castellano, luego calidad (REMUX, 4K, HDR) y tamaño.
- **Insignias técnicas compactas** — `4K`/`1080p`, `DV`, `HDR10`/`HDR10+`/`HLG`, `ATMOS`, `DTS:X`/`DTS-HD`, `TrueHD`, `DD+`, `5.1`/`7.1`, `HEVC`/`AV1`, `REMUX` y tamaño. Van en la misma línea del título y solo bajan a una segunda línea cuando de verdad no caben; no hay contador `+N` que esconda información.
- **Barra superior en dos filas** — `Fuente` y `Orden` abren menús de vidrio; los filtros rápidos quedan siempre a la vista.
- **Insignias reales en el reproductor** — resolución, DV/HDR, formato de audio, canales e idioma de la pista seleccionada, leídos del stream en reproducción (no del nombre del archivo). También aparecen en la pantalla de pausa.
- **Caché de verificación** — 7 días para resultados verificados, 6 horas para inconclusos; por perfil, con tope de 1000 entradas y escrituras agrupadas.
- **Títulos alternativos en español latino** — metadatos TMDb en español (es-MX), sincronizados con el idioma de la app.
- **Subtítulos en español por defecto** — preferencia Español + Inglés secundario (Settings → Playback → Subtitles).
- **Rendimiento en cajas de gama baja** — navegación a 60 fps, bitmaps por hardware y carga diferida del héroe.
- **Actualizador propio** — actualizaciones dentro de la app desde los Releases de este repositorio.

---

## Cómo funciona la verificación

1. El addon devuelve las fuentes: `infoHash`, `fileIdx`, servicio de debrid, tamaño y nombre.
2. Si la fuente es de debrid, la app resuelve un **enlace directo** con la API del proveedor. Solo se prueban fuentes **en caché** (y URLs directas que no pasan por debrid).
3. La app envía una petición HTTP con `Range: bytes=0-262143` (**256 KB**) al enlace directo del CDN. Para MP4 con el `moov` al final, hace una segunda petición de 256 KB desde la cola.
4. El parser lee:
   - **MKV/WebM (EBML):** `TrackType` de audio, `Language`, `LanguageBCP47`, `Name`, `CodecID`, `Channels`, flags de default/forced.
   - **MP4/MOV:** `moov` → `trak` → `mdia` (`hdlr = soun`, `mdhd`, `elng`, `stsd`).
   - **HLS:** el playlist maestro (`#EXT-X-MEDIA`, `#EXT-X-STREAM-INF`); no descarga segmentos.
5. El clasificador decide:
   - `es-419`, `es-MX`, `es-AR`, `es-CO`, `es-005`, "Latino", "LA", "LATAM", "Latinoamérica" → **latino**.
   - `es-ES`, "Castellano", "EU", "España", "Spain" → **Castellano**.
   - `spa` / `es` sin región ni nombre → **español sin confirmar** (nunca se marca como latino por adivinar).
   - sin español → **sin latino**.
6. El resultado se cachea y se refleja en la tarjeta y en los filtros.

La lectura es **solo del encabezado**: no se descarga la película, no se guardan los bytes en disco y no se contacta al addon otra vez para verificar.

---

## Seguridad y cuota de debrid

- Solo se prueban **fuentes en caché** (o URLs directas). Nunca se añade un torrent sin caché a tu cuenta solo para verificar.
- **8 REMUX por lote**, **2 en paralelo**, timeout de **4.5 s** por lectura y como máximo **256 KB** (+256 KB de cola en MP4).
- Si el proveedor responde `429/401/403/408/5xx`, el lote se detiene y se respeta `Retry-After` (hasta 15 minutos).
- Los resultados se reutilizan: no se vuelve a probar la misma fuente en la sesión ni durante 7 días (6 horas si fue inconcluso).
- Con TorBox, cada probe son ~3 llamadas de API (`createTorrent` con `addOnlyIfCached`, `getTorrent`, `requestDownloadLink`). Un lote de 8 está muy por debajo de los límites publicados (300/min por endpoint para contenido en caché; el límite de 60/hora aplica a torrents sin caché, que aquí no se prueban).

---

## Limitaciones (honestas)

- **No incluye addons, scrapers ni contenido.** Todo depende de las fuentes que instales. Nuvio Latino no genera fuentes nuevas; solo verifica, filtra y ordena lo que tus addons devuelven.
- La verificación por lotes cubre **REMUX en caché**. Los **1080p** no se prueban automáticamente para no gastar cuota; para esos usa `Español / Latino` o la verificación manual (en evaluación).
- Si una pista solo dice `spa`/`es` y no trae región ni nombre ("Spanish", "Español"), la app **no puede saber** si es latino o Castellano. Muestra `ESPAÑOL` y no la cuenta como latino verificado.
- Las insignias del reproductor reflejan lo que el reproductor y el dispositivo exponen. Si el decoder no reporta HDR/DV, no se inventa la insignia.
- Algunos archivos pueden quedar **inconclusos** si las pistas no están dentro del encabezado leído; el resultado se cachea para no reintentar sin necesidad.
- El contador de `Solo Latino` significa **verificados / comprobados**, no el total de fuentes de la película.

---

## Instalar

1. Descarga el APK más reciente desde [Releases](https://github.com/Ardila424/NuvioLatino/releases).
2. Elige el archivo según tu dispositivo:
   - `app-full-armeabi-v7a-release.apk` → cajas de 32 bits (Fire TV Stick, Onn)
   - `app-full-arm64-v8a-release.apk` → cajas de 64 bits (NVIDIA Shield, Google TV 4K)
   - `app-full-universal-release.apk` → si no estás seguro
3. Instala el APK (permite "instalar apps desconocidas").
4. Agrega tus fuentes / addons dentro de la app. **Nuvio Latino no incluye ni aloja contenido ni addons.**

> Nuvio Latino se instala **junto a** Nuvio TV (tiene su propio identificador). No necesitas desinstalar Nuvio TV.

---

## Compilar desde el código

```bash
git clone https://github.com/Ardila424/NuvioLatino.git
cd NuvioLatino
./gradlew :app:assembleFullDebug
```

Necesitas Android Studio, un JDK (Java 17+), y el Android SDK. Las claves de API (TMDB, Trakt, etc.) van en `local.properties` y **no se suben al repositorio**.

Para mantener el fork al día con Nuvio TV:

```bash
git remote add upstream https://github.com/NuvioMedia/NuvioTV.git
git fetch upstream
git checkout -b sync/upstream-YYYY-MM main
git merge upstream/dev
```

Consulta `docs/upstream-sync.md` para el procedimiento completo y la lista de archivos de identidad que siempre hay que re-aplicar.

---

## Ideas en evaluación

- Hoja de pistas de audio por fuente (idioma, codec, canales, default/forced y origen de la clasificación) antes de reproducir.
- Verificación manual de una fuente 1080p concreta (un solo encabezado, sin lote) para no gastar cuota.
- Selección automática de audio latino y etiquetas `Español (Latino)` / `Español (España)` en el menú del reproductor.
- Selector de región latina (MX/AR/CO/CL/PE) para títulos y metadatos TMDb.
- Contadores de depuración: probes por sesión, bytes leídos, aciertos de caché y llamadas a la API.
- Lista de addons recomendados para contenido latino (solo enlaces, no incluidos en la app).

---

## Soporte e issues

- **Reporta bugs o sugiere ideas** en [Issues](https://github.com/Ardila424/NuvioLatino/issues). Incluye tu dispositivo, la versión de Nuvio Latino (Settings → About) y qué estabas reproduciendo.
- Canal de la comunidad: *(próximamente — Discord/Telegram)*.

---

## Apoyar a Nuvio TV

Nuvio Latino está construido sobre Nuvio TV. Si te gusta, apoya al equipo de Nuvio en [nuvio.tv/support](https://nuvio.tv/support). Todas las membresías y donaciones van al proyecto oficial, no a este fork.

---

## Licencia

Este es un fork modificado de [NuvioTV](https://github.com/NuvioMedia/NuvioTV), bajo la [GNU General Public License v3.0](./LICENSE), la misma que NuvioTV. Todos los avisos de copyright, cabeceras de licencia y atribuciones del upstream se conservan. Crédito total a los mantenedores y colaboradores de NuvioTV.

Las fuentes, addons y servicios de terceros pertenecen a sus respectivos dueños. Nuvio Latino no aloja, distribuye ni controla contenido. Úsalo solo donde el contenido y el acceso estén permitidos.

---

## English

**Nuvio Latino** is an **unofficial community fork** of [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) focused on one thing: **finding Latin American Spanish audio.**

It reads the real container header (MKV/MP4/HLS) before playback — the first 256 KB, plus a 256 KB tail for MP4 files whose `moov` is at the end — and labels each source:

- `LATINO ✓` — verified from track metadata (`es-419`, `LA`, `LATAM`, "Latino", etc.)
- `LATINO ?` — only the release name suggests Latino; unverified
- `ESPAÑOL` — Spanish audio found, region not confirmed
- `SIN LATINO` — the header has no Spanish track (hidden when a Spanish badge is present)

Other features:

- `Solo Latino` (verified only; chip shows verified/probed and can verify more cached REMUX), `Español / Latino` (name-based for 1080p), and `Mejor con Latino` sorting.
- Compact technical badges (`4K`, `DV`, `HDR10`, `ATMOS`, `DTS`, `5.1/7.1`, `HEVC`, `REMUX`, size) inline with the title; they wrap only when they do not fit.
- Real player badges from the active tracks: resolution, HDR, audio format, channels and language. No "now playing" label.
- Probe safety: cached debrid only, 8 REMUX per batch, concurrency 2, 4.5 s timeout, 256 KB header reads, `Retry-After`-aware cooldown, 7-day verified cache.
- **No content or addons are bundled or hosted.** Source discovery depends entirely on the addons you install. No affiliation with the official NuvioTV project. **GPL-3.0**, all upstream notices preserved.
