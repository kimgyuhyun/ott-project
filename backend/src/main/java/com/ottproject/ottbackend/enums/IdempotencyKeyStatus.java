package com.ottproject.ottbackend.enums;

/**
 * 멱등키 선점 상태
 *
 * 큰 흐름
 * - CLAIMED: 외부 호출 전에 선점만 된 상태. 그 호출이 실제로 나갔는지 아직 모른다.
 * - CONFIRMED: 작업이 확정된 상태. 대사 배치가 건드리지 않는다.
 *
 * "해제"에 해당하는 상태값은 두지 않는다 — 해제는 행 DELETE 다.
 * key_value 유니크 제약과 선점 빠른 경로가 상태를 보지 않으므로, 행이 남아 있으면 재시도가 그대로 막힌다.
 */
public enum IdempotencyKeyStatus {
    CLAIMED, // 선점만 됨(외부 호출 결과 미확정)
    CONFIRMED // 확정됨
}
