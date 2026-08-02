#!/usr/bin/env bash
# SpotBugs 리포트에서 보안 범주만 골라 배포 게이트로 쓴다.
#
# 빌드 자체는 SpotBugs 지적으로 실패하지 않게 두었다(ignoreFailures = true).
# 스타일·성능 지적까지 차단으로 두면 코드 변경 없이 파이프라인이 멈추고, 그러면 결국
# 게이트를 끄게 된다 — PLATFORM.md 7절 [상황]과 6절이 같은 이유로 경고와 차단을 나눈다.
# 그래서 차단은 이 스크립트가 범주로만 판정한다.
#
# 이식: 두 환경변수만 바꾸면 다른 Gradle 프로젝트에서 그대로 쓴다.
#   SPOTBUGS_REPORT   SpotBugs XML 리포트 경로
#   BLOCK_CATEGORIES  차단할 범주(공백 구분)
set -euo pipefail

SPOTBUGS_REPORT="${SPOTBUGS_REPORT:-backend/build/reports/spotbugs/main.xml}"
BLOCK_CATEGORIES="${BLOCK_CATEGORIES:-SECURITY}"

fail() { echo "SPOTBUGS SECURITY GATE FAILED: $*" >&2; exit 1; }

# 리포트가 없다는 것은 분석이 돌지 않았다는 뜻이다. 음성 결과를 통과 근거로 삼지 않는다.
[ -f "$SPOTBUGS_REPORT" ] || fail "리포트가 없다: $SPOTBUGS_REPORT (SpotBugs 가 실행되지 않았다)"

# "지적이 0건"과 "검사할 대상이 없었다"를 구분한다. 빈 소스셋에 대해서도 지적 0건 리포트는
# 그대로 나오므로, 리포트 존재와 지적 0건만으로는 통과 근거가 되지 않는다.
# 리포트의 <Jar> 는 SpotBugs 에 실제로 넘어간 분석 대상 목록이다.
inputs="$(grep -c '<Jar>' "$SPOTBUGS_REPORT" || true)"
inputs="${inputs:-0}"
[ "$inputs" -gt 0 ] || fail "분석 대상이 0개다. 지적이 없는 것이 아니라 검사할 클래스가 넘어가지 않았다."

# 요약의 total_classes 는 정상 실행에서도 0 으로 나와서 신호가 되지 못한다. 예전에 이 값을
# 분석이 죽은 증거로 오해한 적이 있어 아예 지웠다. 분석이 돌았는지는 위의 <Jar> 개수로 본다.
total_bugs="$(grep -c '<BugInstance ' "$SPOTBUGS_REPORT" || true)"

# 요약을 잡 로그 안에만 두면 확인할 때마다 로그인해서 로그를 펼쳐야 한다. 액션 요약에도
# 같은 내용을 남긴다. 로컬 실행에는 이 변수가 없으므로 그때는 stdout 만 쓴다.
say() {
  echo "$1"
  [ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "$1" >> "$GITHUB_STEP_SUMMARY"
  return 0
}

say "spotbugs report   : $SPOTBUGS_REPORT"
say "analysis inputs   : $inputs"
say "findings (all)    : ${total_bugs:-0}  <- 스타일·성능 포함, 차단하지 않음"
say "blocking categories: $BLOCK_CATEGORIES"

blocking=0
for cat in $BLOCK_CATEGORIES; do
  # BugInstance 요소만 센다. 리포트 끝의 BugPattern 정의 블록에도 category 속성이 있어서
  # 파일 전체에서 category 를 세면 실제 지적 건수보다 부풀려진다.
  # grep 은 매치가 없으면 1 로 끝난다. pipefail 이 걸려 있으므로 그대로 두면 지적이
  # 0건일 때 스크립트가 죽는다 - 통과해야 할 상황에서 실패하는 셈이다.
  n="$({ grep -o "<BugInstance [^>]*category=\"$cat\"" "$SPOTBUGS_REPORT" || true; } | wc -l | tr -d ' ')"
  n="${n:-0}"
  say "  $cat : $n"
  if [ "$n" -gt 0 ]; then
    blocking=$(( blocking + n ))
    # 어떤 룰에 걸렸는지 남긴다. 개수만 있으면 고칠 수가 없다.
    grep -o "<BugInstance type=\"[^\"]*\"[^>]*category=\"$cat\"" "$SPOTBUGS_REPORT" \
      | sed 's/.*type="\([^"]*\)".*/    - \1/' | sort | uniq -c || true
  fi
done

[ "$blocking" -eq 0 ] || fail "보안 범주 지적 ${blocking}건. 위 목록을 고치거나, 도달 불가 근거와 만료일을 적은 예외로 관리해라."

echo "spotbugs security gate passed"
