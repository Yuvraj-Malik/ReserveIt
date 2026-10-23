package com.resrvit.domain;

public enum EquipmentStatus {
    AVAILABLE("Available"),
    MAINTENANCE("Under maintenance"),
    RETIRED("Retired");

    private final String label;

    EquipmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
