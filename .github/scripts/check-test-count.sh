#!/usr/bin/env bash
# 테스트가 "실제로 실행됐는지"를 배포 게이트로 쓴다.
#
# 빌드 성공은 테스트가 실행됐다는 뜻이 아니다. JUnit 플랫폼 설정이 빠지거나 조건부
# 비활성화(@EnabledIf 계열)가 걸리면 실행 0건에도 BUILD SUCCESSFUL 이 뜬다.
# 그래서 통과 여부가 아니라 결과 XML 의 tests 속성 합계를 읽어 판정한다.
#
# 총량 하한만으로는 부족하다. 조건부 비활성화는 보통 특정 클래스 묶음만 통째로 빼는데,
# 그 묶음이 전체에서 차지하는 비중이 작으면 하한을 넘겨버린다. 그래서 실패 비용이 큰
# 경로(결제·인증)는 클래스 단위로 실행 건수가 0 이 아닌지 따로 확인한다.
#
# 이식: 아래 세 환경변수만 바꾸면 다른 Gradle 프로젝트에서 그대로 쓴다.
#   RESULTS_DIR      JUnit XML 디렉터리
#   MIN_TESTS        실행 건수 하한
#   REQUIRED_CLASSES 개별 확인할 클래스 이름(공백 구분, 패키지 생략 가능)
set -euo pipefail

RESULTS_DIR="${RESULTS_DIR:-backend/build/test-results/test}"
MIN_TESTS="${MIN_TESTS:-1}"
REQUIRED_CLASSES="${REQUIRED_CLASSES:-}"

fail() { echo "TEST-COUNT GATE FAILED: $*" >&2; exit 1; }

[ -d "$RESULTS_DIR" ] || fail "결과 디렉터리가 없다: $RESULTS_DIR (테스트가 아예 실행되지 않았다)"

shopt -s nullglob
files=("$RESULTS_DIR"/TEST-*.xml)
[ ${#files[@]} -gt 0 ] || fail "$RESULTS_DIR 에 TEST-*.xml 이 없다 (테스트가 아예 실행되지 않았다)"

# 한 파일의 <testsuite ...> 에서 속성 하나를 읽는다.
attr() { grep -o '<testsuite [^>]*' "$1" | head -1 | grep -o "$2=\"[0-9]*\"" | grep -o '[0-9]*' | head -1; }

total=0
skipped=0
for f in "${files[@]}"; do
  t="$(attr "$f" tests || true)"
  s="$(attr "$f" skipped || true)"
  total=$(( total + ${t:-0} ))
  skipped=$(( skipped + ${s:-0} ))
done
executed=$(( total - skipped ))

echo "test result files : ${#files[@]}"
echo "tests declared    : $total"
echo "skipped           : $skipped"
echo "executed          : $executed  (하한 $MIN_TESTS)"

[ "$executed" -ge "$MIN_TESTS" ] || fail "실행된 테스트가 $executed 건으로 하한 $MIN_TESTS 미만이다"

missing=""
for cls in $REQUIRED_CLASSES; do
  cls_exec=0
  found=0
  for f in "$RESULTS_DIR"/TEST-*"$cls"*.xml; do
    found=1
    t="$(attr "$f" tests || true)"
    s="$(attr "$f" skipped || true)"
    cls_exec=$(( cls_exec + ${t:-0} - ${s:-0} ))
  done
  if [ "$found" -eq 0 ] || [ "$cls_exec" -eq 0 ]; then
    missing="$missing $cls"
  else
    echo "  required: $cls -> $cls_exec executed"
  fi
done

[ -z "$missing" ] || fail "핵심 경로 테스트가 실행되지 않았다:$missing"

echo "test count gate passed"
