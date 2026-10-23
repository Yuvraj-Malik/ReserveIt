package com.resrvit.domain;

public enum BookingStatus {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired"),
    CHECKED_OUT("Checked out"),
    RETURNED("Returned");

    private final String label;

    BookingStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
