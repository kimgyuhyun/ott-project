param([switch]$Test)
# ============================================================================
# ott-uptime.ps1 — 사이트 다운/복구 감지 → Discord 알림 (1분 주기 로컬 모니터)
# ----------------------------------------------------------------------------
# 점검 대상: https://localhost/ (nginx→frontend→backend, 자체 인증서 무시)
#   * 공개 URL은 공유기 헤어핀 때문에 호스트에서 못 닿아 오탐 → 로컬로 점검
# 알림: 상태가 바뀔 때만(UP→DOWN, DOWN→UP) 1회씩 발송 → 스팸 없음
# 한계: 서버/인터넷이 통째로 다운되면 이 스크립트도 못 돌아 알림 불가
#       → 그 경우 대비 외부 모니터(UptimeRobot/healthchecks.io)는 백스톱으로 유지
# ============================================================================
$ErrorActionPreference = 'Continue'
$base       = 'C:\solo-project\ott-project\security'
$log        = Join-Path $base 'uptime.log'
$state      = Join-Path $base 'uptime.state'
$target     = 'https://localhost/'
$hostHeader = 'laputa.kozow.com'
$timeout    = 10
$failThreshold = 2     # 연속 N회 실패해야 DOWN 처리(일시적 흔들림 무시)
if (-not (Test-Path $base)) { New-Item -ItemType Directory -Path $base -Force | Out-Null }
function Log($m){ Add-Content -Path $log -Value ("$(Get-Date -Format o)  $m") -Encoding utf8; Write-Output $m }

# 디스코드 웹훅(워치독과 동일 파일 사용). 한글 깨짐 방지 위해 UTF-8 바이트로 전송.
function Send-DiscordAlert($content){
  try {
    $whFile = Join-Path $base 'discord-webhook.txt'
    if (-not (Test-Path $whFile)) { return }
    $url = ([regex]::Match((Get-Content $whFile -Raw), 'https://\S+')).Value
    if (-not $url) { return }
    if ($content.Length -gt 1800) { $content = $content.Substring(0,1800) }
    $json  = @{ content = $content } | ConvertTo-Json
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    Invoke-RestMethod -Uri $url -Method Post -ContentType 'application/json; charset=utf-8' -Body $bytes -TimeoutSec 10 | Out-Null
  } catch { Log "discord send failed: $($_.Exception.Message)" }
}

if ($Test) { Send-DiscordAlert ':satellite_orbital: 업타임 모니터 테스트 — 정상 작동 중입니다.'; Log 'test sent'; exit 0 }

# --- 현재 상태 점검 (curl.exe: 자체서명/Host 헤더 처리 용이) ---
$code = 0
try {
  $out  = & curl.exe -s -o NUL -w '%{http_code}' -k --max-time $timeout -H "Host: $hostHeader" $target 2>$null
  $code = [int]$out
} catch { $code = 0 }
$ok = ($code -ge 200 -and $code -lt 400)

# --- 이전 상태 로드: "STATUS FAILCOUNT" ---
$prevStatus = 'UP'; $fail = 0
if (Test-Path $state) {
  $parts = (Get-Content $state -Raw).Trim() -split '\s+'
  if ($parts.Count -ge 1 -and $parts[0]) { $prevStatus = $parts[0] }
  if ($parts.Count -ge 2) { $fail = [int]$parts[1] }
}

$now = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
if ($ok) {
  if ($prevStatus -eq 'DOWN') {
    Send-DiscordAlert (":green_circle: **사이트 복구됨** — 정상 응답(HTTP $code)`n시각: $now")
    Log "RECOVERED (code=$code)"
  } else { Log "OK (code=$code)" }
  Set-Content -Path $state -Value 'UP 0' -Encoding ascii
} else {
  $fail = $fail + 1
  if ($prevStatus -ne 'DOWN' -and $fail -ge $failThreshold) {
    Send-DiscordAlert (":red_circle: **사이트 다운 감지** — 응답 실패(HTTP $code, 연속 ${fail}회)`n시각: $now`n컨테이너/워치독 상태 확인 필요.")
    Log "DOWN alerted (code=$code, fail=$fail)"
    Set-Content -Path $state -Value "DOWN $fail" -Encoding ascii
  } elseif ($prevStatus -eq 'DOWN') {
    Log "still DOWN (code=$code, fail=$fail)"
    Set-Content -Path $state -Value "DOWN $fail" -Encoding ascii
  } else {
    Log "fail $fail/$failThreshold (code=$code) — 아직 알림 보류"
    Set-Content -Path $state -Value "UP $fail" -Encoding ascii
  }
}
