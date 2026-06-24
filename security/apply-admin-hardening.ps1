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
  if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) {
    Write-Host "[skip] 이미 등록됨: $taskName"
  } else {
    $action  = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`""
    $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date) -RepetitionInterval (New-TimeSpan -Minutes 5)
    # 현재 사용자 컨텍스트로 실행(Docker Desktop 접근 가능). 로그온 시 동작.
    $principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Highest
    $settings  = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 4)
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings | Out-Null
    Write-Host "[OK] 등록됨: '$taskName' (5분마다 ott-frontend/ott-app 점검)"
  }
}
# 되돌리기: Unregister-ScheduledTask -TaskName "OTT Security Watchdog" -Confirm:$false

Write-Host "=== 검증 ===" -ForegroundColor Cyan
Get-NetFirewallRule -DisplayName "Block MySQL * inbound (incident 2026-06)" -ErrorAction SilentlyContinue | Select-Object DisplayName, Enabled, Action | Format-Table -AutoSize
Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue | Select-Object TaskName, State | Format-Table -AutoSize
Write-Host "완료. 워치독 로그: C:\solo-project\ott-project\security\watchdog.log" -ForegroundColor Green
