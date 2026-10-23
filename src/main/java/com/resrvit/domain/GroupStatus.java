package com.resrvit.domain;

public enum GroupStatus {
    PENDING_CONFIRMATION("Waiting for members"),
    PENDING_FACULTY("Waiting for faculty"),
    ACTIVE("Active"),
    DISSOLVED("Dissolved");

    private final String label;

    GroupStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
