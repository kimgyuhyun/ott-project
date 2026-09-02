#!/usr/bin/env bash
# push 직후 그 이미지의 레지스트리 digest 를 파일 한 줄("<커밋SHA> <레포@sha256:...>")로 남긴다.
# CD 는 이 값으로 pull 하므로, CI 가 스캔을 통과시킨 그 이미지가 그대로 배포된다.
# 태그를 넘기면 CD 가 pull 시점에 태그를 다시 해석하게 되어, CI 종료와 CD 의 pull 사이에
# 같은 태그가 덮어써지면 CD 는 그 이미지를 그대로 "고정"해서 배포한다.
#
# RepoDigests 를 인덱스 0 으로 꺼내지 않는다. 같은 이미지 ID 가 여러 레포 이름으로 로컬에
# 있으면 0 번이 우리 레포라는 보장이 없고, 항목 순서도 정해져 있지 않다. 레포 이름으로
# 걸러 정확히 그 항목만 고른다.
set -euo pipefail

: "${IMAGE_REPO:?IMAGE_REPO required}"
: "${IMAGE_TAG:?IMAGE_TAG required}"
: "${COMMIT_SHA:?COMMIT_SHA required}"
: "${OUT_FILE:?OUT_FILE required}"

ref=$(docker inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' \
        "${IMAGE_REPO}:${IMAGE_TAG}" | grep -m1 "^${IMAGE_REPO}@sha256:" || true)

if [ -z "$ref" ]; then
  echo "no registry digest for ${IMAGE_REPO}:${IMAGE_TAG} - the push step must run first" >&2
  exit 1
fi

# 형식을 여기서 한 번 확인한다. CD 가 이 문자열을 그대로 docker pull 에 넘긴다.
if ! printf '%s' "$ref" | grep -qE "^${IMAGE_REPO}@sha256:[0-9a-f]{64}$"; then
  echo "unexpected digest ref: $ref" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUT_FILE")"
printf '%s %s\n' "$COMMIT_SHA" "$ref" > "$OUT_FILE"
echo "captured: $ref"

# 같은 잡 안의 뒤 스텝(e2e 등)이 쓸 수 있게 잡 output 으로도 내보낸다.
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "ref=$ref" >> "$GITHUB_OUTPUT"
fi
