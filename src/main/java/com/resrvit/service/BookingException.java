package com.resrvit.service;

/** A rule was broken. The message is shown to the user as-is. */
public class BookingException extends RuntimeException {
    public BookingException(String message) {
        super(message);
    }
}
