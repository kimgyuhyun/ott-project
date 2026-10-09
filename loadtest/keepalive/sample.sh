#!/usr/bin/env bash
# [측정 전용] 소켓 상태와 CPU 를 표본으로 남긴다. 사용: sample.sh <출력디렉터리> <초>
# 8090(0x1F9A) 이 로컬 포트면 앱 쪽 소켓, 원격 포트면 nginx 쪽 소켓이다.
# /proc/net/tcp 의 st: 01=ESTABLISHED, 06=TIME_WAIT
out=$1; secs=$2; mkdir -p "$out"
count() { # $1=컨테이너 $2=local|remote
  docker exec "$1" sh -c 'cat /proc/net/tcp /proc/net/tcp6 2>/dev/null' | awk -v side="$2" '
    NR>1 && $1 ~ /:$/ { split($2,l,":"); split($3,r,":");
      p = (side=="local") ? l[2] : r[2];
      if (p=="1F9A") { if ($4=="01") e++; else if ($4=="06") t++; } }
    END { printf "%d,%d", e+0, t+0 }'
}
echo "epoch,nginx_estab,nginx_tw,app1_estab,app1_tw,app2_estab,app2_tw" > "$out/sockets.csv"
( end=$((SECONDS+secs)); while [ $SECONDS -lt $end ]; do
    docker stats --no-stream --format '{{.Name}},{{.CPUPerc}}' ott-e2e-nginx ott-e2e-app ott-ka-app2 \
      | sed "s/^/$(date +%s),/" >> "$out/cpu.csv"
  done ) &
end=$((SECONDS+secs))
while [ $SECONDS -lt $end ]; do
  echo "$(date +%s),$(count ott-e2e-nginx remote),$(count ott-ka-probe1 local),$(count ott-ka-probe2 local)" >> "$out/sockets.csv"
  sleep 1
done
wait
