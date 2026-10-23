package com.resrvit.domain;

public enum DayType {
    WORKING("Working day"),
    HOLIDAY("Holiday"),
    EXAM("Exam day");

    private final String label;

    DayType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
