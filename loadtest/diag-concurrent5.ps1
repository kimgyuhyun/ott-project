# 교차 요청 시 "누가" 죽는지 확인
#
# A 는 인스턴스1, B 는 인스턴스2 에서 만든 뒤,
# A 로 인스턴스2 를 때린다(교차). 앞 실험에서 A 는 200 이었다.
# 그렇다면 인스턴스2 는 아무도 안 죽인 것인가, 아니면 B 를 죽인 것인가?
#
# 실행: powershell -ExecutionPolicy Bypass -File .\loadtest\diag-concurrent5.ps1

$ErrorActionPreference = 'Stop'

$I1        = 'http://127.0.0.1:8090'
$I2        = 'http://127.0.0.1:8093'
$Origin    = 'https://laputa.kozow.com'
$Email     = 'loadtest0041@loadtest.local'
$Password  = $(if ($env:LT_PASSWORD) { $env:LT_PASSWORD } else { throw 'LT_PASSWORD 환경변수가 필요합니다. seed-users.sql 에 심은 비밀번호를 넣으세요.' })
$EpisodeId = 520

$work = Join-Path $env:TEMP 'ott-conc5-diag'
New-Item -ItemType Directory -Force $work | Out-Null
$progBody = Join-Path $work 'prog.json'
$respBody = Join-Path $work 'resp.txt'
Set-Content -Path $progBody -Value '{"positionSec":10,"durationSec":1440}' -Encoding ascii -NoNewline

function LoginTo($target) {
    $hdr = Join-Path $work 'lh.txt'
    $lb  = Join-Path $work 'login.json'
    Set-Content -Path $lb -Value ('{"email":"' + $Email + '","password":"' + $Password + '"}') -Encoding ascii -NoNewline
    $code = & curl.exe -s -o NUL -D "$hdr" -w '%{http_code}' `
        -H "Origin: $Origin" -H 'Content-Type: application/json' `
        -X POST --data-binary "@$lb" "$target/api/auth/login"
    if ($code -ne '200') { throw "로그인 실패 HTTP $code" }
    return (Select-String -Path $hdr -Pattern 'Set-Cookie:\s*JSESSIONID=([^;]+)' | Select-Object -First 1).Matches[0].Groups[1].Value
}
function Hit($target, $cookie) {
    $hdr = Join-Path $work 'h.txt'
    $code = & curl.exe -s -o "$respBody" -D "$hdr" -w '%{http_code}' `
        -H "Origin: $Origin" -H 'Content-Type: application/json' -H "Cookie: JSESSIONID=$cookie" `
        -X POST --data-binary "@$progBody" "$target/api/episodes/$EpisodeId/progress"
    $new = $cookie
    $sc = Select-String -Path $hdr -Pattern 'Set-Cookie:\s*JSESSIONID=([^;]+)' | Select-Object -First 1
    if ($sc) { $new = $sc.Matches[0].Groups[1].Value }
    $body = ''
    if (Test-Path $respBody) { $body = (Get-Content $respBody -Raw); if ($null -eq $body) { $body = '' } }
    $body = $body.Trim()
    $verdict = if ($code -eq '401') { 'X 죽음(401)' }
               elseif ($body -match 'expired') { 'X 죽음(200인데 만료안내)' }
               else { 'O 정상' }
    return @{ Cookie = $new; Verdict = $verdict }
}

$ca = LoginTo $I1; $r = Hit $I1 $ca; $ca = $r.Cookie
Write-Host "A 를 인스턴스1 에 등록: $($r.Verdict)"
$cb = LoginTo $I2; $r = Hit $I2 $cb; $cb = $r.Cookie
Write-Host "B 를 인스턴스2 에 등록: $($r.Verdict)"

Write-Host ""
Write-Host "지금 상태 - 인스턴스1 명부: [A]   인스턴스2 명부: [B]"
Write-Host ""
Write-Host "교차 발생: A 로 인스턴스2 를 때린다"
$r = Hit $I2 $ca; if ($r.Verdict -eq 'O 정상') { $ca = $r.Cookie }
Write-Host "  A 의 결과 -> $($r.Verdict)"

Write-Host ""
Write-Host "그럼 B 는 무사한가? B 로 인스턴스2 를 때려본다"
$r = Hit $I2 $cb
Write-Host "  B 의 결과 -> $($r.Verdict)"

Write-Host ""
Write-Host "B 로 인스턴스1 도 때려본다 (인스턴스1 명부에는 B 가 없었음)"
$r = Hit $I1 $cb
Write-Host "  B 의 결과 -> $($r.Verdict)"
