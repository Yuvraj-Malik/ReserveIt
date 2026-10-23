package com.resrvit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Sends email when SMTP is configured (MAIL_HOST); otherwise logs it so local dev still works. */
@Service
@Slf4j
@RequiredArgsConstructor
public class MailService {

    private final ObjectProvider<JavaMailSender> sender;

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${resrvit.mail-from}")
    private String from;

    public boolean isConfigured() {
        return host != null && !host.isBlank();
    }

    @Async
    public void send(String to, String subject, String body) {
        if (!isConfigured()) {
            log.info("[mail disabled] To: {} | {} | {}", to, subject, body.replace('\n', ' '));
            return;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(to);
            m.setSubject("[ResrvIt] " + subject);
            m.setText(body);
            sender.getObject().send(m);
        } catch (Exception e) {
            log.warn("Could not send mail to {}: {}", to, e.getMessage());
        }
    }
}
