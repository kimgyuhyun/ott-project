# ============================================================================
# ott-alert-relay.ps1  —  Prometheus 경보를 디스코드로 중계
# ----------------------------------------------------------------------------
# 5분마다 스케줄 실행. Prometheus 가 firing 으로 올린 경보를 사람에게 전달한다.
#
# 왜 이게 필요한가:
#   이 스택에는 Alertmanager 가 없다. 그래서 alerts.yml 의 규칙이 firing 되어도
#   Prometheus /alerts 화면과 Grafana 목록에만 뜨고 아무도 부르지 않았다.
#   즉 DLQ 유입·디스크 임계·정기결제 배치 정체를 "누가 화면을 열어봐야" 알았다.
#   반면 호스트의 ott-watchdog.ps1 은 이미 디스코드로 잘 쏘고 있었다.
#   컨테이너를 하나 더 띄우는 대신 이미 동작하는 발송 경로를 재사용한다.
#
# Alertmanager 를 안 쓴 대가(알고 쓰는 것):
#   - 그룹핑·억제(inhibition)·무음(silence)이 없다. 지금 규칙이 4개라 필요 없다.
#   - 재알림(re-notify)이 없다. 한 번 알리고 해소될 때까지 조용하다. 아래 상태 파일 참고.
#
# 상태 파일(alert-relay.state):
#   이미 알린 경보를 기억한다. 없으면 5분마다 같은 내용이 계속 날아와서
#   결국 알림을 무시하게 된다 — 경보를 다는 목적과 정반대가 된다.
#   해소되면 복구 메시지를 한 번 보내고 목록에서 지운다.
# ============================================================================
param([switch]$Test)   # -Test: 파싱·발송 경로만 확인(실제 firing 이 없어도 샘플 1건을 보낸다)
$ErrorActionPreference = 'Continue'
$base  = 'C:\solo-project\ott-project\security'
$log   = Join-Path $base 'alert-relay.log'
$state = Join-Path $base 'alert-relay.state'
$promUrl = 'http://127.0.0.1:9090'

function Log($m){ $line = "$(Get-Date -Format o)  $m"; Add-Content -Path $log -Value $line -Encoding utf8; Write-Host $line }

# $base 와 Log 가 정의된 뒤에 읽어야 한다(공용 함수가 둘 다 쓴다).
. (Join-Path $base 'alert-common.ps1')

if ($Test) {
  Send-DiscordAlert ':white_check_mark: OTT 경보 중계 테스트 — 이 메시지가 보이면 Prometheus 경보도 이 경로로 옵니다.'
  Log 'test alert sent'; exit 0
}

# 경보 한 건의 신원. 같은 규칙이라도 라벨이 다르면(예: 디스크 path, DLQ topic) 다른 경보다.
# 라벨을 정렬해 붙이는 이유는 순서가 바뀌어도 같은 경보로 보기 위해서다.
function Get-AlertKey($a) {
  $labels = ($a.labels.PSObject.Properties | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ','
  return $labels
}

# --- Prometheus 조회 ---------------------------------------------------------
# 실패해도 조용히 끝낸다. Prometheus 자체가 죽은 경우는 ott-watchdog.ps1 의
# 컨테이너 상태 점검(expected 목록에 ott-prometheus 포함)이 이미 알린다.
try {
  $resp = Invoke-RestMethod -Uri "$promUrl/api/v1/alerts" -TimeoutSec 10
} catch {
  Log "Prometheus 조회 실패: $($_.Exception.Message) — 이번 주기 skip"
  exit 0
}
if ($resp.status -ne 'success') { Log "Prometheus 응답 이상: status=$($resp.status)"; exit 0 }

# pending 은 알리지 않는다. for: 구간을 아직 채우는 중이고, 여기서 알리면
# 규칙에 for 를 건 의미가 사라진다(잠깐 튄 값에 알림이 나간다).
$firing = @($resp.data.alerts | Where-Object { $_.state -eq 'firing' })
$curKeys = @($firing | ForEach-Object { Get-AlertKey $_ })

$notified = @()
if (Test-Path $state) {
  try { $notified = @((Get-Content $state -Raw | ConvertFrom-Json).notified) } catch { }
}
$notified = @($notified | Where-Object { $_ })   # null/빈 값 제거(파일이 비었던 경우)

$new       = @($firing | Where-Object { $notified -notcontains (Get-AlertKey $_) })
$resolved  = @($notified | Where-Object { $curKeys -notcontains $_ })

foreach ($a in $new) {
  $name = $a.labels.alertname
  $sev  = if ($a.labels.severity) { $a.labels.severity } else { 'unknown' }
  $sum  = if ($a.annotations.summary) { $a.annotations.summary } else { $name }
  $desc = $a.annotations.description
  $icon = if ($sev -eq 'critical') { ':rotating_light:' } else { ':warning:' }

  Log "FIRING [$name] $sum"
  Send-DiscordAlert ("$icon **OTT 경보 — $name** ($sev)`n$sum`n`n$desc`n" +
    "firing 시작: $($a.activeAt)`n화면: http://127.0.0.1:9090/alerts")
}

foreach ($k in $resolved) {
  $name = ([regex]::Match($k, 'alertname=([^,]+)')).Groups[1].Value
  Log "RESOLVED [$name] $k"
  Send-DiscordAlert ":white_check_mark: **OTT 경보 해소 — $name**`n$k"
}

if (-not $new -and -not $resolved) {
  Log "OK  firing=$($firing.Count) (신규/해소 없음)"
}

@{ notified = $curKeys } | ConvertTo-Json -Compress | Set-Content -Path $state -Encoding utf8
exit 0
