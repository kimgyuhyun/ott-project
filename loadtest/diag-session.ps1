# 세션 재사용 진단 스크립트 (k6 배제용)
#
# 한 번 로그인한 뒤 같은 쿠키로 진행률 저장을 N회 반복하면서
#   - HTTP 상태코드
#   - Redis 에 세션 키가 아직 살아있는지 (1=있음, 0=삭제됨)
#   - 세션 ID 가 중간에 바뀌는지
# 를 매 요청마다 찍는다.
#
# 401 이 나는 시점에 redis_exists 가 0 으로 떨어지면 -> 서버가 세션을 무효화한 것(서버 원인 확정).
# 계속 1 인데 401 이면 -> 세션은 살아있고 인증 부착 단계가 문제.
#
# 실행: powershell -ExecutionPolicy Bypass -File .\loadtest\diag-session.ps1

$ErrorActionPreference = 'Stop'

$Base      = 'https://laputa.kozow.com'
$Origin    = $Base
$Email     = 'loadtest0001@loadtest.local'
$Password  = $(if ($env:LT_PASSWORD) { $env:LT_PASSWORD } else { throw 'LT_PASSWORD 환경변수가 필요합니다. seed-users.sql 에 심은 비밀번호를 넣으세요.' })
$EpisodeId = 520
$Count     = 20

$work = Join-Path $env:TEMP 'ott-session-diag'
New-Item -ItemType Directory -Force $work | Out-Null
$jar       = Join-Path $work 'cookies.txt'
$loginBody = Join-Path $work 'login.json'
$progBody  = Join-Path $work 'prog.json'
if (Test-Path $jar) { Remove-Item $jar }

# Redis 비밀번호는 .env 에서 읽고 화면에는 절대 출력하지 않는다
$redisPw = $null
$envFile = Join-Path $PSScriptRoot '..\.env'
if (Test-Path $envFile) {
    $line = Select-String -Path $envFile -Pattern '^\s*REDIS_PASSWORD\s*=' | Select-Object -First 1
    if ($line) { $redisPw = ($line.Line -split '=', 2)[1].Trim().Trim('"').Trim("'") }
}
if (-not $redisPw) { Write-Warning ".env 에서 REDIS_PASSWORD 를 못 읽음 - redis 확인은 건너뜀" }

function Get-SessionId {
    if (-not (Test-Path $jar)) { return $null }
    $l = Get-Content $jar | Where-Object { $_ -match 'JSESSIONID' } | Select-Object -Last 1
    if (-not $l) { return $null }
    $v = ($l -split "`t")[-1]
    # Spring Session 은 쿠키값을 base64 로 인코딩한다. 디코드해야 Redis 키 이름이 나온다.
    try { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($v)) } catch { return $v }
}

function Test-RedisSession($sid) {
    if (-not $redisPw -or -not $sid) { return '?' }
    (docker exec ott-redis redis-cli -a $redisPw --no-auth-warning exists "ott:session:sessions:$sid").Trim()
}

Set-Content -Path $loginBody -Value ('{"email":"' + $Email + '","password":"' + $Password + '"}') -Encoding ascii -NoNewline
Set-Content -Path $progBody  -Value '{"positionSec":10,"durationSec":1440}' -Encoding ascii -NoNewline

Write-Host "[login] $Email"
$code = & curl.exe -s -o NUL -w '%{http_code}' -c "$jar" `
    -H "Origin: $Origin" -H 'Content-Type: application/json' `
    -X POST --data-binary "@$loginBody" "$Base/api/auth/login"
$sid = Get-SessionId
Write-Host "[login] HTTP $code  sid=$sid  redis_exists=$(Test-RedisSession $sid)"
if ($code -ne '200') { Write-Host "로그인 실패 - 중단"; exit 1 }

$prevSid = $sid
Write-Host ""
Write-Host " #  HTTP  redis  session_id"
Write-Host "--- ----  -----  ----------"

for ($i = 1; $i -le $Count; $i++) {
    $code = & curl.exe -s -o NUL -w '%{http_code}' -b "$jar" -c "$jar" `
        -H "Origin: $Origin" -H 'Content-Type: application/json' `
        -X POST --data-binary "@$progBody" "$Base/api/episodes/$EpisodeId/progress"

    $sid    = Get-SessionId
    $exists = Test-RedisSession $sid
    $mark   = if ($sid -ne $prevSid) { '  <- 세션ID 회전' } else { '' }
    $prevSid = $sid

    "{0,2}  {1}   {2}      {3}{4}" -f $i, $code, $exists, $sid, $mark | Write-Host
    Start-Sleep -Seconds 1
}
