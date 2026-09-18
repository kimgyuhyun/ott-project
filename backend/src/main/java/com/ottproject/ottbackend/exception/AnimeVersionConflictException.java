package com.ottproject.ottbackend.exception;

import com.ottproject.ottbackend.dto.admin.AdminAnimeDetailDto;
import lombok.Getter;

/**
 * 관리자가 본 애니 버전과 현재 행의 버전이 다를 때 던진다.
 *
 * 수정 폼을 연 뒤 다른 관리자가 같은 작품을 먼저 저장했다는 뜻이다. 그대로 저장하면 그 수정을 덮어쓴다.
 *
 * 현재 값을 함께 들고 다니는 이유
 * - "다시 시도하세요"로 끝내면 관리자가 입력하던 내용을 버리고 처음부터 다시 써야 한다.
 *   응답에 서버의 현재 값을 실어 보내면 화면이 항목별로 내 값과 서버 값을 나란히 보여주고 고르게 할 수 있다.
 */
@Getter
public class AnimeVersionConflictException extends RuntimeException {

    /** 거절 시점의 서버 값(새 version 포함). 클라이언트가 충돌 화면을 그리는 데 쓴다. */
    private final transient AdminAnimeDetailDto current;

    public AnimeVersionConflictException(Long requestVersion, AdminAnimeDetailDto current) {
        super("애니 버전 충돌: id=" + current.getId() + ", 요청=" + requestVersion + ", 현재=" + current.getVersion());
        this.current = current;
    }
}
