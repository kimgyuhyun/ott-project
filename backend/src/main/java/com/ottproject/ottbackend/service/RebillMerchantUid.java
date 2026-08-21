package com.ottproject.ottbackend.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * RebillMerchantUid
 *
 * 큰 흐름
 * - 정기결제 재청구의 merchant_uid 를 결정적으로 만든다. 같은 청구 시도는 언제 계산해도 같은 값이 나온다.
 *   그래서 MQ 중복 배달 두 건이 같은 값으로 PENDING 삽입을 시도하고, payments 의 유니크 제약이 한 건을 떨어뜨린다.
 *   (이전에는 게이트웨이가 호출할 때마다 timestamp 로 새로 만들어서, 중복 배달을 구분할 방법이 아예 없었다.)
 *
 * 형식: rebill_{구독ID}_{주기앵커 yyyyMMdd}_{시도횟수}_{결제수단ID}
 *
 * 구성 요소를 이렇게 고른 이유
 * - 주기 앵커는 endAt 이다. nextBillingAt 은 청구가 실패할 때마다 갱신돼(+3일/+1일) 주기 식별자로 못 쓴다.
 *   endAt 은 청구가 성공했을 때만 움직이므로 청구주기와 1:1로 대응한다.
 * - 결제수단ID 가 들어가는 이유: 아임포트는 실패한 시도에도 merchant_uid 를 소진시킨다. 기본 수단이
 *   거절된 뒤 보조 수단을 같은 값으로 부르면 "이미 존재하는 merchant_uid" 로 거절돼 폴백이 통째로 죽는다.
 * - 시도횟수(retryCount)가 들어가므로 재시도는 새 값이 되고, 같은 시도의 중복 배달만 같은 값이 된다.
 *
 * 길이: 아임포트 merchant_uid 는 최대 40자. 구독ID·결제수단ID 가 각각 9자리여도 38자를 넘지 않는다.
 */
public final class RebillMerchantUid {

    private static final String PREFIX = "rebill_"; // 대사 경로가 재청구 결제를 식별하는 접두어
    private static final DateTimeFormatter ANCHOR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private RebillMerchantUid() { // 유틸리티 클래스
    }

    /**
     * 재청구 merchant_uid 생성
     *
     * @param subscriptionId 구독 ID
     * @param cycleAnchor    청구주기 앵커(구독의 endAt). null 이면 주기 구분이 불가능하므로 거부한다
     * @param attempt        시도 횟수(구독의 retryCount)
     * @param paymentMethodId 이번 시도에 쓸 결제수단 ID
     */
    public static String create(Long subscriptionId, LocalDateTime cycleAnchor, int attempt, Long paymentMethodId) {
        if (subscriptionId == null || paymentMethodId == null) {
            throw new IllegalArgumentException("구독 ID와 결제수단 ID는 필수입니다.");
        }
        if (cycleAnchor == null) {
            // 앵커가 없으면 서로 다른 주기가 같은 값을 갖게 되어, 다음 주기 청구가 중복으로 차단된다.
            throw new IllegalArgumentException("청구주기 앵커는 필수입니다.");
        }
        return PREFIX + subscriptionId
                + "_" + cycleAnchor.format(ANCHOR_FORMAT)
                + "_" + attempt
                + "_" + paymentMethodId;
    }

    /**
     * 재청구 경로가 만든 merchant_uid 인지 판별
     * - 대사 배치가 체크아웃 전제의 확정 로직으로 보내면 안 되는 결제를 골라내는 데 쓴다.
     */
    public static boolean isRebill(String merchantUid) {
        return merchantUid != null && merchantUid.startsWith(PREFIX);
    }

    /**
     * merchant_uid 에서 구독 ID 추출
     * - 대사가 확정한 재청구 결제를 어느 구독에 반영할지 찾는 용도.
     *   payments 에는 구독 FK 가 없어서 uid 가 유일한 연결고리다.
     * @return 구독 ID, 형식이 맞지 않으면 null
     */
    public static Long subscriptionIdOf(String merchantUid) {
        if (!isRebill(merchantUid)) {
            return null;
        }
        String[] parts = merchantUid.split("_");
        if (parts.length != 5) {
            return null; // rebill_{sub}_{anchor}_{attempt}_{method}
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
