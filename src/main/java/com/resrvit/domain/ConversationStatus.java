package com.resrvit.domain;

public enum ConversationStatus {
    OPEN("Open"),
    RESOLVED("Resolved");

    private final String label;

    ConversationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
