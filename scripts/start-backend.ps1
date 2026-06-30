# 从项目根目录 .env 加载环境变量并启动后端
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$envFile = Join-Path $root ".env"

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line -match "^([^=]+)=(.*)$") {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            if ($value) {
                Set-Item -Path "Env:$name" -Value $value
            }
        }
    }
    Write-Host "已加载 .env"
} else {
    Write-Host "未找到 .env，将使用系统环境变量或 application.yml 默认值"
}

if (-not $env:COZE_API_TOKEN) {
    Write-Host "提示: COZE_API_TOKEN 未设置，Coze 将运行在演示模式"
}
if (-not $env:COZE_BOT_ID) {
    Write-Host "提示: COZE_BOT_ID 未设置，请在 .env 中填写 Bot ID 后重启"
}

Set-Location (Join-Path $root "backend")
Write-Host "正在打包后端..."
mvn -q package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "后端打包失败，已停止启动。"
    exit $LASTEXITCODE
}

Write-Host "正在启动后端 JAR..."
java -jar target/ai-mes-backend-1.0.0.jar
