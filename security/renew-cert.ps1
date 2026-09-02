param([switch]$Test, [switch]$CheckOnly)
# ============================================================================
# renew-cert.ps1 — Let's Encrypt 갱신 + 결과 검증 + 만료 감시 (하루 2회 스케줄)
# ----------------------------------------------------------------------------
# 왜 배치에서 옮겨왔나 (2026-09-02):
#   renew-cert.cmd 는 두 줄이었다.
#       docker run ... certbot renew --quiet
#       docker compose ... exec nginx nginx -s reload
#   배치는 마지막 명령의 종료코드가 스크립트의 종료코드가 된다. 그래서 certbot 이
#   실패해도 뒤의 reload 만 성공하면 스크립트는 0 으로 끝나고, 스케줄 작업 기록에도
#   성공으로 남는다(실측 2026-09-02: LastTaskResult=0). 즉 갱신이 30일 내내 실패해도
#   아무도 모르고, 사이트가 죽어야 업타임 모니터가 잡는다 — 그건 사전 감시가 아니다
#   (PLATFORM 3절 "인증서 갱신을 자동화한 뒤 만료 감시를 둔다").
#
#   .cmd 는 스케줄 작업 3개(LetsEncryptRenew / _Evening / _OnLogon)가 가리키고 있어서
#   그대로 두고 이 파일을 호출하는 껍데기로만 남겼다. 작업 등록은 건드리지 않는다.
#
# 만료를 "디스크의 pem" 이 아니라 "nginx 가 실제로 내보내는 인증서" 에서 읽는 이유:
#   certbot 이 갱신에 성공했는데 reload 가 안 됐으면 파일은 새것, 서빙되는 것은 옛것이다.
#   파일만 보면 정상으로 보이고 브라우저는 만료된 인증서를 받는다. 그 경우를 잡으려면
#   TLS 로 붙어서 받아오는 수밖에 없다. 접속은 127.0.0.1, SNI 는 실도메인으로 보낸다
#   (ott-uptime.ps1 과 같은 이유 — 공유기 헤어핀 때문에 공개 URL 은 호스트에서 못 닿는다).
#
# 임계값 근거(PLATFORM 9절 [상황] — 기준선 없이 임의의 숫자를 박지 않는다):
#   관측이 아니라 정의에서 나온다. Let's Encrypt 인증서는 90일이고 certbot 은 남은
#   30일부터 갱신한다. 그러므로 "21일 미만" 은 하루 2회 갱신 시도가 9일(=18회) 연속
#   실패했다는 뜻이고, 트래픽 기준선 없이 비정상이라고 단정할 수 있다.
#   7일 미만은 주말을 낀 대응 시간이 남지 않는 지점이다.
#
# 알림: 같은 상태로 하루 1회까지만 보낸다. 하루 2회 도는 스크립트라 de-dup 이 없으면
#   만료 경고가 매일 두 번씩 쌓이고, 반복되는 경보는 무시하게 된다(PLATFORM 9절).
# ============================================================================
$ErrorActionPreference = 'Continue'
$base     = $PSScriptRoot                      # alert-common.ps1 이 웹훅 파일을 찾는 기준 경로
$root     = Split-Path $PSScriptRoot -Parent
$log      = Join-Path $base 'cert-renew.log'
$state    = Join-Path $base 'cert-renew.state'
$domain   = 'laputa.kozow.com'
$warnDays = 21
$critDays = 7

function Log($m) { Add-Content -Path $log -Value ("$(Get-Date -Format o)  $m") -Encoding utf8; Write-Output $m }

# 발송 함수는 alert-common.ps1 의 것을 쓴다. 그 파일 주석대로 이 함수는 이미 두 번
# 고쳐졌고(웹훅 파싱, 한글 깨짐), 사본을 만들면 다음 수정이 한쪽에만 적용된다.
# 전제: $base 와 Log 가 dot-source 보다 먼저 정의돼 있어야 한다 — 위에서 끝냈다.
. (Join-Path $PSScriptRoot 'alert-common.ps1')

if ($Test) { Send-DiscordAlert ':lock: 인증서 감시 테스트 — 정상 작동 중입니다.'; exit 0 }

# --- 서빙 중인 인증서의 남은 일수 -------------------------------------------
# 실패해도 예외를 던지지 않고 $null 을 돌려준다. 여기서 죽으면 갱신 결과까지 같이
# 묻히는데, 그게 이 파일이 고치려는 문제 그 자체다.
function Get-ServedCertDaysLeft {
  $tcp = $null; $ssl = $null
  try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    if (-not $tcp.ConnectAsync('127.0.0.1', 443).Wait(10000)) { return $null }
    # 만료된 인증서도 읽어야 하므로 검증 콜백은 무조건 통과시킨다. 여기서 보는 것은
    # 신뢰성이 아니라 NotAfter 하나다.
    $ssl = New-Object System.Net.Security.SslStream($tcp.GetStream(), $false,
             ([System.Net.Security.RemoteCertificateValidationCallback] { $true }))
    $ssl.AuthenticateAsClient($domain)
    $cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($ssl.RemoteCertificate)
    return [math]::Floor(($cert.NotAfter.ToUniversalTime() - [DateTime]::UtcNow).TotalDays)
  } catch { return $null }
  finally { if ($ssl) { $ssl.Dispose() }; if ($tcp) { $tcp.Close() } }
}

# --- 1) 갱신 ---------------------------------------------------------------
$renewOk = $true; $reloadOk = $true
if ($CheckOnly) {
  Log 'CheckOnly - certbot/reload 건너뜀'
} else {
  docker run --rm -v certbot-etc:/etc/letsencrypt -v certbot-webroot:/var/www/certbot certbot/certbot renew --quiet
  $renewOk = ($LASTEXITCODE -eq 0)
  if (-not $renewOk) { Log "certbot renew FAILED (exit $LASTEXITCODE)" }

  # 갱신 여부와 무관하게 항상 reload 한다(기존 .cmd 동작 유지). 앞 회차가 갱신에는
  # 성공하고 reload 에 실패했다면 이번 회차가 그걸 되살린다.
  # -T: 스케줄 작업에는 콘솔이 없다. TTY 를 붙이려다 실패하지 않게 명시한다.
  docker compose -f "$root\docker-compose.yml" -f "$root\docker-compose.prod.yml" exec -T nginx nginx -s reload
  $reloadOk = ($LASTEXITCODE -eq 0)
  if (-not $reloadOk) { Log "nginx reload FAILED (exit $LASTEXITCODE)" }
}

# --- 2) 실제로 서빙되는 인증서 확인 ----------------------------------------
$days = Get-ServedCertDaysLeft
$daysText = if ($null -ne $days) { "$days" + '일' } else { '확인 불가' }

# --- 3) 상태 판정 ----------------------------------------------------------
# 순서가 곧 우선순위다. 갱신이 깨졌으면 남은 일수보다 그게 먼저다.
if (-not $renewOk) {
  $token = 'RENEW_FAIL'
  $msg   = ":rotating_light: **인증서 갱신 실패** - certbot renew 가 0 이 아닌 코드로 끝났습니다. 남은 기간: $daysText"
} elseif (-not $reloadOk) {
  $token = 'RELOAD_FAIL'
  $msg   = ":rotating_light: **nginx reload 실패** - 갱신은 됐을 수 있으나 서빙되는 인증서가 바뀌지 않았습니다. 남은 기간: $daysText"
} elseif ($null -eq $days) {
  $token = 'PROBE_FAIL'
  $msg   = ':warning: **인증서 만료일 확인 불가** - 443 으로 TLS 핸드셰이크가 되지 않습니다. nginx 상태를 확인하세요.'
} elseif ($days -lt $critDays) {
  $token = 'EXPIRY_CRITICAL'
  $msg   = ":rotating_light: **인증서 만료 임박 - $daysText 남음** (기준 $critDays 일). certbot 은 남은 30일부터 갱신하므로 여기까지 왔다면 자동 갱신이 듣지 않고 있습니다. 즉시 수동 확인 필요."
} elseif ($days -lt $warnDays) {
  $token = 'EXPIRY_WARN'
  $msg   = ":warning: **인증서 만료 $daysText 전** (기준 $warnDays 일). certbot 은 30일부터 갱신을 시도하므로, 여기까지 왔다는 것은 갱신이 반복 실패했다는 뜻입니다. cert-renew.log 확인."
} else {
  $token = 'OK'
  $msg   = $null
}

$today = Get-Date -Format 'yyyy-MM-dd'
Log "$token (days=$daysText renew=$renewOk reload=$reloadOk)"

# --- 4) 알림 (같은 상태는 하루 1회) -----------------------------------------
$prevToken = 'OK'; $prevDate = ''
if (Test-Path $state) {
  $parts = (Get-Content $state -Raw).Trim() -split '\|'
  if ($parts.Count -ge 1 -and $parts[0]) { $prevToken = $parts[0] }
  if ($parts.Count -ge 2) { $prevDate = $parts[1] }
}

$now = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
if ($token -ne 'OK') {
  if ($token -ne $prevToken -or $today -ne $prevDate) {
    Send-DiscordAlert "$msg`n시각: $now"
  } else {
    Log 'alert suppressed (same state today)'
  }
} elseif ($prevToken -ne 'OK') {
  # 복구 알림. 업타임 모니터와 같은 방식 - 문제가 사라진 것도 알려야 상태를 따라갈 수 있다.
  Send-DiscordAlert ":green_circle: **인증서 정상 복구** - 남은 기간 $daysText, 갱신과 reload 모두 성공.`n시각: $now"
}
Set-Content -Path $state -Value "$token|$today" -Encoding ascii

# 스케줄 작업 기록에도 남도록 종료코드를 넘긴다. 이게 없으면 .cmd 시절과 똑같이
# "결과 0" 만 남는다.
if ($token -ne 'OK') { exit 1 }
exit 0
