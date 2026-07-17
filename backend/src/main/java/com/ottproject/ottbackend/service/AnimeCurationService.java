package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.admin.AdminAnimeListItemDto;
import com.ottproject.ottbackend.dto.admin.AnimeCurationSearchCondition;
import com.ottproject.ottbackend.repository.curation.AnimeCurationQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 애니 큐레이션 서비스
 *
 * 큰 흐름
 * - 운영자가 조건으로 애니를 찾고, 배지/노출 여부/제목/포스터를 고칠 수 있게 한다.
 * - 이 값들은 외부 API 가 주는 게 아니라 운영자가 판단하는 값이다.
 *
 * 트랜잭션(명시)
 * - 클래스 레벨 @Transactional 로 쓰기를 기본으로 두고, 읽기 메서드에만 readOnly=true 를 덧씌운다.
 *   PaymentCommandService/AdminContentService 와 같은 관례다.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AnimeCurationService {

    private final AnimeCurationQueryRepository curationQueryRepository;

    /**
     * 조건 검색.
     * 조건이 비어 있으면 전체를 돌려준다(검색에서는 정상이다 — 벌크에서만 위험해서 그쪽에서 따로 막는다).
     */
    @Transactional(readOnly = true)
    public PagedResponse<AdminAnimeListItemDto> search(AnimeCurationSearchCondition condition, int page, int size) {
        List<AdminAnimeListItemDto> items = curationQueryRepository.search(condition, page, size)
                .stream()
                .map(AdminAnimeListItemDto::from)
                .toList();
        long total = curationQueryRepository.countByCondition(condition);

        return new PagedResponse<>(items, total, page, size);
    }
}
