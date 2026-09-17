package com.ottproject.ottbackend.exception;

/**
 * 관리자가 본 애니 버전과 현재 행의 버전이 다를 때 던진다.
 *
 * 수정 폼을 연 뒤 다른 관리자가 같은 작품을 먼저 저장했다는 뜻이다. 그대로 저장하면 그 수정을 덮어쓴다.
 */
public class AnimeVersionConflictException extends RuntimeException {

    public AnimeVersionConflictException(Long animeId, Long requestVersion, Long currentVersion) {
        super("애니 버전 충돌: id=" + animeId + ", 요청=" + requestVersion + ", 현재=" + currentVersion);
    }
}
