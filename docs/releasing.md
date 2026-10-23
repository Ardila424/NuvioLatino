# Publicar una versión

Hay dos formas. La primera funciona hoy con la firma de desarrollo. La segunda es la recomendada cuando tengas una clave de release definitiva.

## Opción A — un comando (firma de desarrollo)

```powershell
.\scripts\publish-release.ps1
```

El script:

1. Incrementa `versionCode` y `versionName` en `app/build.gradle.kts` (`1.0.0-latino.N` → `1.0.0-latino.N+1`).
2. Compila `:app:assembleFullRelease` con `CI_USE_DEBUG_SIGNING=true`.
3. Hace commit del cambio de versión y sube `main`.
4. Crea el release en GitHub, sube los tres APK y lo publica.

Opciones:

```powershell
# Versión concreta
.\scripts\publish-release.ps1 -Version 1.0.0-latino.5

# Notas de release desde un archivo Markdown
.\scripts\publish-release.ps1 -NotesFile .\release-notes.md

# Solo compilar y commitear, sin subir
.\scripts\publish-release.ps1 -SkipUpload

# Ver qué versión se publicaría, sin tocar nada
.\scripts\publish-release.ps1 -DryRun
```

El token de GitHub se toma de `GH_TOKEN`, `GITHUB_TOKEN` o de las credenciales que ya usa Git para `github.com`. No se guarda en el repositorio.

Si el build falla, el script restaura la versión anterior.

## Opción B — GitHub Actions con clave de release

La firma de desarrollo sirve para probar, pero no es recomendable para una app pública: si cambias de clave, Android pide desinstalar y reinstalar una vez.

### 1. Crear la clave

Guárdala fuera del repositorio y no la subas nunca.

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" `
  -genkeypair -v `
  -keystore "$env:USERPROFILE\Documents\nuviolatino-release.jks" `
  -alias nuviolatino `
  -keyalg RSA -keysize 4096 -validity 10000
```

### 2. Preparar los secretos

Convierte la clave y tu `local.properties` a Base64:

```powershell
[Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("$env:USERPROFILE\Documents\nuviolatino-release.jks")
) | Set-Clipboard
```

```powershell
[Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("local.properties")
) | Set-Clipboard
```

En GitHub: **Settings → Secrets and variables → Actions → New repository secret**.

| Secreto | Valor |
|---|---|
| `NUVIO_RELEASE_KEYSTORE_BASE64` | La clave `.jks` en Base64 |
| `NUVIO_RELEASE_KEY_ALIAS` | `nuviolatino` |
| `NUVIO_RELEASE_KEY_PASSWORD` | La contraseña de la clave |
| `NUVIO_RELEASE_STORE_PASSWORD` | La contraseña del keystore |
| `LOCAL_PROPERTIES_BASE64` | Tu `local.properties` en Base64 (TMDB/Trakt, etc.) |

### 3. Publicar

El workflow es manual:

**Actions → Build Nuvio Latino release → Run workflow**

- Modo `publish-release`
- Tag: `v1.0.0-latino.N`

También puedes usar `draft-release` para revisar los APK antes de publicarlos.

Si el secreto del keystore existe, el build usa la firma de release; si no, cae en la firma de desarrollo.

## Notas

- El tag debe empezar por `v` (`v1.0.0-latino.3`).
- No subas nunca el `.jks` ni las contraseñas al repositorio.
- Si pierdes la clave, no podrás actualizar la app publicada; los usuarios tendrían que desinstalar y reinstalar.
- El APK universal pesa más; para la TV conviene el de su arquitectura (`armeabi-v7a` en Onn/Fire TV, `arm64-v8a` en Shield/Google TV 4K).
