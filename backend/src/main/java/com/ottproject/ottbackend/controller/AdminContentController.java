package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.AdminContentRequestDto;
import com.ottproject.ottbackend.dto.AdminContentResponseDto;
import com.ottproject.ottbackend.service.AdminContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    @Operation(summary = "공개 컨텐츠 조회(DB)", description = "공개된 컨텐츠를 타입과 언어별로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/public/{type}") // GET /public/{type}
    public ResponseEntity<List<AdminContentResponseDto>> listPublic(
            @Parameter(description = "컨텐츠 타입 (FAQ, BENEFIT, CTA)", required = true) 
            @PathVariable String type, // 유형 경로 변수
            @Parameter(description = "언어 코드 (기본값: ko)", required = false) 
            @RequestParam(defaultValue = "ko") String locale // 언어
    ) { // 메서드 시작
        return ResponseEntity.ok(service.listPublic(type.toUpperCase(), locale)); // 서비스 호출 후 200
    }

    @Operation(summary = "관리자용 목록", description = "관리자가 컨텐츠를 타입과 언어별로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping // GET /
    public ResponseEntity<List<AdminContentResponseDto>> list(
            @Parameter(description = "컨텐츠 타입 (FAQ, BENEFIT, CTA)", required = true) 
            @RequestParam String type, // 필수 유형
            @Parameter(description = "언어 코드", required = false) 
            @RequestParam(required = false) String locale // 선택 언어
    ) { // 메서드 시작
        return ResponseEntity.ok(service.list(type.toUpperCase(), locale)); // 서비스 호출 후 200
    }

    @Operation(summary = "컨텐츠 생성", description = "새로운 컨텐츠를 생성합니다.")
    @ApiResponse(responseCode = "200", description = "생성 성공")
    @PostMapping // POST /
    public ResponseEntity<AdminContentResponseDto> create(
            @Parameter(description = "컨텐츠 생성 정보", required = true) 
            @RequestBody AdminContentRequestDto dto) { // 바디 입력
        return ResponseEntity.ok(service.create(dto)); // 생성 후 반환
    }

    @Operation(summary = "컨텐츠 수정", description = "기존 컨텐츠를 수정합니다.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @PutMapping("/{id}") // PUT /{id}
    public ResponseEntity<AdminContentResponseDto> update(
            @Parameter(description = "컨텐츠 ID", required = true) 
            @PathVariable Long id, 
            @Parameter(description = "컨텐츠 수정 정보", required = true) 
            @RequestBody AdminContentRequestDto dto) { // 경로+바디
        return ResponseEntity.ok(service.update(id, dto)); // 수정 후 반환
    }

    @Operation(summary = "컨텐츠 삭제", description = "컨텐츠를 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/{id}") // DELETE /{id}
    public ResponseEntity<Void> delete(
            @Parameter(description = "컨텐츠 ID", required = true) 
            @PathVariable Long id) { // 경로 변수
        service.delete(id); // 삭제 수행
        return ResponseEntity.noContent().build(); // 204 반환
    }

    @Operation(summary = "공개 토글", description = "컨텐츠의 공개 상태를 토글합니다.")
    @ApiResponse(responseCode = "200", description = "토글 성공")
    @PutMapping("/{id}/publish") // PUT /{id}/publish
    public ResponseEntity<AdminContentResponseDto> publish(
            @Parameter(description = "컨텐츠 ID", required = true) 
            @PathVariable Long id, 
            @Parameter(description = "공개 여부 (true: 공개, false: 비공개)", required = true) 
            @RequestParam boolean value) { // query value
        return ResponseEntity.ok(service.setPublish(id, value)); // 결과 반환
    }

    @Operation(summary = "순서 변경", description = "컨텐츠의 표시 순서를 변경합니다.")
    @ApiResponse(responseCode = "200", description = "순서 변경 성공")
    @PutMapping("/{id}/position") // PUT /{id}/position
    public ResponseEntity<AdminContentResponseDto> position(
            @Parameter(description = "컨텐츠 ID", required = true) 
            @PathVariable Long id, 
            @Parameter(description = "새로운 순서 위치", required = true) 
            @RequestParam int position) { // query position
        return ResponseEntity.ok(service.updatePosition(id, position)); // 결과 반환
    }
}


