# 동시 세션 제한(maximumSessions(1)) 재현
#
# 가설: 같은 계정으로 세션을 2개 만들면 먼저 만든 세션이 죽는다.
#       그리고 ott-app / ott-app-2 의 세션 레지스트리가 인메모리라 인스턴스별로 판단이 달라서,
#       라운드로빈 때문에 200/401 이 오락가락할 수 있다.
#
# 실행: powershell -ExecutionPolicy Bypass -File .\loadtest\diag-concurrent.ps1

$ErrorActionPreference = 'Stop'

$Base      = 'https://laputa.kozow.com'
$Email     = 'loadtest0003@loadtest.local'
$Password  = $(if ($env:LT_PASSWORD) { $env:LT_PASSWORD } else { throw 'LT_PASSWORD 환경변수가 필요합니다. seed-users.sql 에 심은 비밀번호를 넣으세요.' })
$EpisodeId = 520

$work = Join-Path $env:TEMP 'ott-conc-diag'
New-Item -ItemType Directory -Force $work | Out-Null
$jarA      = Join-Path $work 'a.txt'
$jarB      = Join-Path $work 'b.txt'
$loginBody = Join-Path $work 'login.json'
$progBody  = Join-Path $work 'prog.json'
foreach ($f in @($jarA, $jarB)) { if (Test-Path $f) { Remove-Item $f } }

Set-Content -Path $loginBody -Value ('{"email":"' + $Email + '","password":"' + $Password + '"}') -Encoding ascii -NoNewline
Set-Content -Path $progBody  -Value '{"positionSec":10,"durationSec":1440}' -Encoding ascii -NoNewline

$redisPw = $null
$envFile = Join-Path $PSScriptRoot '..\.env'
if (Test-Path $envFile) {
    $line = Select-String -Path $envFile -Pattern '^\s*REDIS_PASSWORD\s*=' | Select-Object -First 1
    if ($line) { $redisPw = ($line.Line -split '=', 2)[1].Trim().Trim('"').Trim("'") }
}

function Get-Sid($jar) {
    $l = Get-Content $jar | Where-Object { $_ -match 'JSESSIONID' } | Select-Object -Last 1
    if (-not $l) { return $null }
    $v = ($l -split "`t")[-1]
    try { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($v)) } catch { return $v }
}
function RedisHas($sid) {
    if (-not $redisPw -or -not $sid) { return '?' }
    (docker exec ott-redis redis-cli -a $redisPw --no-auth-warning exists "ott:session:sessions:$sid").Trim()
}
function DoLogin($jar) {
    & curl.exe -s -o NUL -w '%{http_code}' -c "$jar" `
        -H "Origin: $Base" -H 'Content-Type: application/json' `
        -X POST --data-binary "@$loginBody" "$Base/api/auth/login"
}
function DoProgress($jar) {
    & curl.exe -s -o NUL -w '%{http_code}' -b "$jar" -c "$jar" `
        -H "Origin: $Base" -H 'Content-Type: application/json' `
        -X POST --data-binary "@$progBody" "$Base/api/episodes/$EpisodeId/progress"
}

Write-Host "=== 1단계: 세션 A 만들기 (로그인 + 요청 1회로 ID 회전까지 끝냄) ==="
Write-Host "  로그인   HTTP $(DoLogin $jarA)"
Write-Host "  요청     HTTP $(DoProgress $jarA)"
$sidA = Get-Sid $jarA
Write-Host "  세션 A = $sidA  (redis=$(RedisHas $sidA))"

Write-Host ""
Write-Host "=== 2단계: 같은 계정으로 세션 B 만들기 ==="
Write-Host "  로그인   HTTP $(DoLogin $jarB)"
Write-Host "  요청     HTTP $(DoProgress $jarB)"
$sidB = Get-Sid $jarB
Write-Host "  세션 B = $sidB  (redis=$(RedisHas $sidB))"
Write-Host "  세션 A 는 아직 살아있나? redis=$(RedisHas $sidA)"

Write-Host ""
Write-Host "=== 3단계: 세션 A 로 계속 요청해본다 (라운드로빈이면 결과가 흔들릴 수 있음) ==="
Write-Host " #  A쪽HTTP  A의redis   B쪽HTTP"
Write-Host "--- -------  --------   -------"
for ($i = 1; $i -le 8; $i++) {
    $ca = DoProgress $jarA
    $ra = RedisHas $sidA
    $cb = DoProgress $jarB
    "{0,2}    {1}       {2}         {3}" -f $i, $ca, $ra, $cb | Write-Host
    Start-Sleep -Milliseconds 700
}
