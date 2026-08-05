package com.ottproject.ottbackend.util;

/**
 * PageLimitUtil
 *
 * 큰 흐름
 * - 클라이언트가 보낸 페이지 크기를 서버가 강제하는 상한으로 자른다.
 *
 * 왜 한 곳에 모으는가
 * - size 는 보정 없이 그대로 SQL LIMIT 으로 내려간다. 상한이 없으면 size=1000000 요청
 *   하나로 테이블을 통째로 읽어 응답을 만들게 된다.
 * - offset(= page * size)도 같은 값에서 나오므로, 자른 값으로 offset 까지 계산해야
 *   limit 만 잘리고 offset 은 거대한 채로 남는 상태가 생기지 않는다.
 * - 상한을 바꿀 일이 생겼을 때 고칠 자리를 하나로 둔다.
 *
 * 하한을 건드리지 않는 이유
 * - 이 유틸의 책임은 상한 강제 하나다. size 가 0 이나 음수일 때의 동작은 기존 그대로 둔다.
 */
public final class PageLimitUtil {

    /**
     * 목록 API 페이지 크기 상한.
     * 각 엔드포인트의 기본값이 6~20 이라 정상 사용 대비 5~15배 여유가 있고,
     * 한 번에 100건이면 프론트의 어느 목록 화면도 막히지 않는다.
     */
    public static final int MAX_PAGE_SIZE = 100;

    private PageLimitUtil() { }

    /**
     * 페이지 크기를 상한 이하로 자른다.
     *
     * @param size 클라이언트가 보낸 페이지 크기
     * @return MAX_PAGE_SIZE 를 넘지 않는 값
     */
    public static int clampSize(int size) {
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
