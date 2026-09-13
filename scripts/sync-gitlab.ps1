# 把当前分支已提交代码推到本机 GitLab
#
# 用法:
#   .\scripts\sync-gitlab.ps1              # 推当前分支
#   .\scripts\sync-gitlab.ps1 status       # 只看本地 vs GitLab，不推
#   .\scripts\sync-gitlab.ps1 -Branch dev  # 指定分支
#
# 环境变量:
#   GITLAB_URL       默认 http://localhost:30880
#   GITLAB_PROJECT   默认 root/hfwas-devops
#   GITLAB_USER      默认 root
#   GITLAB_PASSWORD  默认读 deploy/charts/gitlab/values.yaml 的 rootPassword
#   GITLAB_TOKEN     若设置则优先于密码（推荐 PAT）
#   GITLAB_REMOTE    默认 gitlab
#
# 只推已提交内容。工作区未提交改动不会上去。
# 网页请打开 /-/commits/<分支>，不要打开某个 commit SHA。

[CmdletBinding()]
param(
    [Parameter(Position = 0)][string]$Command = "push",
    [string]$Branch = "",
    [string]$Remote = ""
)

$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -ge 7) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ValuesFile = Join-Path $RepoRoot "deploy\charts\gitlab\values.yaml"

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

function Clear-LocalProxy {
    foreach ($key in @("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy")) {
        Remove-Item "Env:$key" -ErrorAction SilentlyContinue
    }
    $env:NO_PROXY = "*"
    $env:no_proxy = "*"
}

function Read-RootPassword {
    if (-not (Test-Path -LiteralPath $ValuesFile)) {
        Die "找不到 $ValuesFile，无法读取默认密码。可设 GITLAB_PASSWORD 或 GITLAB_TOKEN。"
    }
    $line = Select-String -LiteralPath $ValuesFile -Pattern '^rootPassword:' | Select-Object -First 1
    $pwd = $null
    if ($line) {
        $parts = $line.Line.Split('"')
        if ($parts.Count -ge 2) { $pwd = $parts[1] }
    }
    if ([string]::IsNullOrWhiteSpace($pwd)) {
        Die "values.yaml 里没有 rootPassword。可设 GITLAB_PASSWORD 或 GITLAB_TOKEN。"
    }
    return $pwd
}

function Get-Auth {
    $url = if ($env:GITLAB_URL) { $env:GITLAB_URL.TrimEnd("/") } else { "http://localhost:30880" }
    $project = if ($env:GITLAB_PROJECT) { $env:GITLAB_PROJECT.Trim("/") } else { "root/hfwas-devops" }
    $user = if ($env:GITLAB_USER) { $env:GITLAB_USER } else { "root" }
    $token = $env:GITLAB_TOKEN
    if ($token) {
        return [pscustomobject]@{
            Url      = $url
            Project  = $project
            User     = "oauth2"
            Secret   = $token
            UseToken = $true
        }
    }
    $pass = if ($env:GITLAB_PASSWORD) { $env:GITLAB_PASSWORD } else { Read-RootPassword }
    return [pscustomobject]@{
        Url      = $url
        Project  = $project
        User     = $user
        Secret   = $pass
        UseToken = $false
    }
}

function Get-BasicHeaders($Auth) {
    $pair = "{0}:{1}" -f $Auth.User, $Auth.Secret
    $b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
    @{ Authorization = "Basic $b64"; Accept = "application/json" }
}

function Test-GitLab($Auth) {
    $signIn = "$($Auth.Url)/users/sign_in"
    try {
        $resp = curl.exe -sS -o NUL -w "%{http_code}" --max-time 8 $signIn
    } catch {
        Die "GitLab 连不上 $($Auth.Url) 。确认 Pod 已就绪: kubectl -n gitlab get pods"
    }
    if ($resp -ne "200") {
        Die "GitLab HTTP $resp ($signIn). 确认 http://localhost:30880 能打开."
    }
}

function Ensure-Project($Auth) {
    $encoded = [Uri]::EscapeDataString($Auth.Project)
    $api = "$($Auth.Url)/api/v4/projects/$encoded"
    try {
        $null = Invoke-RestMethod -Uri $api -Headers (Get-BasicHeaders $Auth) -TimeoutSec 15
        Write-Log "项目已存在: $($Auth.Project)"
        return
    } catch {
        $code = 0
        if ($_.Exception.Response) {
            $code = [int]$_.Exception.Response.StatusCode
        }
        if ($code -ne 404) {
            Write-Log "查询项目失败 HTTP $code，继续尝试 git push。"
            return
        }
    }

    $name = ($Auth.Project -split "/")[-1]
    $body = @{
        name                   = $name
        path                   = $name
        visibility             = "private"
        initialize_with_readme = $false
    } | ConvertTo-Json
    try {
        $null = Invoke-RestMethod -Method Post -Uri "$($Auth.Url)/api/v4/projects" `
            -Headers (Get-BasicHeaders $Auth) -Body $body -ContentType "application/json" -TimeoutSec 20
        Write-Log "已创建项目: $($Auth.Project)"
    } catch {
        Write-Log "自动建库失败，若 push 报 repository not found，请先在 GitLab 网页建空项目 $($Auth.Project)。"
    }
}

function Ensure-Remote([string]$RemoteName, $Auth) {
    $pushUrl = "$($Auth.Url)/$($Auth.Project).git"
    $existing = git remote 2>$null | Where-Object { $_ -eq $RemoteName }
    if (-not $existing) {
        git remote add $RemoteName $pushUrl
        Write-Log "已添加远程 $RemoteName -> $pushUrl"
        return
    }
    $current = (git remote get-url $RemoteName).Trim()
    if ($current -ne $pushUrl) {
        Write-Log "远程 $RemoteName 当前是 $current"
        Write-Log "本次仍推到该地址。要改成 $pushUrl 可执行: git remote set-url $RemoteName $pushUrl"
    }
}

function Invoke-GitLabGit {
    param(
        [string[]]$GitArgs,
        $Auth
    )
    $askCmd = Join-Path $PSScriptRoot "gitlab-askpass.cmd"
    if (-not (Test-Path -LiteralPath $askCmd)) {
        Die "找不到 $askCmd"
    }
    $oldAskpass = $env:GIT_ASKPASS
    $oldPrompt = $env:GIT_TERMINAL_PROMPT
    $oldGcm = $env:GCM_INTERACTIVE
    $oldUser = $env:HFWAS_GITLAB_USER
    $oldSecret = $env:HFWAS_GITLAB_SECRET
    try {
        $env:GIT_ASKPASS = $askCmd
        $env:GIT_TERMINAL_PROMPT = "0"
        $env:GCM_INTERACTIVE = "never"
        $env:HFWAS_GITLAB_USER = $Auth.User
        $env:HFWAS_GITLAB_SECRET = $Auth.Secret
        & git -c credential.helper= -c "core.askPass=$askCmd" @GitArgs
        if ($LASTEXITCODE -ne 0) {
            Die ("git {0} 失败 (exit {1})" -f ($GitArgs -join " "), $LASTEXITCODE)
        }
    } finally {
        $env:GIT_ASKPASS = $oldAskpass
        $env:GIT_TERMINAL_PROMPT = $oldPrompt
        $env:GCM_INTERACTIVE = $oldGcm
        $env:HFWAS_GITLAB_USER = $oldUser
        $env:HFWAS_GITLAB_SECRET = $oldSecret
    }
}

function Get-CurrentBranch {
    $b = (git rev-parse --abbrev-ref HEAD).Trim()
    if (-not $b -or $b -eq "HEAD") {
        Die "当前不在命名分支上，请先 checkout 一个分支。"
    }
    return $b
}

function Show-DirtyWarning {
    $porcelain = git status --porcelain
    if (-not $porcelain) { return }
    Write-Host ""
    Write-Log "工作区有未提交改动，本次不会推上去："
    git status --short
    Write-Host ""
}

function Show-Status($Auth, [string]$RemoteName, [string]$BranchName) {
    $head = (git rev-parse --short HEAD).Trim()
    Write-Log "GitLab: $($Auth.Url)/$($Auth.Project)"
    Write-Log "本地分支: $BranchName ($head)"
    $remoteRef = "refs/remotes/$RemoteName/$BranchName"
    $remoteSha = git rev-parse --verify --short $remoteRef 2>$null
    if ($LASTEXITCODE -eq 0 -and $remoteSha) {
        $remoteSha = $remoteSha.Trim()
        Write-Log "GitLab $BranchName : $remoteSha"
        if ($remoteSha -eq $head) {
            Write-Log "已与 GitLab 对齐。"
        } else {
            Write-Log "不一致。运行 .\scripts\sync-gitlab.ps1 推送。"
        }
    } else {
        Write-Log "本地还没有 $RemoteName/$BranchName 跟踪分支。"
    }
    Write-Log "提交页: $($Auth.Url)/$($Auth.Project)/-/commits/$BranchName"
    Show-DirtyWarning
}

Set-Location $RepoRoot
Clear-LocalProxy

if ($Command -in @("-h", "--help", "help")) {
    Show-Usage
    exit 0
}

if ($Command -notin @("push", "status")) {
    Die "未知命令: $Command 。用 push 或 status。"
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Die "未找到 git" }

$auth = Get-Auth
$remoteName = if ($Remote) { $Remote } elseif ($env:GITLAB_REMOTE) { $env:GITLAB_REMOTE } else { "gitlab" }
$branchName = if ($Branch) { $Branch } else { Get-CurrentBranch }

Test-GitLab $auth
Ensure-Remote $remoteName $auth

if ($Command -eq "status") {
    Invoke-GitLabGit -Auth $auth -GitArgs @("fetch", $remoteName, "--prune")
    Show-Status $auth $remoteName $branchName
    exit 0
}

Ensure-Project $auth
Show-DirtyWarning
$head = (git rev-parse --short HEAD).Trim()
Write-Log "推送 $head -> ${remoteName}/$branchName"
Invoke-GitLabGit -Auth $auth -GitArgs @("push", $remoteName, "HEAD:${branchName}")
Invoke-GitLabGit -Auth $auth -GitArgs @("fetch", $remoteName, $branchName)

Write-Host ""
Write-Log "完成: $head -> ${remoteName}/$branchName"
Write-Log "提交页: $($auth.Url)/$($auth.Project)/-/commits/$branchName"
Write-Log "代码树: $($auth.Url)/$($auth.Project)/-/tree/$branchName"
Show-DirtyWarning
