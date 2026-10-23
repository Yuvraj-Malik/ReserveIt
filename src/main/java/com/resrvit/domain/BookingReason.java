package com.resrvit.domain;

public enum BookingReason {
    EVALUATION_PRACTICE("Evaluation practice"),
    PROJECT_WORK("Project work"),
    COURSEWORK("Coursework"),
    OTHER("Other");

    private final String label;

    BookingReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
