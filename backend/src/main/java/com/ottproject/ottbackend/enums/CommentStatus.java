package com.ottproject.ottbackend.enums;

// ========== 댓글 상태 enum ==========

/**
 * 댓글 상태를 나타내는 enum
 */
public enum CommentStatus {
    ACTIVE("활성"),      // 정상 상태
    DELETED("삭제됨"),   // 삭제된 상태
    REPORTED("신고됨");  // 신고된 상태

    private final String displayName; // 화면에 표시할 이름

    CommentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

