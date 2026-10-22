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

Nuvio Latino **verifica el audio real** de cada fuente (leyendo el encabezado del contenedor: MKV, MP4 o HLS) antes de que presiones reproducir, y te muestra cuáles tienen **audio latino verificado**, cuáles solo Castellano, y cuáles no tienen español. Así dejas de adivinar y empiezas a reproducir.

No es un competidor de Nuvio TV y no pretende reemplazarlo. Es un banco de pruebas comunitario: las ideas sólidas y bien probadas pueden, eventualmente, proponerse al proyecto oficial.

> **Nuvio Latino no está afiliado ni respaldado por el proyecto Nuvio TV.** Por favor **no reportes problemas de esta versión** en el GitHub o Discord oficial de Nuvio — no pueden ayudarte con cambios que no hicieron.

---

## ¿Para quién es?

Para personas que quieren **escuchar el doblaje latino** de sus películas y series, y que hoy pierden tiempo abriendo fuente por fuente para descubrir que "LATINO" en el nombre era solo publicidad.

Funciona en los mismos dispositivos que Nuvio TV (Fire TV Stick, Onn, Mi Box, Chromecast / Google TV, y cajas Android TV en general), incluidas cajas económicas de 1.5–2 GB de RAM.

---

## Qué agrega este fork

- **Audio latino verificado** — detección real del audio latino leyendo el encabezado EBML (MKV), y próximamente MP4 y HLS, sin descargar el video. Los falsos positivos del nombre del archivo se eliminan.
- **Insignias y filtros** — insignia "✅ LATINO (Verificado)" en cada fuente, y chips de filtro rápido: `Solo Latino`, `Latino + Castellano`, `Remux`, `4K`.
- **Orden "Mejor con Latino"** — las fuentes con audio latino verificado y mejor calidad (Remux, 4K, HDR) quedan arriba.
- **Títulos alternativos en español latino** — metadatos TMDb en español (es-MX), sincronizados con el idioma de la app.
- **Subtítulos por defecto en español** — preferencia de subtítulos Español + Inglés secundario (ver Settings → Playback → Subtitles).
- **Rendimiento en cajas de gama baja** — navegación a 60 fps, bitmaps por hardware, y carga diferida del héroe.
- **Actualizador propio** — actualizaciones dentro de la app desde los Releases de este repositorio.

---

## Instalar

1. Descarga el APK más reciente desde [Releases](https://github.com/Ardila424/NuvioLatino/releases).
2. Elige el archivo según tu dispositivo:
   - `app-full-armeabi-v7a-release.apk` → cajas de 32 bits (Fire TV Stick, Onn)
   - `app-full-arm64-v8a-release.apk` → cajas de 64 bits (NVIDIA Shield, Google TV 4K)
   - `app-full-universal-release.apk` → si no estás seguro
3. Instala el APK (permite "instalar apps desconocidas").
4. Agrega tus fuentes / add-ons dentro de la app. **Nuvio Latino no incluye ni aloja contenido ni add-ons.**

> ⚠️ Nuvio Latino se instala **junto a** Nuvio TV (tiene su propio identificador). No necesitas desinstalar Nuvio TV.

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

## Soporte e issues

- **Reporta bugs o sugiere ideas** en [Issues](https://github.com/Ardila424/NuvioLatino/issues). Incluye tu dispositivo, la versión de Nuvio Latino (Settings → About) y qué estabas reproduciendo.
- Canal de la comunidad: *(próximamente — Discord/Telegram)*.

---

## Apoyar a Nuvio TV

Nuvio Latino está construido sobre Nuvio TV. Si te gusta, apoya al equipo de Nuvio en [nuvio.tv/support](https://nuvio.tv/support). Todas las membresías y donaciones van al proyecto oficial, no a este fork.

---

## Licencia

Este es un fork modificado de [NuvioTV](https://github.com/NuvioMedia/NuvioTV), bajo la [GNU General Public License v3.0](./LICENSE), la misma que NuvioTV. Todos los avisos de copyright, cabeceras de licencia y atribuciones del upstream se conservan. Crédito total a los mantenedores y colaboradores de NuvioTV.

Las fuentes, add-ons y servicios de terceros pertenecen a sus respectivos dueños. Nuvio Latino no aloja, distribuye ni controla contenido. Úsalo solo donde el contenido y el acceso estén permitidos.

---

## English

**Nuvio Latino** is an **unofficial community fork** of [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) focused on one thing: **finding Latin American Spanish audio.** It verifies each source's real audio tracks by reading the container header (MKV now, MP4 and HLS coming) before you press play, so you know which streams actually have verified Latino audio — and the best-quality one rises to the top.

- **Not affiliated with or endorsed by the NuvioTV project.** Do not report issues from this build to the official Nuvio channels.
- **No content or add-ons are bundled or hosted.** Bring your own sources.
- **GPL-3.0**, all upstream notices preserved.
