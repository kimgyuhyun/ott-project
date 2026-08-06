package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.PaymentMethodType;
import com.ottproject.ottbackend.enums.PaymentProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PaymentMethod 상태 전이 검증
 *
 * 여기서 고정하는 것은 "함께 바뀌어야 하는 필드가 같이 바뀌는가" 하나다.
 * 세터로 바꾸던 시절에는 호출자가 한쪽만 바꿔도 컴파일이 통과했고, 그 결과가
 * 사용자 화면에 그대로 나갔다.
 */
class PaymentMethodStateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 12, 0);

    private PaymentMethod cardMethod() {
        PaymentMethod pm = PaymentMethod.createPaymentMethod(
                User.reference(1L), PaymentProvider.IMPORT, PaymentMethodType.CARD, "billing_key_1");
        pm.describeCard("VISA", "4242", 12, 2030);
        return pm;
    }

    @Test
    @DisplayName("소프트 삭제는 삭제 시각 기록과 기본 수단 해제를 함께 한다 - 삭제된 행에 기본 플래그가 남으면 기본 수단이 둘이 된다")
    void softDeleteAlsoClearsDefaultFlag() {
        PaymentMethod pm = cardMethod();
        pm.markAsDefault();

        pm.softDelete(NOW);

        assertThat(pm.getDeletedAt()).isEqualTo(NOW);
        assertThat(pm.isDefault()).isFalse();
    }

    @Test
    @DisplayName("게이트웨이 수단 확정은 유형과 브랜드를 함께 바꾼다 - 카드에서 간편결제로 바뀌면 카드 브랜드가 남으면 안 된다")
    void gatewayMethodDetailsReplaceTypeAndBrandTogether() {
        PaymentMethod pm = cardMethod();

        pm.applyGatewayMethodDetails(PaymentProvider.IMPORT, PaymentMethodType.KAKAO_PAY, null);

        assertThat(pm.getType()).isEqualTo(PaymentMethodType.KAKAO_PAY);
        assertThat(pm.getBrand()).isNull(); // "카카오페이 VISA" 가 되지 않는다
    }
}
