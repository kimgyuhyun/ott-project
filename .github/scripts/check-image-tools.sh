#!/usr/bin/env bash
# .github/image-tools.txt 에 적은 명령이 각 이미지 안에 실제로 있는지 확인한다.
# 운영 스크립트는 docker exec 로 남의 이미지 안의 명령(cat, wget, curl ...)을 부른다. 이미지를 올리는
# PR 이 그 명령을 없애도 지금까지는 CI 가 몰랐고, 운영 배포가 실패하고서야 드러났다.
#
#   check-image-tools.sh                  lint + compose 에 태그로 고정된 외부 이미지 전부
#   check-image-tools.sh app=IMAGE ...    CI 가 방금 빌드한 자체 이미지(app, frontend)만
#
# 명령을 실행해 보지 않는다. 시작하지 않은 컨테이너를 만들고 PATH 의 각 디렉터리에서 docker cp 로
# 파일을 꺼내 본다 — 셸이 없는 distroless 이미지에서도 같은 방식으로 판정된다.
set -euo pipefail
cd "$(dirname "$0")/../.."

contract=.github/image-tools.txt
# deploy-rolling.ps1 의 $ComposeFiles 와 같은 조합이다.
compose=(-f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.netlock.yml
         -f docker-compose.ha.yml -f docker-compose.monitoring.yml)
default_path=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

mapfile -t rules < <(sed -e 's/\r$//' -e 's/#.*//' "$contract" | awk 'NF == 2 { print $1, $2 }')
fail=0

declare -A image=()
if [ $# -gt 0 ]; then
  for kv in "$@"; do image[${kv%%=*}]=${kv#*=}; done
else
  # lint: 스크립트가 docker exec 로 부르는 명령이 계약에 빠짐없이 있는가.
  # 새 docker exec 를 쓰고 계약에 안 적으면 아래 이미지 검사도 그 명령을 모르고 지나간다.
  used=$(grep -hv '^[[:space:]]*#' deploy.ps1 deploy-rolling.ps1 deploy-drain.ps1 .github/workflows/cd.yml security/*.ps1 \
    | grep -oE 'docker exec( -i)? [^ ]+ [A-Za-z0-9_.-]+' | awk '{ print $NF }' | sort -u)
  for cmd in $used; do
    if ! printf '%s\n' "${rules[@]}" | awk '{ print $2 }' | grep -qx "$cmd"; then
      echo "LINT  '$cmd' 를 docker exec 로 쓰는데 $contract 에 없다"
      fail=1
    fi
  done

  # 운영에 .env 가 없어도 이미지 이름은 읽을 수 있다(--no-interpolate). 변수로 된 것(자체 이미지)은
  # 빌드한 잡이 따로 검사하므로 여기서는 이름만 기억한다.
  # 변수에 먼저 받는다 — 프로세스 치환으로 바로 읽으면 compose 가 실패해도 set -e 가 못 잡는다.
  services=$(docker compose "${compose[@]}" config --no-interpolate --format json \
               | jq -r '.services | to_entries[] | "\(.key) \(.value.image)"')
  declare -A known=()
  while read -r svc img; do
    known[$svc]=1
    case $img in *\$\{*) continue ;; esac
    image[$svc]=$img
  done <<< "$services"
  for svc in $(printf '%s\n' "${rules[@]}" | awk '{ print $1 }' | sort -u); do
    if [ -z "${known[$svc]:-}" ]; then
      echo "LINT  $contract 의 서비스 '$svc' 가 운영 compose 조합에 없다"
      fail=1
    fi
  done
fi

declare -A cid=() path=()
for rule in "${rules[@]}"; do
  svc=${rule%% *}; cmd=${rule#* }
  img=${image[$svc]:-}
  [ -n "$img" ] || continue
  if [ -z "${cid[$svc]:-}" ]; then
    docker image inspect "$img" >/dev/null 2>&1 || docker pull -q "$img" >/dev/null
    cid[$svc]=$(docker create "$img")
    p=$(docker image inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$img" | sed -n 's/^PATH=//p')
    path[$svc]=${p:-$default_path}
  fi
  found=
  IFS=: read -ra dirs <<< "${path[$svc]}"
  for d in "${dirs[@]}"; do
    if docker cp "${cid[$svc]}:$d/$cmd" - >/dev/null 2>&1; then found="$d/$cmd"; break; fi
  done
  if [ -n "$found" ]; then
    echo "OK    $svc  $cmd  ($found)"
  else
    echo "MISS  $svc  $cmd  — $img 의 PATH(${path[$svc]})에 없다"
    fail=1
  fi
done

for c in "${cid[@]}"; do docker rm "$c" >/dev/null; done
exit "$fail"
