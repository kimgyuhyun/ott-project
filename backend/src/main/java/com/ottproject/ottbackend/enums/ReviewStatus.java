package com.ottproject.ottbackend.enums;

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
