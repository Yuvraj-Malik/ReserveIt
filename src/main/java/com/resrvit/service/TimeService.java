package com.resrvit.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Single source of "now" so tests can move the clock. Campus time is Asia/Kolkata. */
@Service
public class TimeService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private Clock clock = Clock.system(ZONE);

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** For tests only. */
    public void setClock(Clock clock) {
        this.clock = clock;
    }
}
