# Start AI-MES backend: load .env -> mvn package -> java -jar
param(
    [switch]$SkipBuild
)

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$envFile = Join-Path $root ".env"
$backendDir = Join-Path $root "backend"
$jarPath = Join-Path $backendDir "target\ai-mes-backend-1.0.0.jar"

# load .env
if (Test-Path $envFile) {
    Get-Content $envFile -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line -match "^([^=]+)=(.*)$") {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            if ($value) {
                Set-Item -Path "Env:$name" -Value $value
            }
        }
    }
    Write-Host "[env] loaded .env"
} else {
    Write-Host "[env] .env not found"
}

if (-not $env:COZE_API_TOKEN) {
    Write-Host "[warn] COZE_API_TOKEN empty -> Coze demo mode"
}

$env:SPRING_PROFILES_ACTIVE = "dev"
Set-Location $backendDir

if (-not $SkipBuild -or -not (Test-Path $jarPath)) {
    Write-Host "[build] mvn package -DskipTests ..."
    mvn -q package "-DskipTests"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (-not (Test-Path $jarPath)) {
    Write-Host "[error] jar not found: $jarPath"
    exit 1
}

Write-Host "[run] java -jar $jarPath"
& java -jar $jarPath
