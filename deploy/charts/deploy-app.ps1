# 应用镜像构建 + Helm 升级（backend / frontend）
# 与 deploy-app.sh 同一套流程，给 Windows PowerShell / Docker Desktop 用。
#
# 用法:
#   .\deploy-app.ps1 backend
#   .\deploy-app.ps1 frontend
#   .\deploy-app.ps1 all
#   .\deploy-app.ps1 build backend
#   .\deploy-app.ps1 upgrade backend
#   .\deploy-app.ps1 status
#
# 环境变量同 deploy-app.sh：TAG / PROFILE / NAMESPACE / JWT_SECRET / SKIP_IMPORT / K3S_CONTAINER / HEALTH_URL

[CmdletBinding()]
param(
    [Parameter(Position = 0)][string]$Command = "",
    [Parameter(Position = 1)][string]$Target = ""
)

$ErrorActionPreference = "Stop"

$ChartsDir = $PSScriptRoot
$RepoRoot = (Resolve-Path (Join-Path $ChartsDir "..\..")).Path
$Namespace = if ($env:NAMESPACE) { $env:NAMESPACE } else { "devops" }
$JwtSecret = if ($env:JWT_SECRET) { $env:JWT_SECRET } else { "desktop-dev-jwt-secret" }
$ImageBackend = if ($env:IMAGE_BACKEND) { $env:IMAGE_BACKEND } else { "hfwas/devops-backend" }
$ImageFrontend = if ($env:IMAGE_FRONTEND) { $env:IMAGE_FRONTEND } else { "hfwas/devops-frontend" }
$HealthUrl = if ($env:HEALTH_URL) { $env:HEALTH_URL } else { "http://localhost:30889/health/check" }
$RolloutTimeout = if ($env:ROLLOUT_TIMEOUT) { $env:ROLLOUT_TIMEOUT } else { "180s" }

function Write-Log([string]$Message) {
    Write-Host ("[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $Message)
}

function Die([string]$Message) {
    Write-Log "ERROR: $Message"
    exit 1
}

function Show-Usage {
    foreach ($line in (Get-Content -LiteralPath $PSCommandPath -Encoding UTF8)) {
        if ($line.StartsWith("#")) {
            Write-Host ($line -replace '^# ?', '')
            continue
        }
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        break
    }
}

function Require-Cmd([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        Die "missing command: $Name"
    }
}

function Clear-ClusterProxy {
    foreach ($key in @("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy")) {
        Remove-Item "Env:$key" -ErrorAction SilentlyContinue
    }
    $env:NO_PROXY = if ($env:NO_PROXY) { $env:NO_PROXY } else { "*" }
    $env:no_proxy = $env:NO_PROXY
}

function Find-K3s {
    if ($env:K3S_CONTAINER) { return $env:K3S_CONTAINER }
    docker ps --format "{{.Names}}" | Where-Object { $_ -match "k3s" } | Select-Object -First 1
}

function Detect-Profile {
    if ($env:PROFILE) { return $env:PROFILE }
    $ctx = ""
    try { $ctx = (kubectl config current-context 2>$null) } catch { $ctx = "" }
    if ($ctx -match "^docker-desktop") { return "desktop" }
    if (Find-K3s) { return "k3s" }
    return "desktop"
}

function Overlay-File([string]$Chart, [string]$Profile) {
    $specific = Join-Path $ChartsDir "$Chart\values-$Profile.yaml"
    if (Test-Path $specific) { return $specific }
    $desktop = Join-Path $ChartsDir "$Chart\values-desktop.yaml"
    if (Test-Path $desktop) { return $desktop }
    Die "missing values overlay for $Chart profile=$Profile"
}

function Default-Tag {
    Get-Date -Format "yyyyMMdd-HHmm"
}

function Invoke-Native([string]$File, [string[]]$CmdArgs) {
    & $File @CmdArgs
    if ($LASTEXITCODE -ne 0) {
        Die "$File failed (exit $LASTEXITCODE)"
    }
}

function Build-Backend([string]$Tag) {
    Write-Log "=== docker build ${ImageBackend}:$Tag ==="
    Invoke-Native docker @(
        "build", "--pull=false",
        "-t", "${ImageBackend}:latest",
        "-t", "${ImageBackend}:$Tag",
        "-f", (Join-Path $RepoRoot "backend\Dockerfile"),
        $RepoRoot
    )
}

function Build-Frontend([string]$Tag) {
    Write-Log "=== docker build ${ImageFrontend}:$Tag ==="
    Invoke-Native docker @(
        "build", "--pull=false",
        "-t", "${ImageFrontend}:latest",
        "-t", "${ImageFrontend}:$Tag",
        (Join-Path $RepoRoot "frontend")
    )
}

function Import-K3sImages([string]$Tag, [string[]]$Names) {
    $ctn = Find-K3s
    if (-not $ctn) { Die "k3s container not found; set K3S_CONTAINER" }
    Write-Log "=== import images into k3s containerd ($ctn) ==="
    $refs = foreach ($name in $Names) { @("${name}:$Tag", "${name}:latest") }
    docker save @refs | docker exec -i $ctn ctr -n k8s.io images import -
    if ($LASTEXITCODE -ne 0) { Die "ctr import failed" }
}

function Upgrade-Backend([string]$Tag, [string]$Profile) {
    $overlay = Overlay-File "backend" $Profile
    Write-Log "=== helm upgrade devops-backend (tag=$Tag profile=$Profile) ==="
    Invoke-Native helm @(
        "upgrade", "--install", "devops-backend", (Join-Path $ChartsDir "backend"),
        "--namespace", $Namespace,
        "--create-namespace",
        "--values", (Join-Path $ChartsDir "backend\values.yaml"),
        "--values", $overlay,
        "--set", "config.jwtSecret=$JwtSecret",
        "--set", "image.tag=$Tag"
    )
    Invoke-Native kubectl @("-n", $Namespace, "rollout", "status", "deploy/devops-backend", "--timeout=$RolloutTimeout")
}

function Upgrade-Frontend([string]$Tag, [string]$Profile) {
    $overlay = Overlay-File "frontend" $Profile
    Write-Log "=== helm upgrade devops-frontend (tag=$Tag profile=$Profile) ==="
    Invoke-Native helm @(
        "upgrade", "--install", "devops-frontend", (Join-Path $ChartsDir "frontend"),
        "--namespace", $Namespace,
        "--create-namespace",
        "--values", (Join-Path $ChartsDir "frontend\values.yaml"),
        "--values", $overlay,
        "--set", "image.tag=$Tag"
    )
    Invoke-Native kubectl @("-n", $Namespace, "rollout", "status", "deploy/devops-frontend", "--timeout=$RolloutTimeout")
}

function Check-BackendHealth {
    for ($i = 0; $i -lt 30; $i++) {
        try {
            $r = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200) {
                Write-Log "health: HTTP $($r.StatusCode) $HealthUrl"
                return
            }
        } catch {
            # retry
        }
        Start-Sleep -Seconds 2
    }
    Write-Log "WARN: health not 200: $HealthUrl"
}

function Show-Status {
    Require-Cmd kubectl
    Require-Cmd helm
    Clear-ClusterProxy
    Invoke-Native helm @("-n", $Namespace, "list")
    Write-Host ""
    Invoke-Native kubectl @("-n", $Namespace, "get", "deploy,pods,svc", "-o", "wide")
}

function Normalize-Target([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return "all" }
    $allowed = @("backend", "frontend", "all")
    if ($allowed -contains $Value) { return $Value }
    Die "unknown target: $Value (backend|frontend|all)"
}

function Invoke-Build([string]$BuildTarget, [string]$Tag) {
    Require-Cmd docker
    switch ($BuildTarget) {
        "backend" { Build-Backend $Tag }
        "frontend" { Build-Frontend $Tag }
        "all" { Build-Backend $Tag; Build-Frontend $Tag }
    }
}

function Invoke-ImportIfNeeded([string]$BuildTarget, [string]$Tag, [string]$Profile) {
    if ($Profile -ne "k3s" -or $env:SKIP_IMPORT -eq "1") { return }
    Require-Cmd docker
    switch ($BuildTarget) {
        "backend" { Import-K3sImages $Tag @($ImageBackend) }
        "frontend" { Import-K3sImages $Tag @($ImageFrontend) }
        "all" { Import-K3sImages $Tag @($ImageBackend, $ImageFrontend) }
    }
}

function Invoke-Upgrade([string]$BuildTarget, [string]$Tag, [string]$Profile) {
    Require-Cmd helm
    Require-Cmd kubectl
    Clear-ClusterProxy
    switch ($BuildTarget) {
        "backend" {
            Upgrade-Backend $Tag $Profile
            Check-BackendHealth
        }
        "frontend" {
            Upgrade-Frontend $Tag $Profile
        }
        "all" {
            Upgrade-Backend $Tag $Profile
            Upgrade-Frontend $Tag $Profile
            Check-BackendHealth
        }
    }
}

if ($Command -eq "" -or $Command -eq "-h" -or $Command -eq "--help" -or $Command -eq "help") {
    Show-Usage
    exit 0
}

if ($Command -eq "status") {
    Show-Status
    exit 0
}

$profile = Detect-Profile

if ($Command -eq "build") {
    $t = Normalize-Target $Target
    $tag = if ($env:TAG) { $env:TAG } else { Default-Tag }
    Write-Log "profile=$profile tag=$tag"
    Invoke-Build $t $tag
    Invoke-ImportIfNeeded $t $tag $profile
    Write-Log "done: target=$t tag=$tag profile=$profile namespace=$Namespace"
    exit 0
}

if ($Command -eq "upgrade") {
    $t = Normalize-Target $Target
    $tag = if ($env:TAG) { $env:TAG } else { "latest" }
    Write-Log "profile=$profile tag=$tag"
    Invoke-Upgrade $t $tag $profile
    Write-Log "done: target=$t tag=$tag profile=$profile namespace=$Namespace"
    exit 0
}

$appTargets = @("backend", "frontend", "all")
if ($appTargets -contains $Command) {
    $tag = if ($env:TAG) { $env:TAG } else { Default-Tag }
    Write-Log "profile=$profile tag=$tag"
    Invoke-Build $Command $tag
    Invoke-ImportIfNeeded $Command $tag $profile
    Invoke-Upgrade $Command $tag $profile
    Write-Log "done: target=$Command tag=$tag profile=$profile namespace=$Namespace"
    exit 0
}

Show-Usage
Die "unknown command: $Command"
