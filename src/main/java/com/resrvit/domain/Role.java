package com.resrvit.domain;

public enum Role {
    SUPER_ADMIN("Super admin"),
    GD_ADMIN("GD Room Admin"),
    SOCIETY_ADMIN("Society Room Admin"),
    FACULTY("Faculty"),
    LAB_ASSISTANT("Lab assistant"),
    STUDENT("Student");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
