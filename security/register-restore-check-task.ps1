# ============================================================================
# register-restore-check-task.ps1  —  백업 점검 예약 작업 등록 (관리자 권한 필요)
# ----------------------------------------------------------------------------
# restore-drill.ps1 -Mode Check 를 매일 04:15 에 돌린다.
#
# 왜 04:15 인가 (이 호스트의 새벽 작업 배치):
#   04:00  ott-db-backup    DB 백업 (약 10초)
#   04:15  ott-restore-check  ← 이 작업. 방금 올라간 백업을 점검한다
#   04:30  ott-log-backup   로그 백업
#   백업 직후여야 의미가 있고, 로그 백업과는 겹치지 않아야 한다.
#
# 왜 Check 모드만 자동인가:
#   Full 모드는 age 개인키가 있어야 하는데 그 키는 이 호스트에 없다(의도된 설계).
#   전체 복원 훈련은 분기마다 사람이 키를 꺼내 손으로 돌린다 — docs/restore-runbook.md 참고.
#
# 사용: 관리자 PowerShell 에서
#   powershell -ExecutionPolicy Bypass -File C:\solo-project\ott-project\security\register-restore-check-task.ps1
#
# 되돌리기:
#   Unregister-ScheduledTask -TaskName 'ott-restore-check' -Confirm:$false
# ============================================================================
$ErrorActionPreference = 'Stop'

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
           ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { throw '관리자 권한으로 실행해야 한다(Register-ScheduledTask 가 Access denied 로 실패한다).' }

$taskName = 'ott-restore-check'
$script   = 'C:\solo-project\ott-project\security\restore-drill.ps1'
if (-not (Test-Path $script)) { throw "점검 스크립트가 없다: $script" }

# 구성은 'OTT Alert Relay'(S4U·최고권한) 가 아니라 ott-db-backup 을 따른다.
# 이 작업은 백업과 의존물이 같다 — docker CLI(Docker Desktop 은 사용자 세션에서 돈다)와
# rclone 설정(%APPDATA%\rclone\rclone.conf, 사용자 프로필 안). 그 조합으로 04:00 작업이
# 실제로 매일 성공하고 있으므로, 검증된 쪽을 그대로 쓴다.
$action = New-ScheduledTaskAction -Execute 'powershell' `
            -Argument "-ExecutionPolicy Bypass -File `"$script`" -Mode Check"

$trigger = New-ScheduledTaskTrigger -Daily -At '04:15'

$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited

# ExecutionTimeLimit 은 ott-db-backup 의 72시간을 따라가지 않는다. 이 점검은 R2 조회 몇 번이라
# 수 초면 끝나고, MultipleInstances=IgnoreNew 와 묶이면 한 번 멈춘 작업이 다음 실행들을
# 사흘 동안 막게 된다. 30분이면 넉넉하면서 그 상태에 갇히지 않는다.
$settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew `
             -ExecutionTimeLimit (New-TimeSpan -Minutes 30) -StartWhenAvailable

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger `
  -Principal $principal -Settings $settings -Force `
  -Description '백업이 계속 도착하는지 매일 점검(존재·신선도·age 형식·크기 급변). 실패 시 디스코드 알림. 전체 복원 훈련은 개인키가 필요해 분기별 수동 — docs/restore-runbook.md' | Out-Null

Get-ScheduledTask -TaskName $taskName | Select-Object TaskName, State
Write-Host ''
Write-Host '등록 완료. 지금 한 번 돌려서 확인:'
Write-Host "  powershell -ExecutionPolicy Bypass -File `"$script`" -Mode Check"
Write-Host '알림 경로까지 확인하려면 백업이 26시간 넘게 없을 때 실패 알림이 오는지 보면 된다.'
