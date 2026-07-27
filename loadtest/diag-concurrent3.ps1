# 동시 세션 제한을 인스턴스 직접 호출로 확정한다 (nginx 라운드로빈 제거)
#
# ott-app   -> 127.0.0.1:8090
# ott-app-2 -> 127.0.0.1:8093
#
# 시나리오 1: 세션 A 와 B 를 같은 인스턴스에서 만든다   -> 그 인스턴스가 둘 다 알고 있으므로 A 가 죽어야 함
# 시나리오 2: A 는 1번, B 는 2번 인스턴스에서 만든다     -> 서로 모르므로 A 가 살아야 함
#             그 뒤 A 로 2번 인스턴스를 때리면 그때 죽어야 함
#
# 실행: powershell -ExecutionPolicy Bypass -File .\loadtest\diag-concurrent3.ps1

$ErrorActionPreference = 'Stop'

$I1        = 'http://127.0.0.1:8090'
$I2        = 'http://127.0.0.1:8093'
$Origin    = 'https://laputa.kozow.com'
$Password  = $(if ($env:LT_PASSWORD) { $env:LT_PASSWORD } else { throw 'LT_PASSWORD 환경변수가 필요합니다. seed-users.sql 에 심은 비밀번호를 넣으세요.' })
$EpisodeId = 520

$work = Join-Path $env:TEMP 'ott-conc3-diag'
New-Item -ItemType Directory -Force $work | Out-Null
$progBody = Join-Path $work 'prog.json'
$respBody = Join-Path $work 'resp.txt'
Set-Content -Path $progBody -Value '{"positionSec":10,"durationSec":1440}' -Encoding ascii -NoNewline

$redisPw = $null
$envFile = Join-Path $PSScriptRoot '..\.env'
if (Test-Path $envFile) {
    $line = Select-String -Path $envFile -Pattern '^\s*REDIS_PASSWORD\s*=' | Select-Object -First 1
    if ($line) { $redisPw = ($line.Line -split '=', 2)[1].Trim().Trim('"').Trim("'") }
}
function RedisHas($sid) {
    if (-not $redisPw -or -not $sid) { return '?' }
    (docker exec ott-redis redis-cli -a $redisPw --no-auth-warning exists "ott:session:sessions:$sid").Trim()
}
function Decode($raw) {
    try { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($raw)) } catch { return $raw }
}

# 쿠키를 파일이 아니라 변수로 직접 다룬다 (인스턴스를 바꿔가며 같은 쿠키를 써야 하므로)
function LoginTo($target, $email) {
    # 쿠키 도메인이 laputa.kozow.com 이라 127.0.0.1 로 직접 부르면 curl 쿠키 자가 저장을 거부한다.
    # 그래서 응답 헤더에서 Set-Cookie 를 직접 뽑는다.
    $hdr = Join-Path $work 'lh.txt'
    $lb  = Join-Path $work 'login.json'
    Set-Content -Path $lb -Value ('{"email":"' + $email + '","password":"' + $Password + '"}') -Encoding ascii -NoNewline
    $code = & curl.exe -s -o NUL -D "$hdr" -w '%{http_code}' `
        -H "Origin: $Origin" -H 'Content-Type: application/json' `
        -X POST --data-binary "@$lb" "$target/api/auth/login"
    if ($code -ne '200') { throw "로그인 실패 HTTP $code" }
    $sc = Select-String -Path $hdr -Pattern 'Set-Cookie:\s*JSESSIONID=([^;]+)' | Select-Object -First 1
    if (-not $sc) { throw "로그인 응답에 Set-Cookie 없음" }
    return $sc.Matches[0].Groups[1].Value
}

# 쿠키를 주고 요청. 응답에 새 쿠키가 오면 그걸 돌려준다(회전 추적).
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
    if ($body.Length -gt 80) { $body = $body.Substring(0, 80) + '...' }
    return @{ Code = $code; Cookie = $new; Body = $body }
}

function Scenario($name, $emailIdx, $targetA, $targetB, $probeTarget) {
    $email = "loadtest{0:D4}@loadtest.local" -f $emailIdx
    Write-Host ""
    Write-Host "=== $name ($email) ==="

    $ca = LoginTo $targetA $email
    $r  = Hit $targetA $ca ; $ca = $r.Cookie
    $sidA = Decode $ca
    Write-Host ("  세션 A 생성 ({0})  요청 HTTP {1}  redis={2}" -f $targetA, $r.Code, (RedisHas $sidA))

    $cb = LoginTo $targetB $email
    $r  = Hit $targetB $cb ; $cb = $r.Cookie
    Write-Host ("  세션 B 생성 ({0})  요청 HTTP {1}" -f $targetB, $r.Code)
    Write-Host ("  -> 세션 A 아직 살아있나? redis={0}" -f (RedisHas $sidA))

    Write-Host ("  이제 A 로 {0} 를 3회 때린다" -f $probeTarget)
    for ($i = 1; $i -le 3; $i++) {
        $r = Hit $probeTarget $ca
        "     {0}회차 HTTP {1}  redis={2}  본문='{3}'" -f $i, $r.Code, (RedisHas $sidA), $r.Body | Write-Host
    }
}

Scenario "시나리오 1: A 와 B 를 같은 인스턴스(1번)에서 생성" 21 $I1 $I1 $I1
Scenario "시나리오 2: A 는 1번, B 는 2번에서 생성 -> A 로 1번을 때림" 22 $I1 $I2 $I1
Scenario "시나리오 3: A 는 1번, B 는 2번에서 생성 -> A 로 2번을 때림" 23 $I1 $I2 $I2
