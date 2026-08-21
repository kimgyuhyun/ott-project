package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.PaymentMethodRegisterRequestDto;
import com.ottproject.ottbackend.dto.PaymentMethodResponseDto;
import com.ottproject.ottbackend.dto.PaymentMethodUpdateRequestDto;
import com.ottproject.ottbackend.entity.PaymentMethod;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.PaymentMethodRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * PaymentMethodService
 *
 * 큰 흐름
 * - 저장 결제수단의 등록/목록/기본 지정/부분 수정/소프트 삭제를 처리한다.
 *
 * 메서드 개요
 * - register: 결제수단 등록
 * - list: 삭제 제외 목록(기본→우선순위 정렬)
 * - setDefault: 기본 수단 단일화
 * - updatePartial: 일부 필드 수정
 * - delete: 소프트 삭제 및 기본 수단 재지정
 */
@Service // 서비스 빈 등록
@RequiredArgsConstructor // 생성자 주입
@Transactional // 쓰기 트랜잭션 기본
public class PaymentMethodService { // 결제수단 도메인 서비스

    private final PaymentMethodRepository paymentMethodRepository;

    /**
     * 결제수단 등록
     */
    public void register(Long userId, PaymentMethodRegisterRequestDto dto) { // 결제수단 등록
        User user = User.reference(userId);
        PaymentMethod pm = PaymentMethod.createPaymentMethod(
                user, com.ottproject.ottbackend.enums.PaymentProvider.IMPORT, dto.type, dto.providerMethodId);
        pm.describeCard(dto.brand, dto.last4, dto.expiryMonth, dto.expiryYear);
        pm.applyListingOptions(dto.isDefault, dto.priority, dto.label);
        paymentMethodRepository.save(pm);
    }

    /**
     * 결제수단 목록 조회(기본 수단 우선)
     */
    @Transactional(readOnly = true)
    public List<PaymentMethodResponseDto> list(Long userId) { // 결제수단 목록(삭제 제외)
        return paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void setDefault(Long userId, Long paymentMethodId) { // 기본 수단 지정(단일화)
        PaymentMethod target = paymentMethodRepository
                .findByIdAndUser_Id(paymentMethodId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        paymentMethodRepository
                .findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(userId)
                .forEach(pm -> {
                    if (pm.getId().equals(target.getId())) {
                        pm.markAsDefault();
                    } else {
                        pm.clearDefault();
                    }
                });
    }

    @Transactional
    public void delete(Long userId, Long paymentMethodId) { // 소프트 삭제
        PaymentMethod target = paymentMethodRepository
                .findByIdAndUser_Id(paymentMethodId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // 승격 후보는 삭제 '전에' 고른다. 삭제 뒤에 목록을 읽으면 방금 지운 행이 결과에서 빠져 있기를
        // 영속성 컨텍스트의 flush 타이밍에 기대게 된다. 그 기대가 어긋나면 지운 행이 목록 맨 앞으로
        // 돌아와(is_default desc) 자기 자신을 다시 기본으로 올리고, 살아 있는 수단 중에는 기본이 없어진다.
        // 삭제 전 목록에서 대상만 걸러내면 같은 결과를 순서에 기대지 않고 얻는다.
        Optional<PaymentMethod> successor = target.isDefault()
                ? paymentMethodRepository
                        .findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(userId)
                        .stream()
                        .filter(pm -> !pm.getId().equals(paymentMethodId))
                        .findFirst()
                : Optional.empty(); // 기본이 아니었으면 재지정할 것이 없다
        target.softDelete(java.time.LocalDateTime.now());
        successor.ifPresent(PaymentMethod::markAsDefault);
    }

    @Transactional
    public void updatePartial(Long userId, Long paymentMethodId, PaymentMethodUpdateRequestDto patch) { // 일부 필드 수정
        PaymentMethod pm = paymentMethodRepository
                .findByIdAndUser_Id(paymentMethodId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (patch.label != null) pm.rename(patch.label);
        if (patch.priority != null) pm.changePriority(patch.priority);
        // 만료 월/연도는 엔티티에서 한 벌로 바뀐다. 패치에 한쪽만 오면 나머지는 현재 값을 그대로 넘긴다.
        if (patch.expiryMonth != null || patch.expiryYear != null) {
            pm.changeExpiry(
                    patch.expiryMonth != null ? patch.expiryMonth : pm.getExpiryMonth(),
                    patch.expiryYear != null ? patch.expiryYear : pm.getExpiryYear());
        }
    }

    private PaymentMethodResponseDto toDto(PaymentMethod pm) {
        PaymentMethodResponseDto d = new PaymentMethodResponseDto();
        d.id = pm.getId();
        d.provider = pm.getProvider();
        d.type = pm.getType();
        d.brand = pm.getBrand();
        d.last4 = pm.getLast4();
        d.expiryMonth = pm.getExpiryMonth();
        d.expiryYear = pm.getExpiryYear();
        d.isDefault = pm.isDefault();
        d.priority = pm.getPriority();
        d.label = pm.getLabel();
        d.createdAt = pm.getCreatedAt();
        d.updatedAt = pm.getUpdatedAt();
        return d;
    }
}
