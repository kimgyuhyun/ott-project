package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.AdminContentRequestDto;
import com.ottproject.ottbackend.dto.AdminContentResponseDto;
import com.ottproject.ottbackend.service.AdminContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * Admin 컨텐츠 관리 컨트롤러(DB 기반)
 *
 * 큰 흐름
 * - 공개용 조회를 제공(권한 불필요)하고, 관리자 전용으로 CRUD/토글/순서 변경을 제공한다.
 * - type 파라미터는 대문자(FAQ|BENEFIT|CTA) 기준으로 처리한다.
 *
 * 엔드포인트 개요
 * - GET /public/{type}: 공개용 컨텐츠 조회
 * - GET /?type=...: 관리자 목록
 * - POST /: 생성
 * - PUT /{id}: 수정
 * - DELETE /{id}: 삭제
 * - PUT /{id}/publish?value=: 공개 토글
 * - PUT /{id}/position?position=: 순서 변경
 */
@RestController // REST 컨트롤러
@RequiredArgsConstructor // 생성자 주입
@RequestMapping("/api/admin/contents") // 베이스 경로
@Tag(name = "AdminContents", description = "Admin 컨텐츠 관리(DB)") // Swagger 태그
public class AdminContentController { // 컨트롤러 시작

    private final AdminContentService service; // 서비스 의존성

    @Operation(summary = "공개 컨텐츠 조회(DB)") // 공개 조회
    @GetMapping("/public/{type}") // GET /public/{type}
    public ResponseEntity<List<AdminContentResponseDto>> listPublic(
            @PathVariable String type, // 유형 경로 변수
            @RequestParam(defaultValue = "ko") String locale // 언어
    ) { // 메서드 시작
        return ResponseEntity.ok(service.listPublic(type.toUpperCase(), locale)); // 서비스 호출 후 200
    }

    @Operation(summary = "관리자용 목록") // 관리자 목록
    @GetMapping // GET /
    public ResponseEntity<List<AdminContentResponseDto>> list(
            @RequestParam String type, // 필수 유형
            @RequestParam(required = false) String locale // 선택 언어
    ) { // 메서드 시작
        return ResponseEntity.ok(service.list(type.toUpperCase(), locale)); // 서비스 호출 후 200
    }

    @Operation(summary = "생성") // 생성
    @PostMapping // POST /
    public ResponseEntity<AdminContentResponseDto> create(@RequestBody AdminContentRequestDto dto) { // 바디 입력
        return ResponseEntity.ok(service.create(dto)); // 생성 후 반환
    }

    @Operation(summary = "수정") // 수정
    @PutMapping("/{id}") // PUT /{id}
    public ResponseEntity<AdminContentResponseDto> update(@PathVariable Long id, @RequestBody AdminContentRequestDto dto) { // 경로+바디
        return ResponseEntity.ok(service.update(id, dto)); // 수정 후 반환
    }

    @Operation(summary = "삭제") // 삭제
    @DeleteMapping("/{id}") // DELETE /{id}
    public ResponseEntity<Void> delete(@PathVariable Long id) { // 경로 변수
        service.delete(id); // 삭제 수행
        return ResponseEntity.noContent().build(); // 204 반환
    }

    @Operation(summary = "공개 토글") // 공개 토글
    @PutMapping("/{id}/publish") // PUT /{id}/publish
    public ResponseEntity<AdminContentResponseDto> publish(@PathVariable Long id, @RequestParam boolean value) { // query value
        return ResponseEntity.ok(service.setPublish(id, value)); // 결과 반환
    }

    @Operation(summary = "순서 변경") // 순서 변경
    @PutMapping("/{id}/position") // PUT /{id}/position
    public ResponseEntity<AdminContentResponseDto> position(@PathVariable Long id, @RequestParam int position) { // query position
        return ResponseEntity.ok(service.updatePosition(id, position)); // 결과 반환
    }
}


