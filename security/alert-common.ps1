# ============================================================================
# alert-common.ps1  —  디스코드 발송 공용 함수
# ----------------------------------------------------------------------------
# ott-watchdog.ps1 과 ott-alert-relay.ps1 이 함께 쓴다.
#
# 왜 한 곳에 두는가: 이 함수는 이미 두 번 고쳐졌다. 웹훅 URL 파싱(eb9025b)과
# 한글 깨짐(fda6a92). 두 벌로 복사해두면 다음 수정이 한쪽에만 적용되고, 그때
# 어느 스크립트의 알림이 깨졌는지는 알림이 안 와야 알게 된다.
#
# 호출하는 쪽 전제(dot-source 하기 전에 정의돼 있어야 한다):
#   $base — security 디렉터리 경로(웹훅 파일을 여기서 찾는다)
#   Log   — 한 줄 로깅 함수. 스크립트마다 로그 파일이 다르므로 여기서 만들지 않는다.
# ============================================================================

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
