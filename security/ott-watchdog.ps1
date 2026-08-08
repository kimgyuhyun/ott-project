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
    $json  = @{ content = $content } | ConvertTo-Json
    # 한글 깨짐 방지: 본문을 UTF-8 바이트로 직접 전송(PS 5.1 기본 인코딩 우회)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    Invoke-RestMethod -Uri $url -Method Post -ContentType 'application/json; charset=utf-8' -Body $bytes -TimeoutSec 10 | Out-Null
    Log 'Discord alert sent'
  } catch { Log "Discord alert failed: $($_.Exception.Message)" }
}

if ($Test) { Send-DiscordAlert ':white_check_mark: OTT 워치독 테스트 알림 — 이 메시지가 보이면 알림 설정 완료!'; Log 'test alert sent'; exit 0 }

# docker CLI 는 "OCI runtime exec failed: ..." 를 stderr 가 아니라 stdout 으로 낸다. 그래서
# exec 자체가 실패하면 그 에러 문구가 find 결과인 척 담겨 멀쩡한 컨테이너를 침해로 오탐한다.
# (2026-08-08: 호스트 부팅 직후 아직 기동 중인 ott-app 에 exec → broken pipe → 자동 격리 →
#  프록시망에서 끊겨 nginx 가 upstream 을 못 찾고 크래시 루프 → 서비스 전체 다운)
# 종료 코드로 실패를 걸러내고, find 결과는 절대경로로 시작하는 줄만 인정한다.
function Invoke-ContainerFind($c, $cmd) {
  $out = & docker exec $c sh -c $cmd 2>$null
  if ($LASTEXITCODE -ne 0) { Log "EXEC FAILED [$c] rc=$($LASTEXITCODE) $($out -join ' ')"; return @() }
  @($out | Where-Object { $_ -match '^/' })
}

$targets    = @('ott-frontend','ott-app')
$iocRegex   = 'xmrig|javae|minerd|cpuminer|kdevtmpfsi|kinsing|supportxmr|xRaPNJ'
$detections = @()

# === (0) 컨테이너 상태 점검 =================================================
# PLATFORM 9절 [절대] "컨테이너 상태와 침해 지표를 주기적으로 점검하는 워치독을 둔다".
# 2026-08-08: ott-smtp-proxy 가 설정 파일 줄바꿈(CRLF) 때문에 크래시 루프에 빠져
# SMTP 아웃바운드가 통째로 멈췄는데, 다음 배포의 검증 단계가 잡을 때까지 아무도 몰랐다.
# 이 스크립트는 침해만 보고 "떠 있는지"는 안 보고 있었다 — 아래 IOC 검사는 running 이
# 아닌 컨테이너를 그냥 건너뛰므로(continue), 죽은 컨테이너는 오히려 더 조용했다.
#
# 프로메테우스로 하지 않는 이유: 컨테이너 상태를 지표로 만들려면 docker.sock 을 컨테이너에
# 넣어야 하는데(cAdvisor 계열) 그건 docker-compose.monitoring.yml 에서 의도적으로 거절한
# 결정이다. 게다가 이 스택엔 Alertmanager 가 없어 규칙이 firing 돼도 발송 경로가 없다.
# 호스트에서 docker 를 직접 보고 디스코드로 쏘는 이 경로가 유일하게 사람에게 닿는다.
$expected = @(
  'ott-nginx','ott-frontend','ott-app','ott-app-2',        # 웹 계층
  'ott-postgres','ott-redis','ott-kafka','ott-rabbitmq',   # 데이터 계층
  'ott-egress-proxy','ott-smtp-proxy',                     # 아웃바운드 프록시
  'ott-prometheus','ott-grafana','ott-loki','ott-alloy'    # 관측
)
# 단일 인스턴스로 롤백했다면(docker rm -f ott-app-2) 이 목록에서 ott-app-2 를 뺀다.
# pgadmin/certbot 은 opt-in 유틸리티라 상시 대상이 아니다.
$cstate = Join-Path $base 'containers.state'

$down = @(foreach ($c in $expected) {
  $st = (& docker inspect -f '{{.State.Status}}' $c 2>$null)
  if (-not $st) { "$c=absent" } elseif ($st -ne 'running') { "$c=$st" }
})
$downNames = @($down | ForEach-Object { ($_ -split '=')[0] })

# prev = 직전 점검의 다운 목록, alerted = 이미 알린 목록(복구될 때까지 재발송 안 함)
$prev = @(); $alerted = @()
if (Test-Path $cstate) {
  try { $s = Get-Content $cstate -Raw | ConvertFrom-Json; $prev = @($s.prev); $alerted = @($s.alerted) } catch { }
}

# 2회 연속 다운일 때만 알린다. 무중단 배포는 컨테이너를 하나씩 지웠다 다시 만들고
# 백엔드는 헬시까지 수십 초가 걸리므로, 한 번의 점검만 보고 알리면 배포마다 오탐이 난다
# (ott-uptime.ps1 의 failThreshold=2 와 같은 취지). 대신 탐지가 한 주기 늦는다.
$newDown   = @($downNames | Where-Object { $prev -contains $_ -and $alerted -notcontains $_ })
$recovered = @($alerted   | Where-Object { $downNames -notcontains $_ })

if ($newDown) {
  $detail = (($down | Where-Object { $newDown -contains ($_ -split '=')[0] }) -join ', ')
  Log "CONTAINER DOWN $detail"
  Send-DiscordAlert (":warning: **OTT 컨테이너 다운** — $detail`n2회 연속 점검에서 running 이 아니다. 배포 중이었다면 곧 복구 알림이 온다.")
}
if ($recovered) {
  Log "CONTAINER RECOVERED $($recovered -join ', ')"
  Send-DiscordAlert (":white_check_mark: **OTT 컨테이너 복구** — $($recovered -join ', ')")
}
# 다음 점검을 위해 저장: prev 는 이번 다운 목록, alerted 는 아직 안 돌아온 것만 남긴다.
$alerted = @(@($alerted | Where-Object { $downNames -contains $_ }) + $newDown | Select-Object -Unique)
@{ prev = $downNames; alerted = $alerted } | ConvertTo-Json -Compress | Set-Content -Path $cstate -Encoding utf8
if (-not $down) { Log "OK  all $($expected.Count) containers running" }

foreach ($c in $targets) {
  $running = (& docker inspect -f '{{.State.Running}}' $c 2>$null)
  if ($running -ne 'true') { continue }

  # (1) 쓰기 경로의 대용량 파일 또는 마이너 파일명 (정상 tmp 파일은 작음)
  # 2026-08-08: ott-app 이미지(Amazon Linux 2023 minimal)엔 find 가 없어서 아래 두 검사가
  # 줄곧 조용히 0건이었다 — find 를 못 찾은 에러까지 sh 안의 2>/dev/null 이 삼켰고, head 를
  # 거치며 종료 코드도 0 이 됐다. "이상 없음"과 "검사 못 함"을 구분해서 남긴다.
  $hasFind = & docker exec $c sh -c 'command -v find >/dev/null 2>&1 && echo yes' 2>$null
  if ($hasFind -ne 'yes') {
    Log "SCAN DEGRADED [$c] find 없음 — 파일 기반 검사(1)(3) 건너뜀. IOC 프로세스 검사만 유효"
    $dropped = @()
  } else {
    $dropped = Invoke-ContainerFind $c 'find /tmp /dev/shm /var/tmp -type f \( -size +1024k -o -name "javae" -o -iname "xmrig*" -o -iname "*miner*" -o -iname "cpuminer*" \) 2>/dev/null | head -50'
  }
  if ($dropped) { $detections += [PSCustomObject]@{ c=$c; kind='DROPPED_BINARY'; detail=($dropped -join '; ') } }

  # (2) 호스트측 프로세스 목록에서 IOC (in-container ps 불필요 → distroless도 커버)
  #   주의: CMD 전체(인자 포함)가 아니라 "실행 파일 basename"에만 매칭한다.
  #   그렇지 않으면 find/grep 등 보안 스캔 명령이 인자로 키워드(xmrig 등)를 갖는 순간
  #   자기 자신을 마이너로 오탐한다(2026-06-26 오탐 사례). 실제 마이너는 실행 파일명이
  #   xmrig/javae/… 이므로 basename 매칭으로 충분히 잡힌다.
  $procLines = (& docker top $c 2>$null) -split "`n" | Select-Object -Skip 1   # 헤더 제외
  $bad = foreach ($line in $procLines) {
    if (-not $line.Trim()) { continue }
    # docker top 컬럼: UID PID PPID C STIME TTY TIME CMD...  → CMD는 8번째 필드부터
    $fields = $line -split '\s+' | Where-Object { $_ }
    if ($fields.Count -lt 8) { continue }
    $exe = $fields[7]                          # 실행 파일(경로 포함 가능)
    $exeName = ($exe -split '[\\/]')[-1]       # basename
    if ($exeName -match $iocRegex) { $line.Trim() }
  }
  if ($bad) {
    $detections += [PSCustomObject]@{ c=$c; kind='IOC_PROCESS'; detail=($bad -join ' | ') }
  }

  # (3) /tmp 의 위장 점(.)폴더 안의 파일 (정상적으로는 비어있거나 소켓뿐)
  $hidden = if ($hasFind -eq 'yes') { Invoke-ContainerFind $c 'find /tmp/.[A-Za-z]*/ -type f 2>/dev/null | head -30' } else { @() }
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
