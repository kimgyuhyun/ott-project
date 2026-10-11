#!/usr/bin/env bash
# [측정 전용] rolling.sh 결과 요약. 접근 로그에서 upstream 이 둘 이상 적힌 줄 = 다른 인스턴스로 재시도된 요청.
out=$1
echo "== $out"; cat "$out/timeline"
awk '$4 !~ /login/ { n++
       if ($2 != 200) { bad++; print "  non200:", $0 }
       if ($0 ~ /, /) { retried++; if (shown++ < 5) print "  retried:", $0 } }
     END { printf "requests=%d non200=%d retried_to_other=%d\n", n, bad+0, retried+0 }' "$out/nginx-access.log" | tail -30
grep -E "rate=|↳" "$out/k6.txt" | head -4
