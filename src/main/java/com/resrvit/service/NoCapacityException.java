package com.resrvit.service;

/** Approval failed because the slot is full. Thrown without rolling back the bump offers it created. */
public class NoCapacityException extends BookingException {
    public NoCapacityException(String message) {
        super(message);
    }
}
