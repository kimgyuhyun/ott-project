# Git hooks

버전 관리되는 Git 훅. 저장소를 새로 클론하면 아래 명령으로 1회 활성화한다:

```
git config core.hooksPath scripts/git-hooks
```

## pre-commit

`.env`(평문, git 무시)를 커밋 시점에 감지해 `.env.enc`(SOPS 암호화, 커밋됨)로 자동
재암호화·스테이징한다. `.env`가 실제로 바뀐 경우에만 동작하도록 `.env` 해시를
`.git/.env.sha256`(로컬 전용)에 기록해 비교한다.

- 암호화는 `.sops.yaml`의 age 공개키만 사용 → age 비밀키 없이 동작
- `sops` 미설치 시 경고만 하고 커밋은 막지 않음
- `.env` 부재(클론 직후/CI 등) 시 아무것도 하지 않음
