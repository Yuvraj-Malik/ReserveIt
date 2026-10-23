package com.resrvit.domain;

public enum RoomType {
    GD_ROOM("GD room"),
    EVALUATION_LAB("Evaluation lab"),
    SEMINAR_ROOM("Seminar room"),
    SOCIETY_ROOM("Society room"),
    PERMANENT("Permanently assigned");

    private final String label;

    RoomType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
