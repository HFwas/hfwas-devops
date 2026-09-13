param($prompt = "")
if ($prompt -match "username") {
    [Console]::Out.Write($env:HFWAS_GITLAB_USER)
    exit 0
}
[Console]::Out.Write($env:HFWAS_GITLAB_SECRET)
