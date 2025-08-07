package com.ottproject.ottbackend.enums;

public enum AnimeStatus {
    ONGOING("방영중"),
    COMPLETED("완결"),
    UPCOMING("방영예정"),
    HIATUS("방영중단");

    private final String description;

    AnimeStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
