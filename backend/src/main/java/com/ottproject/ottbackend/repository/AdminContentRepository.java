package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.AdminContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * AdminContentRepository
 *
 * 큰 흐름
 * - Admin FAQ/혜택/CTA 등 공개/관리 컨텐츠를 관리하는 JPA 리포지토리.
 * - 유형/로케일/공개여부로 조회하며, 노출 순서는 position 오름차순을 따른다.
 *
 * 메서드 개요
 * - findByTypeAndLocaleAndPublishedOrderByPositionAsc: 유형+로케일 기준 공개 컨텐츠 목록
 * - findByTypeAndLocaleOrderByPositionAsc: 유형+로케일 기준 전체(공개/비공개) 목록
 * - findByTypeOrderByPositionAsc: 유형 기준 전체 로케일 목록
 */
public interface AdminContentRepository extends JpaRepository<AdminContent, Long> { // Repository 시작
    List<AdminContent> findByTypeAndLocaleAndPublishedOrderByPositionAsc(String type, String locale, boolean published); // 공개용
    List<AdminContent> findByTypeAndLocaleOrderByPositionAsc(String type, String locale); // 로케일 필터
    List<AdminContent> findByTypeOrderByPositionAsc(String type); // 전체 로케일
}


