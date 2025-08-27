package com.ottproject.ottbackend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

/**
 * Admin 공개 컨텐츠(FAQ/혜택/CTA) 정적 제공 1단계 컨트롤러
 *
 * 큰 흐름
 * - classpath 리소스(`resources/admin/*.json`)를 읽어 바로 반환한다.
 * - locale 파라미터로 ko/en 필터링하며, 없으면 전체/기본을 반환한다.
 * - 운영자 쓰기 없이 배포만으로 컨텐츠 갱신한다.
 *
 * 필드 개요
 * - objectMapper: JSON 문자열을 List/Map으로 역직렬화하기 위한 Jackson ObjectMapper
 */
@RestController // REST 컨트롤러 선언
@RequiredArgsConstructor // 생성자 주입(현재 필드 없음이지만 표준화 유지)
@RequestMapping("/api/admin/public") // 공개용 베이스 경로
@Tag(name = "AdminPublic", description = "Admin 공개 컨텐츠(정적 JSON)") // Swagger 태그
public class AdminPublicController { // 공개 컨트롤러 시작

    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON 직렬화/역직렬화 도구

    @Operation(summary = "FAQ (정적)", description = "정적 JSON 파일에서 FAQ 컨텐츠를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping(value = "/faq", produces = MediaType.APPLICATION_JSON_VALUE) // GET /faq JSON 반환
    public ResponseEntity<List<Map<String, Object>>> getFaq(
            @Parameter(description = "언어 코드 (ko, en, 기본값: ko)", required = false) 
            @RequestParam(defaultValue = "ko") String locale) { // locale 기본 ko
        List<Map<String, Object>> all = readListJson("admin/faq.json"); // 전체 JSON 로드
        List<Map<String, Object>> filtered = all.stream() // 스트림 변환
                .filter(it -> locale.equalsIgnoreCase(String.valueOf(it.getOrDefault("locale", "ko")))) // locale 일치 필터
                .collect(Collectors.toList()); // 리스트 수집
        return ResponseEntity.ok(filtered.isEmpty() ? all : filtered); // 필터 결과 없으면 전체 반환
    }

    @Operation(summary = "혜택 비교 (정적)", description = "정적 JSON 파일에서 멤버십 혜택 비교 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping(value = "/benefits", produces = MediaType.APPLICATION_JSON_VALUE) // GET /benefits JSON
    public ResponseEntity<List<Map<String, Object>>> getBenefits(
            @Parameter(description = "언어 코드 (ko, en, 기본값: ko)", required = false) 
            @RequestParam(defaultValue = "ko") String locale) { // locale 파라미터
        List<Map<String, Object>> all = readListJson("admin/benefits.json"); // JSON 로드
        List<Map<String, Object>> filtered = all.stream() // 스트림 변환
                .filter(it -> locale.equalsIgnoreCase(String.valueOf(it.getOrDefault("locale", "ko")))) // locale 필터
                .collect(Collectors.toList()); // 리스트 수집
        return ResponseEntity.ok(filtered.isEmpty() ? all : filtered); // 결과 반환
    }

    @Operation(summary = "CTA (정적)", description = "정적 JSON 파일에서 CTA(Call-to-Action) 컨텐츠를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping(value = "/cta", produces = MediaType.APPLICATION_JSON_VALUE) // GET /cta JSON
    public ResponseEntity<Map<String, Object>> getCta(
            @Parameter(description = "언어 코드 (ko, en, 기본값: ko)", required = false) 
            @RequestParam(defaultValue = "ko") String locale) { // locale 파라미터
        List<Map<String, Object>> all = readListJson("admin/cta.json"); // JSON 로드
        Map<String, Object> first = all.stream() // 스트림 변환
                .filter(it -> locale.equalsIgnoreCase(String.valueOf(it.getOrDefault("locale", "ko")))) // locale 필터
                .findFirst() // 첫 항목 선택
                .orElse(all.isEmpty() ? Map.of() : all.get(0)); // 없으면 기본 반환
        return ResponseEntity.ok(first); // 응답 반환
    }

    /**
     * 공개 헬스체크 엔드포인트
     */
    @Operation(summary = "헬스체크", description = "서버 상태 확인")
    @ApiResponse(responseCode = "200", description = "서버 정상 동작")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis());
        response.put("message", "OTT Backend Server is running!");
        return ResponseEntity.ok(response);
    }

    private List<Map<String, Object>> readListJson(String path) { // 리소스 JSON 읽기 유틸
        try (InputStream is = new ClassPathResource(path).getInputStream()) { // classpath 로드
            byte[] bytes = is.readAllBytes(); // 전부 읽기
            String json = new String(bytes, StandardCharsets.UTF_8); // UTF-8 문자열 변환
            return objectMapper.readValue(json, new TypeReference<>() {}); // JSON → List<Map>
        } catch (Exception e) { // 예외 발생 시
            return List.of(); // 빈 목록 반환(안전)
        }
    }
}


