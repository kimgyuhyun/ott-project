package com.ottproject.ottbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 시청 프로필 이름 요청(생성·이름변경 공용)
 *
 * 두 요청이 받는 값이 이름 하나로 같아 한 클래스로 둔다. 생성과 변경이 서로 다른 필드를
 * 받게 되는 시점에 나눈다.
 *
 * 형식 검증은 여기서 하고, 도메인 불변식(공백·길이)은 엔티티에서 다시 검증한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ViewingProfileNameRequestDto {

    @NotBlank(message = "프로필 이름을 입력해주세요.")
    @Size(max = 20, message = "프로필 이름은 20자를 넘을 수 없습니다.")
    private String name;
}
