[CmdletBinding()]
param(
    [string]$Version,
    [string]$NotesFile,
    [switch]$SkipUpload,
    [switch]$RealSigning,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$gradlePath = Join-Path $repoRoot 'app\build.gradle.kts'
$owner = 'Ardila424'
$repo = 'NuvioLatino'
$gradleBackup = $null

function Fail([string]$Message) {
    throw $Message
}

Push-Location $repoRoot
try {
    if (-not (Test-Path $gradlePath)) {
        Fail "No encuentro app\build.gradle.kts. Ejecuta el script desde el repositorio."
    }

    $dirty = @(git status --porcelain)
    if ($dirty.Count -gt 0) {
        Fail "Hay cambios sin commitear. Commitea o guarda esos cambios antes de publicar.`n$($dirty -join "`n")"
    }

    $gradle = [IO.File]::ReadAllText($gradlePath)
    $codeRegex = [regex]'(?m)^(\s*versionCode\s*=\s*)(\d+)'
    $nameRegex = [regex]'(?m)^(\s*versionName\s*=\s*")([^"]+)(")'
    $codeMatch = $codeRegex.Match($gradle)
    $nameMatch = $nameRegex.Match($gradle)
    if (-not $codeMatch.Success -or -not $nameMatch.Success) {
        Fail "No pude leer versionCode/versionName de app\build.gradle.kts."
    }

    $newCode = [int]$codeMatch.Groups[2].Value + 1
    if ($Version) {
        $newName = $Version.Trim().TrimStart('v')
    } else {
        $currentName = $nameMatch.Groups[2].Value
        $parts = [regex]::Match($currentName, '^(.*-latino\.)(\d+)$')
        if ($parts.Success) {
            $newName = $parts.Groups[1].Value + ([int]$parts.Groups[2].Value + 1)
        } else {
            Fail "No pude incrementar '$currentName'. Pasa -Version 1.0.0-latino.N."
        }
    }
    if ($newName -notmatch '^\d+\.\d+\.\d+') {
        Fail "La versión '$newName' no parece válida. Usa algo como 1.0.0-latino.3."
    }

    $tag = "v$newName"
    if ($DryRun) {
        Write-Host "Dry run: would publish $tag (versionCode $newCode) with debug signing." -ForegroundColor Yellow
        return
    }

    $remoteTag = @(git ls-remote --tags origin "refs/tags/$tag")
    if ($remoteTag.Count -gt 0) {
        Fail "El tag $tag ya existe en origin. Usa otra versión."
    }

    $gradleBackup = $gradle
    $gradle = $codeRegex.Replace($gradle, "`${1}$newCode", 1)
    $gradle = $nameRegex.Replace($gradle, "`${1}$newName`${3}", 1)
    [IO.File]::WriteAllText($gradlePath, $gradle, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Version: $newName (versionCode $newCode)" -ForegroundColor Cyan

    if (-not $env:JAVA_HOME) {
        $candidate = 'C:\Program Files\Android\Android Studio\jbr'
        if (Test-Path $candidate) { $env:JAVA_HOME = $candidate }
    }
    if ($RealSigning) {
        Remove-Item Env:CI_USE_DEBUG_SIGNING -ErrorAction SilentlyContinue
    } else {
        $env:CI_USE_DEBUG_SIGNING = 'true'
    }

    Write-Host "Building release APKs..." -ForegroundColor Cyan
    & .\gradlew.bat :app:assembleFullRelease --console=plain
    if ($LASTEXITCODE -ne 0) {
        [IO.File]::WriteAllText($gradlePath, $gradleBackup, (New-Object System.Text.UTF8Encoding($false)))
        Fail "Gradle falló. Restauré la versión anterior."
    }

    $releaseDir = Join-Path $repoRoot 'app\build\outputs\apk\full\release'
    $apks = @(
        (Join-Path $releaseDir 'app-full-armeabi-v7a-release.apk'),
        (Join-Path $releaseDir 'app-full-arm64-v8a-release.apk'),
        (Join-Path $releaseDir 'app-full-universal-release.apk')
    )
    foreach ($apk in $apks) {
        if (-not (Test-Path $apk)) { Fail "Falta el APK: $apk" }
    }

    git add $gradlePath
    git commit -m "release: $tag"
    if ($LASTEXITCODE -ne 0) { Fail "No pude crear el commit de la versión." }
    git push origin main
    if ($LASTEXITCODE -ne 0) { Fail "No pude subir main." }

    if ($SkipUpload) {
        Write-Host "Build y commit listos. Subida omitida (-SkipUpload)." -ForegroundColor Yellow
        return
    }

    $token = $env:GH_TOKEN
    if (-not $token) { $token = $env:GITHUB_TOKEN }
    if (-not $token) {
        $cred = @('protocol=https', 'host=github.com', '') | git credential fill 2>$null
        $line = $cred | Where-Object { $_ -like 'password=*' } | Select-Object -First 1
        if ($line) { $token = $line.Substring(9) }
    }
    if (-not $token) {
        Fail "No hay token de GitHub. Define GH_TOKEN o configura las credenciales de git."
    }

    $headers = @{
        Authorization = "token $token"
        'User-Agent' = 'NuvioLatino'
        Accept = 'application/vnd.github+json'
    }
    $api = "https://api.github.com/repos/$owner/$repo"

    if ($NotesFile) {
        $notesPath = (Resolve-Path $NotesFile).Path
        $notes = [IO.File]::ReadAllText($notesPath)
    } else {
        $notes = @"
## Nuvio Latino $newName

- Audio latino confirmado en la tarjeta o en el archivo.
- Filtro Solo Latino y orden Mejor con Latino.
- Insignias de calidad y audio.
- En inglés, experiencia Nuvio normal.

**Instalar:** `armeabi-v7a` (Onn / Fire TV), `arm64-v8a` (Shield / Google TV 4K) o `universal`.

No incluye addons ni contenido. Proyecto no oficial, GPL-3.0.
"@
    }

    Write-Host "Creating draft release $tag..." -ForegroundColor Cyan
    $payload = @{
        tag_name = $tag
        target_commitish = 'main'
        name = "Nuvio Latino $tag"
        body = $notes
        draft = $true
        prerelease = $false
    } | ConvertTo-Json -Depth 5
    $release = Invoke-RestMethod -Method Post -Uri "$api/releases" -Headers $headers `
        -Body ([Text.Encoding]::UTF8.GetBytes($payload)) -ContentType 'application/json; charset=utf-8'

    $uploadBase = "https://uploads.github.com/repos/$owner/$repo/releases/$($release.id)/assets"
    $uploadHeaders = @{
        Authorization = "token $token"
        'User-Agent' = 'NuvioLatino'
        'Content-Type' = 'application/octet-stream'
    }
    foreach ($apk in $apks) {
        $name = Split-Path $apk -Leaf
        Write-Host "Uploading $name..." -ForegroundColor Cyan
        $uri = "$uploadBase?name=$([uri]::EscapeDataString($name))"
        Invoke-RestMethod -Method Post -Uri $uri -Headers $uploadHeaders -InFile $apk -TimeoutSec 1200 | Out-Null
    }

    Invoke-RestMethod -Method Patch -Uri "$api/releases/$($release.id)" -Headers $headers `
        -Body (@{ draft = $false } | ConvertTo-Json) -ContentType 'application/json; charset=utf-8' | Out-Null

    # The release API creates the remote tag. Keep a local tag too.
    git tag $tag 2>$null
    Write-Host "Published: https://github.com/$owner/$repo/releases/tag/$tag" -ForegroundColor Green
} finally {
    Pop-Location
}
