package com.resrvit.domain;

public enum UserStatus {
    PENDING_VERIFICATION("Email not verified"),
    PENDING_ACTIVATION("Awaiting activation"),
    ACTIVE("Active"),
    DEACTIVATED("Deactivated");

    private final String label;

    UserStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
