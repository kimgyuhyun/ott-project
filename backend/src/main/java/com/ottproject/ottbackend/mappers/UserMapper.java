package com.ottproject.ottbackend.mappers; // 매퍼 패키지

import com.ottproject.ottbackend.dto.RegisterRequestDto; // 가입 요청 DTO(입력 전용)
import com.ottproject.ottbackend.dto.UserResponseDto;    // 사용자 응답 DTO(출력 전용)
import com.ottproject.ottbackend.entity.User;            // 사용자 엔티티
import org.mapstruct.*;                                  // MapStruct 애노테이션들
import java.util.List;                                   // 리스트 매핑용

/**
 * User 매핑 규칙
 * - 출력: 엔티티 -> 응답 DTO(UserResponseDto)
 * - 입력: 가입 요청 DTO(RegisterRequestDto) -> 엔티티(User)
 * - 민감/내부 필드(password, providerId 등)는 요청에서만 사용하거나 서비스에서 설정
 */
@Mapper(
        componentModel = "spring",                         // 스프링 빈으로 등록
        unmappedTargetPolicy = ReportingPolicy.IGNORE      // 매핑되지 않은 대상 필드는 경고 무시(로그만)
)
public interface UserMapper {

    // ===== 출력: 엔티티 -> 응답 DTO =====
    UserResponseDto toUserResponseDto(User user);          // User -> UserResponseDto (민감/내부 필드는 DTO 에 없음)

    List<UserResponseDto> toUserResponseDtoList(List<User> users); // 리스트 매핑(User 리스트 -> 응답 DTO 리스트)

    // ===== 입력: 가입 요청 DTO -> 엔티티 =====
    @Mappings({
            @Mapping(target = "password", source = "password"),     // 비밀번호: 서비스에서 인코딩 후 저장
            @Mapping(target = "authProvider", constant = "LOCAL"),  // 가입 경로: 기본 LOCAL (enum 이름과 일치해야 함)
            @Mapping(target = "providerId", ignore = true),         // 로컬 가입은 providerId 없음
            @Mapping(target = "role", ignore = true),               // 기본값은 엔티티/서비스에서 설정
            @Mapping(target = "emailVerified", constant = "false"), // 이메일 인증 기본 false
            @Mapping(target = "enabled", constant = "true")         // 계정 활성 기본 true
            // createdAt/updatedAt 등은 Auditing 으로 자동 처리 → 명시 불필요
    })
    User fromRegister(RegisterRequestDto dto);              // RegisterRequestDto -> User

    // 참고:
    // - 응답 DTO(UserResponseDto) -> 엔티티 변환 메서드는 보안/정합성 문제로 제공하지 않음(의도적으로 비권장).
    // - 부분 수정이 필요하면 별도의 UpdateUserRequestDto 를 만들고,
    //   아래와 같이 null 무시 업데이트를 추가하는 방식을 권장.
    //
    // @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    // void updateUserFromRequest(UpdateUserRequestDto dto, @MappingTarget User user);
}