package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.PaymentMethodRegisterRequestDto;
import com.ottproject.ottbackend.dto.PaymentMethodResponseDto;
import com.ottproject.ottbackend.dto.PaymentMethodUpdateRequestDto;
import com.ottproject.ottbackend.entity.PaymentMethod;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.repository.PaymentMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 결제수단 서비스
 * - 저장 결제수단 등록/목록 조회 제공
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
		PaymentMethod pm = PaymentMethod.builder()
				.user(User.builder().id(userId).build())
				.provider(dto.provider)
				.type(dto.type)
				.providerMethodId(dto.providerMethodId)
				.brand(dto.brand)
				.last4(dto.last4)
				.expiryMonth(dto.expiryMonth)
				.expiryYear(dto.expiryYear)
				.isDefault(dto.isDefault)
				.priority(dto.priority)
				.label(dto.label)
				.build();
		paymentMethodRepository.save(pm);
	}

	/**
	 * 결제수단 목록 조회(기본 수단 우선)
	 */
	@Transactional(readOnly = true)
	public List<PaymentMethodResponseDto> list(Long userId) { // 결제수단 목록(삭제 제외)
		return paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(userId)
				.stream()
				.map(this::toDto)
				.toList();
	}

	@Transactional
	public void setDefault(Long userId, Long paymentMethodId) { // 기본 수단 지정(단일화)
		PaymentMethod target = paymentMethodRepository.findByIdAndUser_Id(paymentMethodId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(userId)
				.forEach(pm -> pm.setDefault(pm.getId().equals(target.getId())));
	}

	@Transactional
	public void delete(Long userId, Long paymentMethodId) { // 소프트 삭제
		PaymentMethod target = paymentMethodRepository.findByIdAndUser_Id(paymentMethodId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		target.setDeletedAt(java.time.LocalDateTime.now());
		if (target.isDefault()) {
			paymentMethodRepository.findByUser_IdAndDeletedAtIsNullOrderByIsDefaultDescPriorityAsc(userId)
					.stream().findFirst()
					.ifPresent(first -> first.setDefault(true));
		}
	}

	@Transactional
	public void updatePartial(Long userId, Long paymentMethodId, PaymentMethodUpdateRequestDto patch) { // 일부 필드 수정
		PaymentMethod pm = paymentMethodRepository.findByIdAndUser_Id(paymentMethodId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (patch.label != null) pm.setLabel(patch.label);
		if (patch.priority != null) pm.setPriority(patch.priority);
		if (patch.expiryMonth != null) pm.setExpiryMonth(patch.expiryMonth);
		if (patch.expiryYear != null) pm.setExpiryYear(patch.expiryYear);
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


