package com.resrvit.domain;

public enum BookingKind {
    EQUIPMENT("Equipment"),
    GD_ROOM("GD room"),
    LAB_ROOM("Lab / seminar room"),
    SOCIETY_ROOM("Society room");

    private final String label;

    BookingKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
