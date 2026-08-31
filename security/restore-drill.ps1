# ============================================================================
# restore-drill.ps1  --  proves the backup actually restores
# ----------------------------------------------------------------------------
# 왜 있나:
#   db-backup.ps1 은 매일 pg_dump 를 떠서 age 로 암호화하고 R2 에 올린다. 그런데 그 덤프로
#   DB 가 실제로 되살아나는지는 한 번도 확인한 적이 없었다 - 유일한 검증이 "파일이 0바이트가
#   아니다" 한 줄이었다. 덤프가 잘렸거나, 스키마 일부가 빠졌거나, 롤이 없어 복원이 중간에
#   멈춰도 백업은 계속 "성공"으로 찍힌다. 백업이 있다는 것과 복원이 된다는 것은 다른 얘기다.
#
#   2026-08-30 에 카프카에서 정확히 그 종류의 문제가 나왔다: kafka_data 볼륨이 붙어는 있는데
#   브로커가 그걸 안 쓰고 컨테이너 쓰기 레이어에 쓰고 있었다. 아무도 복원을 시도한 적이
#   없어서 몇 달 동안 안 보였다.
#
# 개인키를 서버에 두지 않는다:
#   db-backup.ps1 머리말대로 age 개인키는 이 호스트에 없다. 백업을 푸는 열쇠를 백업 옆에
#   두지 않겠다는 결정이고, 그 결정은 그대로 둔다. 그래서 훈련을 두 단으로 나눈다.
#
#     -Mode Check  (기본, 키 불필요) - 매일 백업 직후 자동으로 돌린다.
#         R2 의 최신 덤프가 존재하는가 / 충분히 최근인가 / age 파일이 맞는가 /
#         크기가 직전 것과 크게 어긋나지 않는가. 복호화는 못 하지만
#         "백업이 조용히 멈췄다", "덤프가 갑자기 쪼그라들었다"를 잡는다.
#
#     -Mode Full -KeyFile <경로>  (키 필요) - 사람이 분기마다 한 번 손으로 돌린다.
#         내려받아 복호화하고, 격리된 빈 postgres 에 실제로 복원하고, 스키마와 데이터를
#         검증하고 철거한다. 키 파일은 읽기만 하고 어디에도 복사하지 않는다.
#
#     -Mode Full -DumpFile <경로.sql>  (키 불필요) - 평문 덤프로 복원 절차 자체를 시험한다.
#         훈련 스크립트를 고친 뒤 그 자체가 멀쩡한지 확인할 때 쓴다.
#
# 실서비스는 건드리지 않는다: 복원은 docker-compose.restore-test.yml 을 -p ott-restore-test 로
# 띄운 별도 스택에서만 일어난다(호스트 포트 없음, internal 망, 매번 down -v).
#
# 이 파일은 UTF-8 BOM 으로 저장해야 한다. Windows PowerShell 5.1 은 BOM 이 없으면 .ps1 을
# 시스템 ANSI 코드페이지로 읽어서 위 주석의 한글이 깨지고, 깨진 바이트가 따옴표로 해석되면
# 파싱 자체가 실패한다(실제로 겪음). 화면에 찍는 문자열은 콘솔 코드페이지까지 얽히므로
# 다른 배포 스크립트들과 같이 영어로 둔다.
# ============================================================================
[CmdletBinding()]
param(
    [ValidateSet('Check', 'Full')][string]$Mode = 'Check',
    [string]$KeyFile,
    [string]$DumpFile,
    [string]$GlobalsFile,
    [int]$MaxAgeHours = 26
)

$ErrorActionPreference = 'Stop'
$base = $PSScriptRoot           # alert-common.ps1 이 웹훅 파일을 찾는 기준 경로
Set-Location (Join-Path $PSScriptRoot '..')

# Check 모드는 예약 작업으로 무인 실행된다. 실패해도 아무도 안 보면 "백업이 멈췄다"를
# 잡으려고 만든 검사가 자기도 조용히 멈춰 있는 꼴이 되므로, 실패는 디스코드로 알린다.
# 발송 함수는 alert-common.ps1 의 것을 그대로 쓴다 — 그 파일 주석대로 이 함수는 이미
# 두 번 고쳐졌고(웹훅 파싱, 한글 깨짐) 복사본을 늘리면 다음 수정이 한쪽에만 적용된다.
# 그쪽이 요구하는 전제는 $base 와 Log 두 개다.
$logFile = Join-Path $PSScriptRoot 'restore-drill.log'
function Log($m) {
    $line = "{0} {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $m
    Write-Host $line
    try { Add-Content -Path $logFile -Value $line -Encoding UTF8 } catch { }
}
. (Join-Path $PSScriptRoot 'alert-common.ps1')

$Remote      = 'r2:ott-db-backups'
$Project     = 'ott-restore-test'
$ComposeFile = 'docker-compose.restore-test.yml'
$Container   = 'ott-restore-postgres'
$LiveDb      = 'ott-postgres'
$Db          = 'ott_project_db'
$DbUser      = 'root'           # 살아있는 DB 를 조회할 때 쓰는 계정
# 복원 스택의 부트스트랩 계정. root 와 겹치면 globals 의 CREATE ROLE root 가 충돌한다
# (docker-compose.restore-test.yml 의 POSTGRES_USER 주석 참고).
$DrillUser   = 'drill_admin'

# 값 하나를 읽어온다. 판정은 호출한 쪽이 한다.
# try/catch 가 필요한 이유: psql 이 SQL 오류를 stderr 로 내면 $ErrorActionPreference='Stop'
# 아래에서 네이티브 stderr 가 종료 오류가 되어, "값을 못 읽었다"가 아니라 스크립트가 통째로
# 죽는다. 그러면 검증 결과 대신 PowerShell 스택이 찍힌다(실제로 겪음 - 없는 테이블 이름 하나에
# 훈련 전체가 멈췄다). 여기서는 빈 문자열을 돌려주고, 호출한 쪽이 그걸 실패로 기록하게 한다.
function Get-Scalar {
    param([string]$Cont, [string]$Sql, [string]$User = $DbUser)
    try { ((docker exec $Cont psql -U $User -d $Db -tAc $Sql 2>$null) | Out-String).Trim() }
    catch { '' }
}

# 일회용 스택을 지운다. 기동 직전과 철거 때 두 번 쓴다.
#
# try/catch 가 장식이 아니다: docker compose 는 진행 상황("Container ... Stopping")을 stderr 로
# 쓰는데, $ErrorActionPreference='Stop' 아래에서 네이티브 stderr 는 종료 오류가 된다.
# 특히 `2>&1 | Out-Null` 로 묶으면 각 줄이 ErrorRecord 가 되어 확실히 터진다.
# 이걸 finally 안에서 맞으면 원래 실패 원인이 이 예외로 덮여서, 복원이 왜 실패했는지가
# 화면에서 사라진다(실제로 그렇게 한 번 놓쳤다). 그래서 여기서 삼킨다 —
# 철거가 덜 됐으면 다음 실행의 down -v 가 어차피 다시 지운다.
function Remove-RestoreStack {
    try { docker compose -p $Project -f $ComposeFile down -v | Out-Null } catch { }
}

# ---------------------------------------------------------------- Check 모드
# 개인키 없이 확인할 수 있는 것만 본다. 여기서 통과했다고 복원이 된다는 뜻은 아니다 -
# 그건 Full 모드만 말할 수 있다. 이 단계의 목적은 "백업이 계속 오고 있는가"다.
function Invoke-CheckMode {
    Write-Host '=== VERIFY backup presence / freshness / size (no private key needed) ==='

    $lines = @(rclone lsl $Remote --include 'db_*.sql.age' 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "rclone could not read R2: $lines" }

    $items = foreach ($l in $lines) {
        if ($l -match '^\s*(\d+)\s+(\S+)\s+(\S+)\s+(.+)$') {
            [pscustomobject]@{
                Size = [long]$Matches[1]
                Time = [datetime]::Parse(($Matches[2] + ' ' + $Matches[3]))
                Name = $Matches[4].Trim()
            }
        }
    }
    $sorted = @($items | Sort-Object Time)
    if ($sorted.Count -eq 0) { throw 'no backups found (db_*.sql.age)' }

    $latest = $sorted[-1]
    Write-Host ("  latest: {0}  {1:yyyy-MM-dd HH:mm}  {2:N0} bytes  ({3} total)" -f $latest.Name, $latest.Time, $latest.Size, $sorted.Count)

    # 1) 신선도 - 백업이 조용히 멈춘 경우를 잡는다. 이게 이 모드의 주된 목적이다.
    $ageHours = [math]::Round(((Get-Date) - $latest.Time).TotalHours, 1)
    if ($ageHours -gt $MaxAgeHours) { throw "latest backup is $ageHours hours old (limit $MaxAgeHours) - has the backup job stopped?" }
    Write-Host "  freshness: $ageHours hours old - OK"

    # 2) age 파일 형식 - 앞부분이 age 헤더여야 한다. 복호화가 아니라 형식만 본다.
    #    빈 파일이나 엉뚱한 것이 올라간 경우를 잡는다.
    $head = (rclone cat ($Remote + '/' + $latest.Name) --count 64 2>$null | Out-String)
    if ($head -notmatch 'age-encryption\.org') { throw 'latest backup does not look like an age file (header mismatch)' }
    Write-Host '  age header: OK'

    # 3) 크기 급변 - 덤프가 갑자기 쪼그라들면 테이블이 빠졌다는 신호다.
    #    DB 는 자라기만 하므로 "직전보다 크게 줄었다"만 실패로 본다. 늘어나는 건 정상이다.
    if ($sorted.Count -ge 2) {
        $prev  = $sorted[-2]
        $ratio = [math]::Round($latest.Size / [double]$prev.Size, 3)
        Write-Host ("  vs previous: {0:N0} -> {1:N0} bytes (x{2})" -f $prev.Size, $latest.Size, $ratio)
        if ($ratio -lt 0.9) { throw "latest backup is more than 10% smaller than the previous one (x$ratio) - was the dump truncated?" }
    }

    Write-Host '=== CHECK OK ==='
    Write-Host 'NOTE: this only says backups keep arriving. Whether they RESTORE is only answered by -Mode Full.'
}

# ----------------------------------------------------------------- Full 모드
function Invoke-FullMode {
    $work    = Join-Path $env:TEMP ('restore-drill-' + (Get-Date -Format 'yyyyMMdd_HHmmss'))
    $plain   = Join-Path $work 'dump.sql'
    $globals = Join-Path $work 'globals.sql'
    New-Item -ItemType Directory -Path $work -Force | Out-Null

    try {
        # --- 1) 평문 덤프 확보 -------------------------------------------------
        if ($DumpFile) {
            if (-not (Test-Path $DumpFile)) { throw "dump file not found: $DumpFile" }
            if (-not $GlobalsFile) { throw '-DumpFile also needs -GlobalsFile (the pg_dumpall --globals-only output). Without the roles the database dump cannot restore - that is the failure this drill exists to catch.' }
            if (-not (Test-Path $GlobalsFile)) { throw "globals file not found: $GlobalsFile" }
            Copy-Item $DumpFile $plain
            Copy-Item $GlobalsFile $globals
            Write-Host "=== dump: using local files ==="
        }
        else {
            if (-not $KeyFile) { throw '-Mode Full needs either -KeyFile (age private key) or -DumpFile. The private key is deliberately not on this host: fetch it from the password manager into a file, and delete that file when the drill is done.' }
            if (-not (Test-Path $KeyFile)) { throw "key file not found: $KeyFile" }

            Write-Host '=== downloading latest backup from R2 ==='
            $names = @(rclone lsf $Remote --include 'db_*.sql.age' 2>&1) | Sort-Object
            if ($names.Count -eq 0) { throw 'no backups found' }
            $latest = ([string]$names[-1]).Trim()
            # 같은 시각의 globals 를 짝으로 가져온다. db_<stamp>.sql.age -> globals_<stamp>.sql.age
            $globalsName = $latest -replace '^db_', 'globals_'
            Write-Host "  $latest"
            Write-Host "  $globalsName"
            rclone copy ($Remote + '/' + $latest) $work
            if ($LASTEXITCODE -ne 0) { throw 'rclone copy failed (database dump)' }
            rclone copy ($Remote + '/' + $globalsName) $work
            if ($LASTEXITCODE -ne 0) { throw "rclone copy failed (globals). Backups taken before 2026-08-30 have no globals file and cannot be restored - see db-backup.ps1." }

            Write-Host '=== decrypting ==='
            # 키는 읽기만 한다. 복호화 결과는 작업 폴더에만 두고 finally 에서 지운다.
            age -d -i $KeyFile -o $plain (Join-Path $work $latest)
            if ($LASTEXITCODE -ne 0) { throw 'age decryption failed - is this key the one this backup was encrypted to?' }
            age -d -i $KeyFile -o $globals (Join-Path $work $globalsName)
            if ($LASTEXITCODE -ne 0) { throw 'age decryption failed for the globals file' }
        }

        if ((Get-Item $plain).Length -eq 0)   { throw 'plaintext dump is empty' }
        if ((Get-Item $globals).Length -eq 0) { throw 'globals dump is empty' }
        Write-Host ("  database dump {0} MB / globals {1} bytes" -f [math]::Round((Get-Item $plain).Length / 1MB, 1), (Get-Item $globals).Length)

        # --- 2) 격리 스택을 빈 상태로 기동 -------------------------------------
        # 항상 down -v 로 먼저 지운다. 남은 데이터 위에 복원하면 "복원이 됐는지" 판정이 안 된다.
        Write-Host '=== starting isolated postgres (empty DB) ==='
        Remove-RestoreStack
        docker compose -p $Project -f $ComposeFile up -d
        if ($LASTEXITCODE -ne 0) { throw 'restore stack failed to start' }

        $health   = ''
        $deadline = (Get-Date).AddSeconds(120)
        while ((Get-Date) -lt $deadline) {
            $health = ((docker inspect $Container --format '{{.State.Health.Status}}' 2>$null) | Out-String).Trim()
            if ($health -eq 'healthy') { break }
            Start-Sleep -Seconds 2
        }
        if ($health -ne 'healthy') { throw "restore postgres was not ready within 120s (state: $health)" }
        Write-Host '  ready'

        # --- 3) 실제 복원 ------------------------------------------------------
        # ON_ERROR_STOP=1 이 핵심이다. 이게 없으면 psql 은 에러를 흘려보내고 0 으로 끝나서,
        # 절반만 복원된 DB 를 "성공"으로 읽게 된다.
        #
        # 덤프를 컨테이너 안에 두지 않고 표준입력으로 흘려보낸다. 복원용 postgres 도
        # read_only 라 쓸 곳이 없고, 있어도 이 호스트의 docker cp 는 tmpfs 를 못 읽는다(실측).
        # cmd /c 가 리다이렉트를 맡아 8MB 넘는 덤프가 PowerShell 인코딩을 타지 않게 한다 —
        # db-backup.ps1 과 같은 방식이다.
        # 순서가 중요하다: 롤이 먼저 있어야 DB 덤프의 GRANT 87줄과 OWNER TO 가 통과한다.
        # 이 순서를 안 지켜서 나온 게 이 스크립트를 만들게 한 그 실패다
        # (ERROR: role "ott_app" does not exist, db_*.sql 189145행).
        Write-Host '=== restoring globals (roles) ==='
        $globalsLog = Join-Path $work 'globals.log'
        cmd /c "docker exec -i $Container psql -v ON_ERROR_STOP=1 -U $DrillUser -d $Db -f - < `"$globals`" > `"$globalsLog`" 2>&1"
        if ($LASTEXITCODE -ne 0) {
            $tail = (Get-Content $globalsLog -Tail 15 | Out-String)
            throw ("GLOBALS RESTORE FAILED (psql exit " + $LASTEXITCODE + "). last log lines:`n" + $tail)
        }
        Write-Host '  roles created'

        Write-Host '=== restoring database (psql ON_ERROR_STOP=1, dump via stdin) ==='
        $restoreLog = Join-Path $work 'restore.log'
        cmd /c "docker exec -i $Container psql -v ON_ERROR_STOP=1 -U $DrillUser -d $Db -f - < `"$plain`" > `"$restoreLog`" 2>&1"
        $restoreExit = $LASTEXITCODE
        if ($restoreExit -ne 0) {
            $tail = (Get-Content $restoreLog -Tail 15 | Out-String)
            throw ("RESTORE FAILED (psql exit " + $restoreExit + "). last log lines:`n" + $tail)
        }
        Write-Host '  restored'

        # --- 4) 검증 -----------------------------------------------------------
        # 복원된 DB 를 살아있는 DB 와 비교한다. 기대값을 스크립트에 박아두면 스키마가 바뀔 때마다
        # 여기를 고쳐야 하고, 안 고치면 검사가 조용히 무의미해진다.
        # 행 수는 비교하지 않는다 - 덤프는 새벽 4시 것이고 그 뒤로 계속 늘어나므로 다른 게 정상이다.
        # 스키마와 마이그레이션 상태는 그 사이에 바뀌지 않으므로 정확히 같아야 한다.
        Write-Host '=== verifying against the live DB ==='
        $failed = @()

        $compare = @(
            @{ Name = 'public tables';   Sql = "select count(*) from information_schema.tables where table_schema='public';" },
            @{ Name = 'flyway version';  Sql = "select coalesce(max(version), 'none') from flyway_schema_history;" },
            @{ Name = 'flyway failures'; Sql = "select count(*) from flyway_schema_history where success = false;" }
        )
        foreach ($c in $compare) {
            $live     = Get-Scalar $LiveDb    $c.Sql
            $restored = Get-Scalar $Container $c.Sql -User $DrillUser
            $ok       = ($live -eq $restored -and $restored -ne '')
            Write-Host ("  {0,-16} live={1,-16} restored={2,-16} {3}" -f $c.Name, $live, $restored, $(if ($ok) { 'OK' } else { 'MISMATCH' }))
            if (-not $ok) { $failed += ($c.Name + ': live=' + $live + ' restored=' + $restored) }
        }

        # 양쪽이 같아도 실패한 마이그레이션이 있으면 안 된다.
        $failCount = Get-Scalar $Container "select count(*) from flyway_schema_history where success = false;" -User $DrillUser
        if ($failCount -ne '0') { $failed += ('restored DB has ' + $failCount + ' failed migrations') }

        # 핵심 테이블에 데이터가 실제로 들어왔는지. 스키마만 복원되고 COPY 가 빠지는 경우를 잡는다.
        # 행 수 일치가 아니라 "비어 있지 않다"를 본다(덤프 시점과 현재는 다른 게 정상이므로).
        # 테이블 이름은 추측하지 말 것. 이 넷은 라이브에서 행이 있는 걸 확인하고 골랐다
        # (anime 329 / episodes 6838 / plans 2 / users 8). 처음에 membership_plan 이라고
        # 적었다가 그런 테이블이 없어서 훈련이 멈췄다 - 스키마가 바뀌면 여기도 같이 고친다.
        foreach ($t in @('anime', 'episodes', 'plans', 'users')) {
            $n  = Get-Scalar $Container ("select count(*) from " + $t + ";") -User $DrillUser
            $ok = ($n -match '^\d+$' -and [int]$n -gt 0)
            Write-Host ("  {0,-16} restored={1} {2}" -f ($t + ' rows'), $n, $(if ($ok) { 'OK' } else { 'EMPTY/ERROR' }))
            if (-not $ok) { $failed += ($t + " is empty or could not be queried (value: '" + $n + "')") }
        }

        # 런타임 계정이 실제로 되살아났는지. 롤만 있고 GRANT 가 안 붙으면 복원된 DB 로 앱이 못 뜬다.
        # 이 두 줄이 2026-08-30 에 이 훈련이 잡아낸 그 결함을 지키는 자리다.
        $roleOk = (Get-Scalar $Container "select count(*) from pg_roles where rolname = 'ott_app';" -User $DrillUser)
        Write-Host ("  {0,-16} restored={1} {2}" -f 'ott_app role', $roleOk, $(if ($roleOk -eq '1') { 'OK' } else { 'MISSING' }))
        if ($roleOk -ne '1') { $failed += 'the ott_app runtime role is missing from the restored cluster' }

        # 권한까지 본다 - 롤이 있어도 GRANT 가 빠지면 앱은 권한 오류로 죽는다.
        $grantOk = (Get-Scalar $Container "select has_table_privilege('ott_app', 'public.anime', 'SELECT');" -User $DrillUser)
        Write-Host ("  {0,-16} restored={1} {2}" -f 'ott_app SELECT', $grantOk, $(if ($grantOk -eq 't') { 'OK' } else { 'DENIED' }))
        if ($grantOk -ne 't') { $failed += 'ott_app cannot SELECT on public.anime in the restored cluster - the GRANTs did not apply' }

        if ($failed.Count -gt 0) { throw ("VERIFICATION FAILED:`n  - " + ($failed -join "`n  - ")) }
        Write-Host '=== RESTORE DRILL OK ==='
        Write-Host ("this backup really restores. verified at " + (Get-Date -Format 'yyyy-MM-dd HH:mm'))
    }
    finally {
        # 철거는 실패해도 반드시 한다. 평문 덤프가 호스트에 남으면 안 된다.
        # 여기서 예외가 나면 진짜 실패 원인(위에서 던진 것)이 그 예외로 덮인다. 실제로 겪었다 —
        # Remove-RestoreStack 안의 주석 참고.
        Write-Host '=== tearing down ==='
        Remove-RestoreStack
        Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host '  throwaway stack and plaintext dump removed'
    }
}

# Check 모드만 알림으로 감싼다. Full 모드는 사람이 보고 있는 자리에서 손으로 돌리는 것이라
# 화면의 오류로 충분하고, 무인 실행이 아니므로 알림을 보낼 이유가 없다.
if ($Mode -eq 'Check') {
    try {
        Invoke-CheckMode
    }
    catch {
        Log "restore check FAILED: $($_.Exception.Message)"
        Send-DiscordAlert (":rotating_light: 백업 점검 실패 — $($_.Exception.Message)`n확인: .\security\restore-drill.ps1 -Mode Check")
        exit 1
    }
}
else { Invoke-FullMode }
