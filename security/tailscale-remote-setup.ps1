# tailscale-remote-setup.ps1  —  반드시 "관리자 권한" PowerShell에서 실행
# 목적: 포트포워딩 없이 Tailscale(메시 VPN) 안에서만 이 데스크탑 서버에 SSH 접속.
#       공개 인터넷엔 22번을 절대 열지 않는다 (2026-06 크립토재킹 인시던트 재발 방지).
# 전제: 공유기의 22번 포트포워딩은 계속 "해제" 상태로 둘 것. 이 스크립트는 라우터를 건드리지 않는다.
# 되돌리기: 파일 하단 주석 참고.

$ErrorActionPreference = 'Stop'
Write-Host "=== Tailscale 원격 SSH 세팅 시작 ===" -ForegroundColor Cyan

# 0) 관리자 확인
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { Write-Host "[중단] 관리자 권한이 아닙니다. PowerShell을 '관리자 권한으로 실행' 후 다시 돌리세요." -ForegroundColor Red; return }

# 1) Tailscale 설치 (이미 있으면 skip)
$tsExe = "C:\Program Files\Tailscale\tailscale.exe"
if (-not (Test-Path $tsExe)) {
  Write-Host "[*] Tailscale 설치 중 (winget)..."
  winget install -e --id Tailscale.Tailscale --accept-source-agreements --accept-package-agreements
} else { Write-Host "[OK] Tailscale 이미 설치됨" }
Start-Service Tailscale -ErrorAction SilentlyContinue

# 2) OpenSSH Server 설치 확인 + 서비스 자동시작
$cap = Get-WindowsCapability -Online | Where-Object { $_.Name -like 'OpenSSH.Server*' }
if ($cap -and $cap.State -ne 'Installed') {
  Write-Host "[*] OpenSSH Server 설치 중..."
  Add-WindowsCapability -Online -Name $cap.Name | Out-Null
}
Set-Service sshd -StartupType Automatic
Start-Service sshd
Set-Service ssh-agent -StartupType Automatic -ErrorAction SilentlyContinue
Write-Host "[OK] sshd 실행 + 자동시작"

# 3) 방화벽: 공개 노출 없이 Tailscale 대역(100.64.0.0/10)에서만 22 허용
#    - secure-ssh.ps1이 만든 광역 Block 규칙 제거 (Block은 Allow보다 우선 → 안 지우면 tailnet SSH도 막힘)
Get-NetFirewallRule -DisplayName "Block SSH 22 inbound (incident 2026-06)" -ErrorAction SilentlyContinue | Remove-NetFirewallRule
Write-Host "[OK] 광역 22번 Block 규칙 제거"
#    - 기존 광역 OpenSSH 허용 규칙(어디서나 허용)은 위험하므로 비활성 유지
Get-NetFirewallRule -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -match 'OpenSSH' } | Disable-NetFirewallRule -ErrorAction SilentlyContinue
#    - tailnet 전용 허용 규칙 추가 (이 대역 밖은 Windows 기본 정책상 inbound deny)
if (-not (Get-NetFirewallRule -DisplayName "Allow SSH 22 from Tailscale only" -ErrorAction SilentlyContinue)) {
  New-NetFirewallRule -DisplayName "Allow SSH 22 from Tailscale only" -Direction Inbound -Protocol TCP -LocalPort 22 -RemoteAddress 100.64.0.0/10 -Action Allow -Profile Any | Out-Null
}
Write-Host "[OK] 22번은 Tailscale 대역에서만 허용"

# 4) Tailscale 로그인 (브라우저가 열림 — 본인 계정으로 로그인/기기 승인)
Write-Host "[*] 브라우저에서 Tailscale 로그인하세요..." -ForegroundColor Yellow
& $tsExe up

# 5) 결과 출력
$tsip = (& $tsExe ip -4) 2>$null
Write-Host "=== 완료 ===" -ForegroundColor Cyan
Write-Host ("이 서버의 Tailscale IP : " + $tsip) -ForegroundColor Green
Write-Host ("이 서버의 호스트명      : " + $env:COMPUTERNAME + "  (MagicDNS 사용 시)") -ForegroundColor Green
Write-Host ("로그인 계정(SSH 사용자) : " + $env:USERNAME) -ForegroundColor Green

# ── 되돌리기 ────────────────────────────────────────────────
# sshd 다시 끄기:      Stop-Service sshd; Set-Service sshd -StartupType Disabled
# tailnet 허용 제거:   Remove-NetFirewallRule -DisplayName "Allow SSH 22 from Tailscale only"
# 광역 차단 복원:      New-NetFirewallRule -DisplayName "Block SSH 22 inbound (incident 2026-06)" -Direction Inbound -Protocol TCP -LocalPort 22 -Action Block
# Tailscale 로그아웃:  & "C:\Program Files\Tailscale\tailscale.exe" logout
