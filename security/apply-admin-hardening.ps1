# ============================================================================
# apply-admin-hardening.ps1  —  반드시 "관리자 권한" PowerShell에서 실행
# ----------------------------------------------------------------------------
# 2026-06-24 재감염 대응. 관리자 권한이 필요한 호스트 하드닝을 적용한다.
#   1) 호스트 MySQL(3306/33060) 외부 inbound 차단 (loopback은 영향 없음)
#   2) ott-watchdog 5분 주기 스케줄 작업 등록 (컨테이너 재감염 자동 탐지·격리)
# 되돌리기는 각 항목 주석 참고.
# ============================================================================
$ErrorActionPreference = 'Continue'
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { Write-Host "[중단] 관리자 권한으로 다시 실행하세요 (PowerShell 우클릭 → 관리자 권한으로 실행)." -ForegroundColor Red; return }

Write-Host "=== 1) MySQL 외부 노출 차단 ===" -ForegroundColor Cyan
foreach ($p in 3306,33060) {
  $name = "Block MySQL $p inbound (incident 2026-06)"
  if (Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue) {
    Write-Host "[skip] 이미 존재: $name"
  } else {
    New-NetFirewallRule -DisplayName $name -Direction Inbound -Protocol TCP -LocalPort $p -Action Block -Profile Any | Out-Null
    Write-Host "[OK] 차단 규칙 추가: $name (loopback 사용은 그대로 가능)"
  }
}
# 되돌리기: Remove-NetFirewallRule -DisplayName "Block MySQL 3306 inbound (incident 2026-06)"
#           Remove-NetFirewallRule -DisplayName "Block MySQL 33060 inbound (incident 2026-06)"

Write-Host "=== 2) ott-watchdog 스케줄 작업 등록(5분 주기) ===" -ForegroundColor Cyan
$taskName = "OTT Security Watchdog"
$script   = "C:\solo-project\ott-project\security\ott-watchdog.ps1"
if (-not (Test-Path $script)) { Write-Host "[경고] 워치독 스크립트 없음: $script" -ForegroundColor Yellow }
else {
  $action  = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`""
  $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date) -RepetitionInterval (New-TimeSpan -Minutes 5)
  # S4U = '로그온 여부와 무관하게 실행' → 백그라운드(세션0)에서 동작하여 콘솔 창이 뜨지 않음.
  # (Interactive로 하면 -WindowStyle Hidden이어도 powershell 창이 매번 깜빡인다.)
  $principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType S4U -RunLevel Highest
  $settings  = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -Hidden -ExecutionTimeLimit (New-TimeSpan -Minutes 4)
  # -Force: 이미 있으면 덮어써서 갱신(재실행 시 설정 교정)
  Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
  Write-Host "[OK] 등록/갱신: '$taskName' (S4U 백그라운드 5분 주기, 창 안 뜸)"
}
# 되돌리기: Unregister-ScheduledTask -TaskName "OTT Security Watchdog" -Confirm:$false

Write-Host "=== 3) ott-uptime 스케줄 작업 등록(1분 주기) ===" -ForegroundColor Cyan
$uptimeName   = "OTT Uptime Monitor"
$uptimeScript = "C:\solo-project\ott-project\security\ott-uptime.ps1"
if (-not (Test-Path $uptimeScript)) { Write-Host "[경고] 업타임 스크립트 없음: $uptimeScript" -ForegroundColor Yellow }
else {
  $uAction  = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$uptimeScript`""
  $uTrigger = New-ScheduledTaskTrigger -Once -At (Get-Date) -RepetitionInterval (New-TimeSpan -Minutes 1)
  $uPrincipal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType S4U -RunLevel Highest
  $uSettings  = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -Hidden -ExecutionTimeLimit (New-TimeSpan -Minutes 2)
  Register-ScheduledTask -TaskName $uptimeName -Action $uAction -Trigger $uTrigger -Principal $uPrincipal -Settings $uSettings -Force | Out-Null
  Write-Host "[OK] 등록/갱신: '$uptimeName' (S4U 백그라운드 1분 주기, 다운/복구 시 Discord 알림)"
}
# 되돌리기: Unregister-ScheduledTask -TaskName "OTT Uptime Monitor" -Confirm:$false

Write-Host "=== 검증 ===" -ForegroundColor Cyan
Get-NetFirewallRule -DisplayName "Block MySQL * inbound (incident 2026-06)" -ErrorAction SilentlyContinue | Select-Object DisplayName, Enabled, Action | Format-Table -AutoSize
Get-ScheduledTask -TaskName $taskName, $uptimeName -ErrorAction SilentlyContinue | Select-Object TaskName, State | Format-Table -AutoSize
Write-Host "완료. 로그: security\watchdog.log / security\uptime.log" -ForegroundColor Green
