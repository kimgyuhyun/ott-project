# secure-ssh.ps1  —  반드시 "관리자 권한" PowerShell에서 실행
# 목적: 데스크탑 서버의 인터넷 노출 SSH(22번 + 비밀번호 로그인) 위험을 제거한다.
#       지금은 외부접속 안 하므로 완전히 꺼두고, 나중에 Tailscale 등으로 안전하게 재오픈.
# 되돌리기: sshd 재가동 = Set-Service sshd -StartupType Automatic; Start-Service sshd
#           방화벽 = Remove-NetFirewallRule -DisplayName "Block SSH 22 inbound (incident 2026-06)"

$ErrorActionPreference = 'Continue'
Write-Host "=== SSH 외부노출 차단 시작 ===" -ForegroundColor Cyan

# 0) 관리자 확인
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { Write-Host "[중단] 관리자 권한이 아닙니다. PowerShell을 '관리자 권한으로 실행' 후 다시 돌리세요." -ForegroundColor Red; return }
Start-Transcript -Path (Join-Path $PSScriptRoot 'secure-ssh.log') -Force | Out-Null

# 1) sshd 서비스 중지 + 비활성화
Stop-Service sshd -Force -ErrorAction SilentlyContinue
Set-Service  sshd -StartupType Disabled -ErrorAction SilentlyContinue
Write-Host "[OK] sshd 중지 + 시작유형 Disabled"

# 2) 방화벽: 기존 OpenSSH inbound 허용 규칙 끄고, 22 inbound 명시적 차단 규칙 추가
Get-NetFirewallRule -Direction Inbound -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -match 'OpenSSH' -or $_.DisplayName -match 'OpenSSH' } |
  ForEach-Object { Disable-NetFirewallRule -Name $_.Name -ErrorAction SilentlyContinue; Write-Host "[OK] 허용규칙 비활성화: $($_.DisplayName)" }
if (-not (Get-NetFirewallRule -DisplayName "Block SSH 22 inbound (incident 2026-06)" -ErrorAction SilentlyContinue)) {
  New-NetFirewallRule -DisplayName "Block SSH 22 inbound (incident 2026-06)" -Direction Inbound -Protocol TCP -LocalPort 22 -Action Block | Out-Null
  Write-Host "[OK] 22번 inbound 차단 규칙 추가"
}

# 3) sshd_config: 비밀번호 로그인 끄기 (백업 후) — 재오픈 대비 사전 하드닝
$cfg = "C:\ProgramData\ssh\sshd_config"
if (Test-Path $cfg) {
  Copy-Item $cfg "$cfg.bak" -Force
  $c = Get-Content $cfg -Raw
  if ($c -match '(?m)^\s*#?\s*PasswordAuthentication\s+\w+') {
    $c = [regex]::Replace($c, '(?m)^\s*#?\s*PasswordAuthentication\s+\w+', 'PasswordAuthentication no')
  } else { $c = $c.TrimEnd() + "`r`nPasswordAuthentication no`r`n" }
  Set-Content -Path $cfg -Value $c -Encoding ascii
  Write-Host "[OK] sshd_config: PasswordAuthentication no  (백업: $cfg.bak)"
}

# 4) 검증
Write-Host "=== 검증 ===" -ForegroundColor Cyan
$svc = Get-Service sshd
Write-Host ("sshd : " + $svc.Status + " / " + $svc.StartType)
$listen = Get-NetTCPConnection -LocalPort 22 -State Listen -ErrorAction SilentlyContinue
if ($listen) { Write-Host "22번 리스닝: 아직 열림 — 확인 필요" -ForegroundColor Red }
else { Write-Host "22번 리스닝: 없음 (닫힘) [완료]" -ForegroundColor Green }
Write-Host ""
Write-Host "남은 한 가지(선택): 공유기 관리자페이지에서 22번 포트포워딩도 해제하면 완전히 끝." -ForegroundColor Yellow
Stop-Transcript | Out-Null
