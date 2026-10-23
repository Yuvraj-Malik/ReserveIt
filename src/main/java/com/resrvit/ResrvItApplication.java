package com.resrvit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ResrvItApplication {

    public static void main(String[] args) {
        // Campus time. Also avoids the legacy "Asia/Calcutta" id, which PostgreSQL rejects on connect.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(ResrvItApplication.class, args);
    }
}
