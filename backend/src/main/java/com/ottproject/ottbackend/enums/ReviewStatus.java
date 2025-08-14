package com.ottproject.ottbackend.enums;

/**
 * 리뷰 상태
 * - ACTIVE/DELETED/REPORTED
 */
public enum ReviewStatus {
    ACTIVE("활성"),
    DELETED("삭제됨"),
    REPORTED("신고됨");

    private final String displayName;

    ReviewStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
