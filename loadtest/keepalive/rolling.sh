#!/usr/bin/env bash
# [측정 전용] 순차 교체 중 실패 수. 사용: rolling.sh <이름>
# 30 r/s 지속 부하(워밍업 30s + 150s) 중에 deploy-rolling.ps1 과 같은 순서로 두 백엔드를 교체한다:
#   up -d --force-recreate --no-deps <svc> → nginx 안에서 health UP 대기 → 다음 svc
# HEALTHY_GRACE=<초>: health UP 후 다음 svc 로 넘어가기 전 대기(fail_timeout 경합 보완책 시험용).
# DRAIN=1: deploy-rolling.ps1 과 같은 함수(deploy-drain.ps1 의 Set-UpstreamDrain)로 교체 전에
#          nginx 에서 빼고 healthy 후 되돌린다. 대상 설정은 nginx 가 마운트한 ${KA_NGINX}.conf.
set -e
export MSYS_NO_PATHCONV=1
: "${APP_IMAGE:?}" "${FRONT_IMAGE:?}"
name=$1; dir="$(dirname "$0")"; out="$dir/results/$name"; mkdir -p "$out"
root="$dir/../.."
F="-p ott-ka -f $root/docker-compose.e2e.yml -f $dir/docker-compose.keepalive.yml"
# PowerShell 에 넘길 Windows 경로(C:/...). 슬래시 그대로 받는다.
root_win=$(cd "$root" && pwd -W)
conf_win="$root_win/loadtest/keepalive/${KA_NGINX:-before}.conf"
wait_healthy() {
  for i in $(seq 1 60); do
    if docker exec ott-e2e-nginx wget -qO- -T3 "http://$1:8090/actuator/health" 2>/dev/null | grep -q UP; then
      echo "$(date +%s) $1 healthy" >> "$out/timeline"; return 0; fi
    sleep 2
  done
  echo "$1 not healthy" >> "$out/timeline"; return 1
}
drain() {
  powershell -NoProfile -ExecutionPolicy Bypass -Command \
    "\$ErrorActionPreference='Stop'; . '$root_win\deploy-drain.ps1'; Set-UpstreamDrain -ConfPath '$conf_win' -NginxContainer ott-e2e-nginx -Drain '$1'" \
    >> "$out/drain.log" 2>&1
  echo "$(date +%s) drain='$1' done" >> "$out/timeline"
}
docker exec ott-e2e-nginx sh -c ': > /var/log/nginx/ka.log'
date +%s > "$out/start_epoch"
k6 run -q -e LT_PASSWORD="$LT_PASSWORD" -e RATE=30 -e DURATION=150s \
  --summary-export "$out/k6-summary.json" "$dir/keepalive.js" > "$out/k6.txt" 2>&1 &
k6pid=$!
# drain 이 실패해 스크립트가 중간에 끝나도 부하가 남아 돌지 않게 한다.
trap "kill $k6pid 2>/dev/null || true" EXIT
sleep 40
for svc in app app2; do
  host=$([ $svc = app ] && echo ott-app || echo ott-app-2)
  if [ "${DRAIN:-0}" = 1 ]; then drain "$host"; fi
  echo "$(date +%s) recreate $svc" >> "$out/timeline"
  docker compose $F up -d --force-recreate --no-deps $svc >> "$out/compose.log" 2>&1
  wait_healthy $host
  sleep "${HEALTHY_GRACE:-0}"
done
# deploy-rolling.ps1 과 같이 다음 인스턴스의 drain 이 앞 인스턴스 복구를 겸하고, 마지막에 한 번만 되돌린다.
if [ "${DRAIN:-0}" = 1 ]; then drain ""; fi
wait $k6pid || echo "k6 exit $?" >> "$out/k6.txt"
docker cp ott-e2e-nginx:/var/log/nginx/ka.log "$out/nginx-access.log"
