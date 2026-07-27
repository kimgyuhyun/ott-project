# 동시 세션 만료 시점의 "200" 이 진짜 성공인지 확인
#
# 앞선 재현에서 세션 A 가 죽는 순간의 요청이 HTTP 200 을 받았다.
# Spring Security 의 기본 세션 만료 전략은 안내 문구를 본문에 쓰고 200 으로 끝내기 때문에,
# 클라이언트는 성공으로 오해하지만 실제로는 진행률이 저장되지 않았을 수 있다.
# 그래서 응답 본문을 그대로 찍어본다.
#
# 실행: powershell -ExecutionPolicy Bypass -File .\loadtest\diag-concurrent2.ps1

$ErrorActionPreference = 'Stop'

$Base      = 'https://laputa.kozow.com'
$Email     = 'loadtest0004@loadtest.local'
$Password  = $(if ($env:LT_PASSWORD) { $env:LT_PASSWORD } else { throw 'LT_PASSWORD 환경변수가 필요합니다. seed-users.sql 에 심은 비밀번호를 넣으세요.' })
$EpisodeId = 520

$work = Join-Path $env:TEMP 'ott-conc2-diag'
New-Item -ItemType Directory -Force $work | Out-Null
$jarA      = Join-Path $work 'a.txt'
$jarB      = Join-Path $work 'b.txt'
$loginBody = Join-Path $work 'login.json'
$progBody  = Join-Path $work 'prog.json'
$respBody  = Join-Path $work 'resp.txt'
foreach ($f in @($jarA, $jarB)) { if (Test-Path $f) { Remove-Item $f } }

Set-Content -Path $loginBody -Value ('{"email":"' + $Email + '","password":"' + $Password + '"}') -Encoding ascii -NoNewline
Set-Content -Path $progBody  -Value '{"positionSec":10,"durationSec":1440}' -Encoding ascii -NoNewline

function DoLogin($jar) {
    & curl.exe -s -o NUL -w '%{http_code}' -c "$jar" `
        -H "Origin: $Base" -H 'Content-Type: application/json' `
        -X POST --data-binary "@$loginBody" "$Base/api/auth/login"
}
function DoProgress($jar) {
    $code = & curl.exe -s -o "$respBody" -w '%{http_code}' -b "$jar" -c "$jar" `
        -H "Origin: $Base" -H 'Content-Type: application/json' `
        -X POST --data-binary "@$progBody" "$Base/api/episodes/$EpisodeId/progress"
    $body = if (Test-Path $respBody) { (Get-Content $respBody -Raw) } else { '' }
    if ($null -eq $body) { $body = '' }
    $body = $body.Trim()
    if ($body.Length -gt 90) { $body = $body.Substring(0, 90) + '...' }
    return @{ Code = $code; Body = $body }
}

Write-Host "세션 A 준비: 로그인 $(DoLogin $jarA), 요청 $((DoProgress $jarA).Code)"
Write-Host "세션 B 준비: 로그인 $(DoLogin $jarB), 요청 $((DoProgress $jarB).Code)"
Write-Host ""
Write-Host "정상 응답 본문이 어떻게 생겼는지 기준값:"
$ref = DoProgress $jarB
Write-Host "  B: HTTP $($ref.Code)  본문= $($ref.Body)"
Write-Host ""
Write-Host "이제 세션 A 로 반복 (본문이 바뀌는 순간을 본다)"
Write-Host " #  HTTP  본문"
Write-Host "--- ----  ----"
for ($i = 1; $i -le 8; $i++) {
    $r = DoProgress $jarA
    "{0,2}  {1}   {2}" -f $i, $r.Code, $r.Body | Write-Host
    Start-Sleep -Milliseconds 700
}
