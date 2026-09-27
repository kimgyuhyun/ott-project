package com.ottproject.ottbackend.service;

/**
 * 결제 대사 한 건의 결과
 *
 * - SETTLED: 결제사 상태대로 확정·실패·취소로 정리했다.
 * - UNSETTLED: 정리할 것이 없거나 미결인 채가 정상이다. 다른 확정 경로가 먼저 끝냈거나, 결제창 이탈로
 *   결제사에 기록이 없거나, 결제사가 ready 라고 답한 경우다.
 * - INCONCLUSIVE: 결론을 낼 수 없다. 결제사 조회 실패, 상태 판독 불가, 금액 불일치. 사람이 봐야 하므로
 *   대사 배치가 경보용 카운터로 올린다(ARCHITECTURE 5절).
 *
 * boolean 이던 때는 UNSETTLED 와 INCONCLUSIVE 가 같은 false 라서, 경보가 판정 불가만 골라 셀 수 없었다.
 */
public enum ReconcileOutcome {
    SETTLED,
    UNSETTLED,
    INCONCLUSIVE
}
