package com.ottproject.ottbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.ottproject.ottbackend.dto.PaymentMethodRegisterRequestDto;
import com.ottproject.ottbackend.dto.PaymentMethodUpdateRequestDto;
import com.ottproject.ottbackend.entity.PaymentMethod;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.PaymentMethodType;
import com.ottproject.ottbackend.enums.PaymentProvider;
import com.ottproject.ottbackend.repository.PaymentMethodRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * PaymentMethodService 단위 테스트
 *
 * 지키려는 규칙(결제수단 정책)
 * - 기본 수단은 사용자당 하나다. 이 규칙은 여러 행에 걸쳐 있어 엔티티가 아니라 이 서비스가 지킨다.
 * - 기본 수단을 지우면 남은 것 중 하나가 기본이 된다. 아무도 기본이 아닌 상태로 두면
 *   정기결제 폴백이 집을 수단을 못 찾는다.
 * - 부분 수정은 패치에 온 필드만 바꾼다. null 은 "변경 없음"이지 "null 로 설정"이 아니다.
 * - 남의 결제수단은 조회 단계에서 404 로 끊는다(findByIdAndUser_Id).
 *
 * 조회 메서드는 전부 파생 쿼리라 프레임워크가 보장한다(15절). 여기서는 목으로 대신하고
 * 서비스가 그 결과를 어떻게 조합하는지만 본다. list/toDto 는 매핑 전용이라 대상이 아니다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentMethodServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @InjectMocks
    private PaymentMethodService service;

    /** PK 가 달린 결제수단. PK 는 영속화가 채우는 값이라 테스트에서만 주입한다. */
    private PaymentMethod method(long id, boolean isDefault, int priority) {
        PaymentMethod pm = PaymentMethod.createPaymentMethod(
                User.reference(USER_ID), PaymentProvider.IMPORT, PaymentMethodType.CARD, "billing_key_" + id);
        ReflectionTestUtils.setField(pm, "id", id);
        pm.describeCard("VISA", "4242", 12, 2030);
        pm.applyListingOptions(isDefault, priority, "내 카드");
        return pm;
    }

    private PaymentMethodRegisterRequestDto registerReq() {
        PaymentMethodRegisterRequestDto dto = new PaymentMethodRegisterRequestDto();
        dto.type = PaymentMethodType.KAKAO_PAY;
        dto.providerMethodId = "billing_key_new";
        dto.brand = "KAKAO";
        dto.last4 = "1234";
        dto.expiryMonth = 3;
        dto.expiryYear = 2029;
        dto.isDefault = true;
        dto.priority = 1;
        dto.label = "카카오페이";
        return dto;
    }

    // ===== 등록 =====

    @Test
    @DisplayName("등록 - 요청의 표기 정보와 노출 정책이 저장되는 엔티티에 전부 실린다")
    void registerCarriesEveryRequestedField() {
        service.register(USER_ID, registerReq());

        ArgumentCaptor<PaymentMethod> saved = ArgumentCaptor.forClass(PaymentMethod.class);
        verify(paymentMethodRepository).save(saved.capture());
        PaymentMethod pm = saved.getValue();

        assertThat(pm.getUser().getId()).isEqualTo(USER_ID);
        assertThat(pm.getType()).isEqualTo(PaymentMethodType.KAKAO_PAY);
        assertThat(pm.getProviderMethodId()).isEqualTo("billing_key_new");
        assertThat(pm.getBrand()).isEqualTo("KAKAO");
        assertThat(pm.getLast4()).isEqualTo("1234");
        assertThat(pm.getExpiryMonth()).isEqualTo(3);
        assertThat(pm.getExpiryYear()).isEqualTo(2029);
        assertThat(pm.isDefault()).isTrue();
        assertThat(pm.getPriority()).isEqualTo(1);
        assertThat(pm.getLabel()).isEqualTo("카카오페이");
    }

    @Test
    @DisplayName("등록 - provider 는 요청값이 아니라 IMPORT 로 고정된다")
    void registerForcesImportProvider() {
        PaymentMethodRegisterRequestDto dto = registerReq();
        dto.provider = PaymentProvider.STRIPE; // 요청이 다른 값을 보내와도

        service.register(USER_ID, dto);

        ArgumentCaptor<PaymentMethod> saved = ArgumentCaptor.forClass(PaymentMethod.class);
        verify(paymentMethodRepository).save(saved.capture());
        assertThat(saved.getValue().getProvider()).isEqualTo(PaymentProvider.IMPORT);
    }

    // ===== 기본 수단 지정 =====

    @Test
    @DisplayName("기본 지정 - 대상만 기본이 되고 나머지는 전부 해제된다(사용자당 기본은 하나)")
    void setDefaultLeavesExactlyOneDefault() {
        PaymentMethod target = method(2L, false, 100);
        PaymentMethod previousDefault = method(1L, true, 100);
        PaymentMethod other = method(3L, false, 100);
        given(paymentMethodRepository.findByIdAndUser_Id(2L, USER_ID)).willReturn(Optional.of(target));
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(USER_ID))
                .willReturn(List.of(previousDefault, target, other));

        service.setDefault(USER_ID, 2L);

        assertThat(target.isDefault()).isTrue();
        // 핵심: 이전 기본을 내리지 않으면 기본 수단이 둘이 되고 폴백이 어느 쪽을 쓸지 알 수 없다
        assertThat(previousDefault.isDefault()).isFalse();
        assertThat(other.isDefault()).isFalse();
    }

    @Test
    @DisplayName("기본 지정 - 남의 결제수단이면 404, 아무것도 건드리지 않는다")
    void setDefaultOnForeignMethodIsRejected() {
        given(paymentMethodRepository.findByIdAndUser_Id(99L, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.setDefault(USER_ID, 99L)).isInstanceOf(ResponseStatusException.class);

        // 소유 확인이 목록 조회보다 먼저다 - 남의 id 로 내 목록이 훑어지지 않아야 한다
        verify(paymentMethodRepository).findByIdAndUser_Id(99L, USER_ID);
        verifyNoMoreInteractions(paymentMethodRepository);
    }

    // ===== 소프트 삭제 =====

    @Test
    @DisplayName("삭제 - 기본 수단을 지우면 남은 것 중 첫 번째가 기본이 된다")
    void deletingDefaultPromotesTheNextMethod() {
        PaymentMethod target = method(1L, true, 100);
        PaymentMethod survivor = method(2L, false, 50);
        given(paymentMethodRepository.findByIdAndUser_Id(1L, USER_ID)).willReturn(Optional.of(target));
        // 승격 후보는 삭제 전에 읽으므로 대상도 아직 목록에 있다(기본이라 맨 앞)
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(USER_ID))
                .willReturn(List.of(target, survivor));

        service.delete(USER_ID, 1L);

        assertThat(target.getDeletedAt()).isNotNull();
        assertThat(target.isDefault()).isFalse(); // 지워진 행에 기본 플래그가 남으면 기본이 둘이 된다
        // 핵심: 아무도 기본이 아니면 사용자에게 기본 수단이 없는 상태가 된다
        assertThat(survivor.isDefault()).isTrue();
    }

    @Test
    @DisplayName("삭제 - 지우는 대상 자신은 승격 후보에서 제외된다(flush 순서에 기대지 않는다)")
    void deletedMethodIsNeverPromotedBackToDefault() {
        PaymentMethod target = method(1L, true, 1); // priority 가 가장 낮아 정렬상 계속 맨 앞이다
        PaymentMethod survivor = method(2L, false, 100);
        given(paymentMethodRepository.findByIdAndUser_Id(1L, USER_ID)).willReturn(Optional.of(target));
        // 삭제 전 목록이라 대상이 들어 있다. 자기 자신을 걸러내지 않으면 방금 지운 행을 다시 기본으로 올린다
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(USER_ID))
                .willReturn(List.of(target, survivor));

        service.delete(USER_ID, 1L);

        assertThat(target.isDefault()).isFalse();
        assertThat(survivor.isDefault()).isTrue();
    }

    @Test
    @DisplayName("삭제 - 기본이 아닌 수단을 지우면 기본 재지정을 하지 않는다")
    void deletingNonDefaultLeavesTheDefaultAlone() {
        PaymentMethod target = method(2L, false, 100);
        given(paymentMethodRepository.findByIdAndUser_Id(2L, USER_ID)).willReturn(Optional.of(target));

        service.delete(USER_ID, 2L);

        assertThat(target.getDeletedAt()).isNotNull();
        // 재지정 경로로 들어가면 이미 기본인 수단을 다시 승격시키거나 엉뚱한 것을 올린다
        verify(paymentMethodRepository).findByIdAndUser_Id(2L, USER_ID);
        verifyNoMoreInteractions(paymentMethodRepository);
    }

    @Test
    @DisplayName("삭제 - 마지막 하나뿐인 기본 수단을 지워도 터지지 않는다")
    void deletingTheOnlyMethodDoesNotCrash() {
        PaymentMethod target = method(1L, true, 100);
        given(paymentMethodRepository.findByIdAndUser_Id(1L, USER_ID)).willReturn(Optional.of(target));
        // 삭제 전 목록에 자기 자신뿐이므로 걸러내고 나면 승격 후보가 없다
        given(paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(USER_ID))
                .willReturn(List.of(target));

        service.delete(USER_ID, 1L);

        assertThat(target.getDeletedAt()).isNotNull();
        assertThat(target.isDefault()).isFalse();
    }

    @Test
    @DisplayName("삭제 - 남의 결제수단이면 404")
    void deletingForeignMethodIsRejected() {
        given(paymentMethodRepository.findByIdAndUser_Id(99L, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, 99L)).isInstanceOf(ResponseStatusException.class);
    }

    // ===== 부분 수정 =====

    @Test
    @DisplayName("부분 수정 - 패치에 온 필드만 바꾸고 나머지는 그대로 둔다")
    void updatePartialOnlyTouchesSuppliedFields() {
        PaymentMethod pm = method(1L, true, 100);
        given(paymentMethodRepository.findByIdAndUser_Id(1L, USER_ID)).willReturn(Optional.of(pm));
        PaymentMethodUpdateRequestDto patch = new PaymentMethodUpdateRequestDto();
        patch.label = "새 별칭"; // 나머지는 null = 변경 없음

        service.updatePartial(USER_ID, 1L, patch);

        assertThat(pm.getLabel()).isEqualTo("새 별칭");
        assertThat(pm.getPriority()).isEqualTo(100);
        assertThat(pm.getExpiryMonth()).isEqualTo(12);
        assertThat(pm.getExpiryYear()).isEqualTo(2030);
    }

    @Test
    @DisplayName("부분 수정 - 만료 월만 오면 연도는 기존 값이 유지된다(반쪽 만료일 금지)")
    void updatePartialKeepsTheOtherHalfOfExpiry() {
        PaymentMethod pm = method(1L, true, 100); // 12/2030
        given(paymentMethodRepository.findByIdAndUser_Id(1L, USER_ID)).willReturn(Optional.of(pm));
        PaymentMethodUpdateRequestDto patch = new PaymentMethodUpdateRequestDto();
        patch.expiryMonth = 6;

        service.updatePartial(USER_ID, 1L, patch);

        assertThat(pm.getExpiryMonth()).isEqualTo(6);
        // 만료일은 엔티티에서 한 벌로 바뀐다. 연도를 안 넘기면 null 이 되어 만료일이 사라진다
        assertThat(pm.getExpiryYear()).isEqualTo(2030);
    }

    @Test
    @DisplayName("부분 수정 - 만료 연도만 와도 월이 유지된다")
    void updatePartialKeepsMonthWhenOnlyYearIsSupplied() {
        PaymentMethod pm = method(1L, true, 100); // 12/2030
        given(paymentMethodRepository.findByIdAndUser_Id(1L, USER_ID)).willReturn(Optional.of(pm));
        PaymentMethodUpdateRequestDto patch = new PaymentMethodUpdateRequestDto();
        patch.expiryYear = 2031;

        service.updatePartial(USER_ID, 1L, patch);

        assertThat(pm.getExpiryMonth()).isEqualTo(12);
        assertThat(pm.getExpiryYear()).isEqualTo(2031);
    }

    @Test
    @DisplayName("부분 수정 - 남의 결제수단이면 404")
    void updatingForeignMethodIsRejected() {
        given(paymentMethodRepository.findByIdAndUser_Id(99L, USER_ID)).willReturn(Optional.empty());
        PaymentMethodUpdateRequestDto patch = new PaymentMethodUpdateRequestDto();
        patch.label = "새 별칭";

        assertThatThrownBy(() -> service.updatePartial(USER_ID, 99L, patch))
                .isInstanceOf(ResponseStatusException.class);
    }
}
