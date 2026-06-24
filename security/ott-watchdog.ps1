# ============================================================================
# ott-watchdog.ps1  —  컨테이너 재감염(XMRig 등) 탐지 & 자동 격리
# ----------------------------------------------------------------------------
# 2026-06-24 재감염 대응. 5분마다 스케줄 실행(또는 수동).
# 탐지 시: 해당 컨테이너 네트워크 분리 + 정지 + ALERT 기록(증거 보존 위해 rm 안 함).
#
# 탐지 항목(오탐 최소화 — 정상 정적자산/JVM 임시파일 제외):
#   1) 쓰기 가능 경로(/tmp,/dev/shm,/var/tmp)의 "대용량(>1MB) 파일" 또는 알려진 마이너 파일명
#      → 마이너 바이너리는 수 MB. 정상 앱은 이 경로에 대용량 실행물을 두지 않음.
#      (/app/public 은 read_only라 드롭 불가 → 스캔 제외. 정적 이미지의 exec 비트 오탐 방지)
#   2) IOC 프로세스명(xmrig/javae/minerd/cpuminer/kdevtmpfsi/kinsing/xRaPNJ 등) — docker top(호스트측)
#   3) /tmp 내 위장 점(.)폴더에 들어있는 파일(.ICEi-unix/javae 패턴)
# ============================================================================
param([switch]$Test)   # -Test: 디스코드 알림만 한 번 보내고 종료(웹훅 동작 확인용)
$ErrorActionPreference = 'Continue'
$base  = 'C:\solo-project\ott-project\security'
$log   = Join-Path $base 'watchdog.log'
$alert = Join-Path $base 'ALERT.txt'
if (-not (Test-Path $base)) { New-Item -ItemType Directory -Path $base -Force | Out-Null }
function Log($m){ $line = "$(Get-Date -Format o)  $m"; Add-Content -Path $log -Value $line -Encoding utf8; Write-Output $line }

# 디스코드 웹훅 알림. URL은 security\discord-webhook.txt 한 줄에 저장(깃 제외). 없으면 조용히 skip.
function Send-DiscordAlert($content) {
  try {
    $whFile = Join-Path $base 'discord-webhook.txt'
    if (-not (Test-Path $whFile)) { return }
    # 파일 어디에 있든 https://... 토큰을 추출(앞에 다른 텍스트가 붙어 있어도 동작)
    $url = ([regex]::Match((Get-Content $whFile -Raw), 'https://\S+')).Value
    if (-not $url) { return }
    if ($content.Length -gt 1800) { $content = $content.Substring(0,1800) }
    $body = @{ content = $content } | ConvertTo-Json
    Invoke-RestMethod -Uri $url -Method Post -ContentType 'application/json; charset=utf-8' -Body $body -TimeoutSec 10 | Out-Null
    Log 'Discord alert sent'
  } catch { Log "Discord alert failed: $($_.Exception.Message)" }
}

if ($Test) { Send-DiscordAlert ':white_check_mark: OTT 워치독 테스트 알림 — 이 메시지가 보이면 알림 설정 완료!'; Log 'test alert sent'; exit 0 }

$targets    = @('ott-frontend','ott-app')
$iocRegex   = 'xmrig|javae|minerd|cpuminer|kdevtmpfsi|kinsing|supportxmr|xRaPNJ'
$detections = @()

foreach ($c in $targets) {
  $running = (& docker inspect -f '{{.State.Running}}' $c 2>$null)
  if ($running -ne 'true') { continue }

  # (1) 쓰기 경로의 대용량 파일 또는 마이너 파일명 (정상 tmp 파일은 작음)
  $dropped = & docker exec $c sh -c 'find /tmp /dev/shm /var/tmp -type f \( -size +1024k -o -name "javae" -o -iname "xmrig*" -o -iname "*miner*" -o -iname "cpuminer*" \) 2>/dev/null | head -50' 2>$null
  if ($dropped) { $detections += [PSCustomObject]@{ c=$c; kind='DROPPED_BINARY'; detail=($dropped -join '; ') } }

  # (2) 호스트측 프로세스 목록에서 IOC (in-container ps 불필요 → distroless도 커버)
  $procs = & docker top $c 2>$null | Out-String
  if ($procs -match $iocRegex) {
    $bad = ($procs -split "`n" | Where-Object { $_ -match $iocRegex }) -join ' | '
    $detections += [PSCustomObject]@{ c=$c; kind='IOC_PROCESS'; detail=$bad }
  }

  # (3) /tmp 의 위장 점(.)폴더 안의 파일 (정상적으로는 비어있거나 소켓뿐)
  $hidden = & docker exec $c sh -c 'find /tmp/.[A-Za-z]*/ -type f 2>/dev/null | head -30' 2>$null
  if ($hidden) {
    $detections += [PSCustomObject]@{ c=$c; kind='HIDDEN_DIR_FILE'; detail=($hidden -join '; ') }
  }
}

if ($detections.Count -eq 0) {
  Log "OK  no compromise indicators in $($targets -join ',')"
  exit 0
}

# === 탐지됨 → 격리 ===
$summary = "!!! COMPROMISE DETECTED $(Get-Date -Format o) !!!`n"
foreach ($d in $detections) {
  $summary += "[$($d.c)] $($d.kind): $($d.detail)`n"
  Log "DETECT [$($d.c)] $($d.kind): $($d.detail)"
}
$hitContainers = ($detections | Select-Object -ExpandProperty c -Unique)
foreach ($c in $hitContainers) {
  try {
    $nets = (& docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}' $c 2>$null) -split ' ' | Where-Object { $_ }
    foreach ($n in $nets) { & docker network disconnect -f $n $c 2>$null; Log "CONTAIN disconnected $c from $n" }
    & docker stop $c 2>$null | Out-Null
    Log "CONTAIN stopped $c (filesystem preserved for forensics)"
  } catch { Log "CONTAIN error on ${c}: $($_.Exception.Message)" }
}
Set-Content -Path $alert -Value $summary -Encoding utf8
Send-DiscordAlert (":rotating_light: **OTT 보안 경보** — 컨테이너 침해 감지 & 자동 격리됨`n`n$summary`n조치: 네트워크 분리 + 컨테이너 정지(증거 보존). 서버 점검 필요.")
Log "ALERT written to $alert"
exit 1
