package com.ottproject.ottbackend.mapper;

import com.ottproject.ottbackend.dto.UserResponseDto;
import com.ottproject.ottbackend.entity.User;
import org.mapstruct.Mapper;

import java.util.List;

/*
사용자 엔티티와 DTO 간의 변환을 담당하는 매퍼 인터페이스
MapStruct 를 사용하여 컴파일 시점에 자동으로 구현체를 생성

@Mapper 어노테이션: MapStruct 가 이 인터페이스를 기반으로
UserMapperImpl 클래스를 자동 생성
 */
@Mapper(componentModel = "spring") // spring Bean 으로 등록되도록 설정
public interface UserMapper {
    UserResponseDto toUserResponseDto(User user); // User 엔티티 → UserResponseDto 변환
    User toUser(UserResponseDto userResponseDto); // UserResponseDto → User 엔티티 변환
    List<UserResponseDto> toUserResponseDtoList(List<User> users); // User 리스트 → UserResponseDto 리스트 변환
    List<User> toUserList(List<UserResponseDto> userResponseDtos); // UserResponseDto 리스트 → User 리스트 변환
}