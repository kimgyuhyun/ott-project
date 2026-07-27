# 두 인스턴스가 서로 다른 세션을 죽이려 드는지 확인 (핑퐁)
#
# 앞 실험에서 인스턴스를 나눠 만든 세션 A, B 는 서로 죽지 않았다.
# 하지만 동시 세션 제어는 "가장 오래 안 쓴 세션"을 죽이므로,
# 인스턴스1 은 B 를, 인스턴스2 는 A 를 죽이려 들 수 있다.
# 실사용으로 치면 폰과 PC 로 동시 로그인한 뒤 번갈아 쓰는 상황이다.
#
# 실행: powershell -ExecutionPolicy Bypass -File .\loadtest\diag-concurrent4.ps1

$ErrorActionPreference = 'Stop'

$I1        = 'http://127.0.0.1:8090'
$I2        = 'http://127.0.0.1:8093'
$Origin    = 'https://laputa.kozow.com'
$Email     = 'loadtest0031@loadtest.local'
$Password  = $(if ($env:LT_PASSWORD) { $env:LT_PASSWORD } else { throw 'LT_PASSWORD 환경변수가 필요합니다. seed-users.sql 에 심은 비밀번호를 넣으세요.' })
$EpisodeId = 520

$work = Join-Path $env:TEMP 'ott-conc4-diag'
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
    # 결과를 한 글자로 요약: O=정상, X=401, E=세션만료안내(200 인데 실패)
    $verdict = if ($code -eq '401') { 'X(401)' }
               elseif ($body -match 'expired') { 'E(200인데만료)' }
               else { 'O(정상)' }
    return @{ Cookie = $new; Verdict = $verdict }
}

Write-Host "세션 A 를 인스턴스1 에서, 세션 B 를 인스턴스2 에서 만든다 (같은 계정)"
$ca = LoginTo $I1; $r = Hit $I1 $ca; $ca = $r.Cookie; Write-Host "  A 준비: $($r.Verdict)"
$cb = LoginTo $I2; $r = Hit $I2 $cb; $cb = $r.Cookie; Write-Host "  B 준비: $($r.Verdict)"

Write-Host ""
Write-Host "이제 A 는 자기가 만들어진 인스턴스1, B 는 인스턴스2 로 번갈아 요청한다"
Write-Host "(실사용: PC 와 폰이 각각 다른 인스턴스에 붙어서 번갈아 쓰는 상황)"
Write-Host ""
Write-Host " #   A(인스턴스1)      B(인스턴스2)"
Write-Host "--- ---------------  ---------------"
for ($i = 1; $i -le 6; $i++) {
    $ra = Hit $I1 $ca; if ($ra.Verdict -eq 'O(정상)') { $ca = $ra.Cookie }
    $rb = Hit $I2 $cb; if ($rb.Verdict -eq 'O(정상)') { $cb = $rb.Cookie }
    "{0,2}   {1,-15}  {2,-15}" -f $i, $ra.Verdict, $rb.Verdict | Write-Host
    Start-Sleep -Milliseconds 500
}
