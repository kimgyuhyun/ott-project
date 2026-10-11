#!/usr/bin/env bash
# [측정 전용] run.sh 결과 요약. 측정 구간(시작+40s ~ 시작+210s)만 본다.
out=$1; s=$(cat "$out/start_epoch"); a=$((s+40)); b=$((s+210))
echo "== $out"
awk -v a=$a -v b=$b '$1>=a && $1<=b && $4 !~ /login/ {
    n++; if ($2!=200) bad++;
    split($6,c,/, */); if (c[1]=="0.000") reuse++; ct+=c[1];
    rt[n]=$5*1000; split($7,u,/, */); ut[n]=u[1]*1000 }
  END { asort(rt); asort(ut);
    printf "requests=%d non200=%d  connect_time==0: %.1f%%  avg_connect=%.3fms\n", n, bad, 100*reuse/n, 1000*ct/n;
    printf "nginx request_time   p50=%.1f p95=%.1f p99=%.1f ms\n", rt[int(n*.5)], rt[int(n*.95)], rt[int(n*.99)];
    printf "upstream_response    p50=%.1f p95=%.1f p99=%.1f ms\n", ut[int(n*.5)], ut[int(n*.95)], ut[int(n*.99)] }' "$out/nginx-access.log"
awk -F, -v a=$a -v b=$b 'NR>1 && $1>=a && $1<=b { n++; for(i=2;i<=7;i++) s[i]+=$i; if($3>m3)m3=$3; if($5+$7>mt)mt=$5+$7 }
  END { printf "sockets avg: nginx estab=%.0f tw=%.0f(max %d) | app estab=%.0f tw=%.0f (max %d)\n",
        s[2]/n, s[3]/n, m3, (s[4]+s[6])/n, (s[5]+s[7])/n, mt }' "$out/sockets.csv"
awk -F, -v a=$a -v b=$b '$1>=a && $1<=b { gsub("%","",$3); s[$2]+=$3; c[$2]++ }
  END { for (k in s) printf "cpu avg %s=%.1f%%  ", k, s[k]/c[k]; print "" }' "$out/cpu.csv"
grep -E "http_req_duration\{phase:measure\}|http_req_failed\{phase:measure\}|p\(95\)=|rate=" "$out/k6.txt" | head -4
