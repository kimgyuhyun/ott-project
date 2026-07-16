package com.ottproject.ottbackend.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MembershipPlan 정적 팩토리/비즈니스 메서드 검증
 *
 * 왜 이 테스트가 필요한가
 * - code 는 플랜명에서 파생되는데(공백→_, 대문자) unique 제약이 걸린 조회 키다.
 *   생성 규칙이 바뀌면 findByCode(=구독 신청 경로)가 조용히 실패한다.
 * - 가격/기간 거부 분기와 activate/deactivate 중복 호출 방어는 서비스 테스트가 밟지 않는다.
 */
class MembershipPlanFactoryTest {

    private static final Money PRICE = new Money(9900L, "KRW");

    @Nested
    @DisplayName("코드 생성 규칙")
    class CodeGeneration {

        @Test
        @DisplayName("플랜명을 대문자로 바꾸고 공백을 밑줄로 치환해 코드를 만든다")
        void generatesCodeFromName() {
            MembershipPlan plan = MembershipPlan.createBasicPlan("basic plan", "설명", PRICE, 1);

            assertThat(plan.getCode()).isEqualTo("BASIC_PLAN");
        }

        @Test
        @DisplayName("연속 공백도 밑줄 하나로 합친다")
        void collapsesConsecutiveWhitespace() {
            MembershipPlan plan = MembershipPlan.createBasicPlan("  premium   yearly  ", "설명", PRICE, 12);

            assertThat(plan.getCode()).isEqualTo("PREMIUM_YEARLY");
        }

        @Test
        @DisplayName("플랜명을 바꾸면 코드도 함께 갱신된다")
        void updateNameRegeneratesCode() {
            MembershipPlan plan = MembershipPlan.createBasicPlan("old name", "설명", PRICE, 1);

            plan.updateName("new name");

            assertThat(plan.getName()).isEqualTo("new name");
            assertThat(plan.getCode()).isEqualTo("NEW_NAME");
        }
    }

    @Nested
    @DisplayName("createBasicPlan / createPremiumPlan")
    class Creation {

        @Test
        @DisplayName("기본 플랜은 720p 단일 스트림으로 생성된다")
        void basicPlanDefaults() {
            MembershipPlan plan = MembershipPlan.createBasicPlan("Basic", "설명", PRICE, 1);

            assertThat(plan.getMaxQuality()).isEqualTo("720p");
            assertThat(plan.getConcurrentStreams()).isEqualTo(1);
            assertThat(plan.getPeriodMonths()).isEqualTo(1);
            assertThat(plan.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("프리미엄 플랜은 1080p 동시접속 3 으로 생성된다")
        void premiumPlanDefaults() {
            MembershipPlan plan = MembershipPlan.createPremiumPlan("Premium", "설명", PRICE, 12, "4K");

            assertThat(plan.getMaxQuality()).isEqualTo("1080p");
            assertThat(plan.getConcurrentStreams()).isEqualTo(3);
            assertThat(plan.getPeriodMonths()).isEqualTo(12);
        }

        @Test
        @DisplayName("무료 체험 플랜은 0원 1개월로 생성된다")
        void trialPlanIsFree() {
            MembershipPlan plan = MembershipPlan.createTrialPlan("Trial", "설명", 7);

            assertThat(plan.getPrice().getAmount()).isZero();
            assertThat(plan.getPrice().getCurrency()).isEqualTo("KRW");
            assertThat(plan.getPeriodMonths()).isEqualTo(1);
        }

        @Test
        @DisplayName("가격이 없으면 거부한다")
        void rejectsNullPrice() {
            assertThatThrownBy(() -> MembershipPlan.createBasicPlan("Basic", "설명", null, 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("기간이 1개월 미만이면 거부한다 - 0개월 플랜은 청구 주기를 계산할 수 없다")
        void rejectsNonPositivePeriod() {
            assertThatThrownBy(() -> MembershipPlan.createBasicPlan("Basic", "설명", PRICE, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MembershipPlan.createBasicPlan("Basic", "설명", PRICE, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("플랜명과 설명이 비어 있으면 거부한다")
        void rejectsBlankNameOrDescription() {
            assertThatThrownBy(() -> MembershipPlan.createBasicPlan("  ", "설명", PRICE, 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MembershipPlan.createBasicPlan("Basic", "  ", PRICE, 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("체험 기간이 1일 미만이면 거부한다")
        void rejectsNonPositiveTrialDays() {
            assertThatThrownBy(() -> MembershipPlan.createTrialPlan("Trial", "설명", 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("activate / deactivate")
    class ActivationState {

        @Test
        @DisplayName("활성 플랜을 비활성화하면 비활성 상태가 된다")
        void deactivateTurnsPlanOff() {
            MembershipPlan plan = MembershipPlan.createBasicPlan("Basic", "설명", PRICE, 1);

            plan.deactivate();

            assertThat(plan.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("이미 비활성인 플랜을 또 비활성화하면 거부한다")
        void rejectsDoubleDeactivate() {
            MembershipPlan plan = MembershipPlan.createBasicPlan("Basic", "설명", PRICE, 1);
            plan.deactivate();

            assertThatThrownBy(plan::deactivate).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("이미 활성인 플랜을 또 활성화하면 거부한다")
        void rejectsDoubleActivate() {
            MembershipPlan plan = MembershipPlan.createBasicPlan("Basic", "설명", PRICE, 1);

            assertThatThrownBy(plan::activate).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("비활성 플랜은 다시 활성화할 수 있다")
        void reactivateRestoresPlan() {
            MembershipPlan plan = MembershipPlan.createBasicPlan("Basic", "설명", PRICE, 1);
            plan.deactivate();

            plan.activate();

            assertThat(plan.getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("updatePrice")
    class PriceUpdate {

        @Test
        @DisplayName("가격을 새 금액으로 교체한다")
        void updatesPrice() {
            MembershipPlan plan = MembershipPlan.createBasicPlan("Basic", "설명", PRICE, 1);

            plan.updatePrice(new Money(14900L, "KRW"));

            assertThat(plan.getPrice().getAmount()).isEqualTo(14900L);
        }

        @Test
        @DisplayName("가격을 비울 수 없다")
        void rejectsNullPrice() {
            MembershipPlan plan = MembershipPlan.createBasicPlan("Basic", "설명", PRICE, 1);

            assertThatThrownBy(() -> plan.updatePrice(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
