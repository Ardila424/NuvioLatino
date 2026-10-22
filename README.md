<div align="center">

  <img src="assets/brand/app_logo_wordmark.png" alt="Nuvio" width="300" />

  <p>
    A free, open-source media app for your phone, your desktop, and the TV you already own.
    <br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Website](https://nuvio.tv) · [GitHub releases](https://github.com/NuvioMedia/NuvioTV/releases/latest) · [Support Nuvio](https://nuvio.tv/support)

</div>

## This fork — Ardila's build

Custom features on top of upstream Nuvio TV:

- **Latino audio detection** — accurate Latin American (Latino) audio detection via a direct EBML header probe, plus an on-demand Remux audio inspector that removes false positives while preserving the Castellano badge.
- **Latino/Remux badges & quick filters** — native badges on streams and one-tap filter chips that auto-select on focus.
- **Latin alternative titles** — TMDb Latin American alternative titles, with TMDb metadata language synced to the app language.
- **Trakt recommendations** — support for the Trakt user recommendations collection.
- **Performance for low-end TVs** — 60fps D-Pad navigation, Coil hardware bitmaps, debounced hero loading, and background stream filtering.
- **Unreleased content filtering** — hides announced/unreleased titles.
- **Fork-specific updater** — in-app updater pointed at this fork, with the debug build labeled.

## Get Nuvio TV

- [Android TV on Google Play](https://play.google.com/store/apps/details?id=com.nuvio.app)
- [Android TV APK](https://github.com/NuvioMedia/NuvioTV/releases/latest)

## Build from source

```bash
git clone https://github.com/NuvioMedia/NuvioTV.git
cd NuvioTV
./gradlew :app:assembleFullDebug
```

Nuvio TV is built with Kotlin, Jetpack Compose, TV Material 3, and Media3. Development requires Android Studio, a JDK, and the Android SDK.

## License

[GNU General Public License v3.0](./LICENSE)
