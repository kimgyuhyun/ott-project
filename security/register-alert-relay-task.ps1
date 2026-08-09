# ============================================================================
# register-alert-relay-task.ps1  —  경보 중계 스케줄 작업 등록 (관리자 권한 필요)
# ----------------------------------------------------------------------------
# ott-alert-relay.ps1 을 5분마다 돌린다. 설정은 'OTT Security Watchdog' 과 맞췄다:
#   S4U 로그온(비밀번호 없이 로그오프 상태에서도 실행) · 최고 권한 · 중복 실행 금지 · 4분 제한
#
# 사용: 관리자 PowerShell 에서
#   powershell -ExecutionPolicy Bypass -File C:\solo-project\ott-project\security\register-alert-relay-task.ps1
#
# 되돌리기:
#   Unregister-ScheduledTask -TaskName 'OTT Alert Relay' -Confirm:$false
# ============================================================================
$ErrorActionPreference = 'Stop'

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
           ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { throw '관리자 권한으로 실행해야 한다(Register-ScheduledTask 가 Access denied 로 실패한다).' }

$script = 'C:\solo-project\ott-project\security\ott-alert-relay.ps1'
if (-not (Test-Path $script)) { throw "중계 스크립트가 없다: $script" }

$action  = New-ScheduledTaskAction -Execute 'powershell.exe' `
             -Argument "-NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`""
# -Once + RepetitionInterval 은 워치독과 같은 구성이다. 시작 시각을 오늘 0시로 두면
# 등록 직후부터 5분 격자에 맞춰 돈다.
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).Date -RepetitionInterval (New-TimeSpan -Minutes 5)
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType S4U -RunLevel Highest
# StartWhenAvailable: 호스트가 잠들었다 깨면 놓친 주기를 한 번 따라잡는다.
$settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew `
             -ExecutionTimeLimit (New-TimeSpan -Minutes 4) -StartWhenAvailable

Register-ScheduledTask -TaskName 'OTT Alert Relay' -Action $action -Trigger $trigger `
  -Principal $principal -Settings $settings -Force `
  -Description 'Prometheus firing 경보를 디스코드로 중계(이 스택에는 Alertmanager 가 없다). 5분 주기.' | Out-Null

Get-ScheduledTask -TaskName 'OTT Alert Relay' | Select-Object TaskName, State
Write-Host '등록 완료. 발송 경로 확인: powershell -File ott-alert-relay.ps1 -Test'
