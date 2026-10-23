package com.resrvit.web;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String what) {
        super(what + " not found.");
    }
}
