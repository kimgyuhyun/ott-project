#!/usr/bin/env bash
# [측정 전용] 정상상태 1회. 사용: run.sh <이름> <RATE>
# k6(워밍업 30s + 측정 180s)와 표본 수집을 같이 돌리고 nginx 접근 로그를 회수한다.
set -e
export MSYS_NO_PATHCONV=1
name=$1; rate=$2; dir="$(dirname "$0")"; out="$dir/results/$name"; mkdir -p "$out"
docker exec ott-e2e-nginx sh -c ': > /var/log/nginx/ka.log'
date +%s > "$out/start_epoch"
"$dir/sample.sh" "$out" 225 &
k6 run -q -e LT_PASSWORD="$LT_PASSWORD" -e RATE="$rate" -e DURATION=3m \
  --summary-export "$out/k6-summary.json" "$dir/keepalive.js" > "$out/k6.txt" 2>&1 || echo "k6 exit $?" >> "$out/k6.txt"
wait
docker cp ott-e2e-nginx:/var/log/nginx/ka.log "$out/nginx-access.log"
k6 version | head -1 > "$out/k6-version"
