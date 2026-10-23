package com.resrvit.domain;

public enum OfferStatus {
    OPEN("Open"),
    ACCEPTED("Accepted"),
    AUTO_ACCEPTED("Kept automatically"),
    SUPERSEDED("Not chosen");

    private final String label;

    OfferStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
