# 세션 ID 회전 시점 확인
#
# 가설: 로그인 응답의 JSESSIONID 는 "최종" 세션이 아니다.
#       로그인 후 첫 인증 요청에서 서버가 세션 ID 를 한 번 더 회전시키고,
#       그 순간 로그인 때 받은 쿠키는 죽는다.
#
# 검증: 로그인 쿠키 C0 로 요청 -> 200 + 새 쿠키 C1 수신
#       다시 C0 로 요청 -> 401 이면 가설 확정
#       C1 로 요청      -> 200 이면 가설 확정
#
# 실행: powershell -ExecutionPolicy Bypass -File .\loadtest\diag-rotation.ps1

$ErrorActionPreference = 'Stop'

$Base      = 'https://laputa.kozow.com'
$Email     = 'loadtest0002@loadtest.local'
$Password  = $(if ($env:LT_PASSWORD) { $env:LT_PASSWORD } else { throw 'LT_PASSWORD 환경변수가 필요합니다. seed-users.sql 에 심은 비밀번호를 넣으세요.' })
$EpisodeId = 520

$work = Join-Path $env:TEMP 'ott-rot-diag'
New-Item -ItemType Directory -Force $work | Out-Null
$jar       = Join-Path $work 'cookies.txt'
$loginBody = Join-Path $work 'login.json'
$progBody  = Join-Path $work 'prog.json'
if (Test-Path $jar) { Remove-Item $jar }

Set-Content -Path $loginBody -Value ('{"email":"' + $Email + '","password":"' + $Password + '"}') -Encoding ascii -NoNewline
Set-Content -Path $progBody  -Value '{"positionSec":10,"durationSec":1440}' -Encoding ascii -NoNewline

function Get-RawCookie {
    $l = Get-Content $jar | Where-Object { $_ -match 'JSESSIONID' } | Select-Object -Last 1
    if (-not $l) { return $null }
    return ($l -split "`t")[-1]
}
function Short($raw) {
    if (-not $raw) { return '(none)' }
    try { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($raw)).Substring(0, 8) } catch { return $raw.Substring(0, 8) }
}

# 1) 로그인
$code = & curl.exe -s -o NUL -w '%{http_code}' -c "$jar" `
    -H "Origin: $Base" -H 'Content-Type: application/json' `
    -X POST --data-binary "@$loginBody" "$Base/api/auth/login"
$C0 = Get-RawCookie
Write-Host "1) 로그인            HTTP $code  쿠키 C0 = $(Short $C0)"
if ($code -ne '200') { exit 1 }

# 2) C0 로 첫 인증 요청 (쿠키 자를 계속 갱신해서 새 쿠키를 받아본다)
$code = & curl.exe -s -o NUL -w '%{http_code}' -b "$jar" -c "$jar" `
    -H "Origin: $Base" -H 'Content-Type: application/json' `
    -X POST --data-binary "@$progBody" "$Base/api/episodes/$EpisodeId/progress"
$C1 = Get-RawCookie
Write-Host "2) C0 로 진행률 저장 HTTP $code  응답 후 쿠키 = $(Short $C1)$(if ($C1 -ne $C0) { '   <- 회전됨!' })"

# 3) 이제 C0 를 다시 써본다 (k6 스크립트가 하던 짓)
$code = & curl.exe -s -o NUL -w '%{http_code}' `
    -H "Origin: $Base" -H 'Content-Type: application/json' -H "Cookie: JSESSIONID=$C0" `
    -X POST --data-binary "@$progBody" "$Base/api/episodes/$EpisodeId/progress"
Write-Host "3) C0 재사용         HTTP $code   <- 401 이면 로그인 쿠키가 죽은 것"

# 4) C1 은 살아있는지
$code = & curl.exe -s -o NUL -w '%{http_code}' `
    -H "Origin: $Base" -H 'Content-Type: application/json' -H "Cookie: JSESSIONID=$C1" `
    -X POST --data-binary "@$progBody" "$Base/api/episodes/$EpisodeId/progress"
Write-Host "4) C1 사용           HTTP $code   <- 200 이면 회전 후 세션이 정상"

# 5) C1 로 한 번 더 (2회차 이후로는 회전이 멈추는지)
$code = & curl.exe -s -o NUL -w '%{http_code}' `
    -H "Origin: $Base" -H 'Content-Type: application/json' -H "Cookie: JSESSIONID=$C1" `
    -X POST --data-binary "@$progBody" "$Base/api/episodes/$EpisodeId/progress"
Write-Host "5) C1 재사용         HTTP $code   <- 200 이면 회전은 1회성"
