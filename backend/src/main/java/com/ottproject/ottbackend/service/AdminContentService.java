package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.AdminContentRequestDto;
import com.ottproject.ottbackend.dto.AdminContentResponseDto;
import com.ottproject.ottbackend.entity.AdminContent;
import com.ottproject.ottbackend.repository.AdminContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AdminContentService
 *
 * 큰 흐름
 * - Admin 공개/관리 컨텐츠(FAQ/혜택/CTA)의 조회/CRUD/상태 변경을 제공한다.
 *
 * 메서드 개요
 * - listPublic: 공개 컨텐츠 목록
 * - list: 관리자 조회용 목록
 * - create/update/delete: 생성/수정/삭제
 * - setPublish/updatePosition: 공개 토글/순서 변경
 */
@Service // 서비스 빈 등록
@RequiredArgsConstructor // 생성자 주입
@Transactional // 트랜잭션 경계
public class AdminContentService { // 서비스 시작

    private final AdminContentRepository repository; // JPA 리포지토리

    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public List<AdminContentResponseDto> listPublic(String type, String locale) { // 공개 목록
        return repository.findByTypeAndLocaleAndPublishedOrderByPositionAsc(type, locale, true) // 조건 조회
                .stream().map(this::toDto).collect(Collectors.toList()); // DTO 변환
    }

    @Transactional(readOnly = true) // 읽기 전용
    public List<AdminContentResponseDto> list(String type, String locale) { // 관리자 목록
        if (locale != null && !locale.isEmpty()) { // locale 지정 시
            return repository.findByTypeAndLocaleOrderByPositionAsc(type, locale) // 조건 조회
                    .stream().map(this::toDto).collect(Collectors.toList()); // DTO 변환
        }
        return repository.findByTypeOrderByPositionAsc(type) // 전체 로케일
                .stream().map(this::toDto).collect(Collectors.toList()); // DTO 변환
    }

    public AdminContentResponseDto create(AdminContentRequestDto dto) { // 생성
        AdminContent entity = AdminContent.createAdminContent( // 빌더 시작
                dto.getTitle(), // 제목
                dto.getContent(), // 본문
                dto.getType(), // 유형
                dto.getPosition() == null ? 0 : dto.getPosition() // 순서 기본값 0
        );
        entity.setLocale(dto.getLocale()); // 언어 코드
        entity.setPublished(Boolean.TRUE.equals(dto.getPublished())); // 공개 여부
        entity.setActionText(dto.getActionText()); // CTA 텍스트
        entity.setActionUrl(dto.getActionUrl()); // CTA URL
        return toDto(repository.save(entity)); // 저장 후 DTO 반환
    }

    public AdminContentResponseDto update(Long id, AdminContentRequestDto dto) { // 부분 수정
        AdminContent entity = repository.findById(id).orElseThrow(); // 조회
        if (dto.getType() != null) entity.setType(dto.getType()); // 유형
        if (dto.getLocale() != null) entity.setLocale(dto.getLocale()); // 언어
        if (dto.getPosition() != null) entity.setPosition(dto.getPosition()); // 순서
        if (dto.getPublished() != null) entity.setPublished(dto.getPublished()); // 공개 여부
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle()); // 제목
        if (dto.getContent() != null) entity.setContent(dto.getContent()); // 본문
        if (dto.getActionText() != null) entity.setActionText(dto.getActionText()); // CTA 텍스트
        if (dto.getActionUrl() != null) entity.setActionUrl(dto.getActionUrl()); // CTA URL
        return toDto(entity); // DTO 변환
    }

    public void delete(Long id) { // 삭제
        repository.deleteById(id); // by id 삭제
    }

    public AdminContentResponseDto setPublish(Long id, boolean value) { // 공개 토글
        AdminContent entity = repository.findById(id).orElseThrow(); // 조회
        entity.setPublished(value); // 플래그 변경
        return toDto(entity); // DTO 반환
    }

    public AdminContentResponseDto updatePosition(Long id, int position) { // 순서 변경
        AdminContent entity = repository.findById(id).orElseThrow(); // 조회
        entity.setPosition(position); // 위치 설정
        return toDto(entity); // DTO 반환
    }

    private AdminContentResponseDto toDto(AdminContent c) { // 엔티티→DTO
        return AdminContentResponseDto.builder() // 빌더 시작
                .id(c.getId()) // id
                .type(c.getType()) // 유형
                .locale(c.getLocale()) // 언어
                .position(c.getPosition()) // 순서
                .published(c.isPublished()) // 공개 여부
                .title(c.getTitle()) // 제목
                .content(c.getContent()) // 본문
                .actionText(c.getActionText()) // CTA 텍스트
                .actionUrl(c.getActionUrl()) // CTA URL
                .createdAt(c.getCreatedAt()) // 생성 시각
                .updatedAt(c.getUpdatedAt()) // 수정 시각
                .build(); // DTO 완성
    }
}


