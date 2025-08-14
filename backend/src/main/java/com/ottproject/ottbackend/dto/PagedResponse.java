package com.ottproject.ottbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 페이지 응답 래퍼
 * - items/total/page/size 포함한 표준 페이징 응답
 */
@Getter // JSON 직렬화를 위한 읽기 접근자 제공
@AllArgsConstructor // items/total/page/size 한번에 주입 가능한 생성자 생성
public class PagedResponse<T> { // 페이지 응답을 표준화하는 제네릭 래퍼
    
    private final java.util.List<T> items; // 현재 페이지 데이터 목록
    private final long total; // 전체 데이터 개수(페이지네이션 계산용)
    private final int page; // 전체 페이지 번호(0-base)
    private final int size; // 페이지 크기
}
