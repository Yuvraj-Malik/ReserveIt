package com.resrvit.domain;

public enum IncidentStatus {
    OPEN("Open"),
    RESOLVED("Resolved");

    private final String label;

    IncidentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
