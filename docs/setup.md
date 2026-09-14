# 새 기기 세팅

`git pull` 로 따라오지 않는 것이 일곱 있다. 일곱 다 빠져도 경고가 없고 검사만 조용히 사라지므로, 클론 직후 한 번에 해둔다.

```powershell
git config core.hooksPath .githooks            # 없으면 git 훅 2개가 통째로 안 돎
git config blame.ignoreRevsFile .git-blame-ignore-revs
winget install gitleaks                        # 없으면 pre-commit 이 커밋을 전부 막는다
winget install rhysd.actionlint                # 없으면 iac-lint 를 로컬에서 못 돌린다 (CI 가 고정한 1.7.12 와 같은 버전)
winget install koalaman.shellcheck             # actionlint 가 run: 블록을 검사할 때 쓴다 - 없으면 그 검사만 조용히 빠지고 exit 0 이 나온다
cd frontend; npm ci                            # 없으면 prettier·tsc 훅이 조용히 통과
New-Item -ItemType Junction -Path $HOME\.claude\skills -Target C:\dev-standards\skills   # 없으면 절차 스킬이 조용히 사라짐
```

actionlint 와 shellcheck 는 gitleaks 와 달리 winget 이 `Links` 가 아니라
`%LOCALAPPDATA%\Microsoft\WinGet\Packages\<패키지ID>\` 에 두기 때문에 PATH 에 안 잡힌다.
그 두 폴더를 PATH 에 넣고 쓴다. `iac-lint` 잡이 도는 명령은 `actionlint` 와
`shellcheck -x .github/scripts/*.sh .githooks/pre-commit` 둘이다.

`.claude/settings.local.json` 은 `.gitignore` 대상이라 직접 만든다. JDK 경로가 기기마다 달라서 분리해 둔 파일이고, 이게 없으면 `backend-test-mirror.js` 가 JAVA_HOME 을 못 찾아 조용히 통과한다.

```json
{ "env": { "JAVA_HOME": "C:\\Users\\USER\\.jdks\\liberica-21.0.7" } }
```

확인: `git config core.hooksPath` 가 `.githooks` 를 출력하고 `gitleaks version` 과 `actionlint -version` 이 돌면 된다.
