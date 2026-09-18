package com.ottproject.ottbackend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 단건 큐레이션 수정이 버전 충돌로 거절될 때의 409 응답 바디.
 *
 * ApiError(code/message)에 현재 값을 더한 형태다. 화면이 "내 값 / 서버 값"을 나란히 보여주려면
 * 거절과 동시에 서버 값을 알아야 한다. 다시 조회하게 하면 그 사이 또 바뀔 수 있다.
 */
@Getter
@AllArgsConstructor
public class AnimeCurationConflictResponse {

    private final String code;
    private final String message;

    /** 거절 시점의 서버 값. 이 안의 version 으로 다시 저장하면 된다. */
    private final AdminAnimeDetailDto current;
}
