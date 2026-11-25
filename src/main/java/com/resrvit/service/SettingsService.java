package com.resrvit.service;

import com.resrvit.domain.Setting;
import com.resrvit.repo.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SettingsService {

    public static final String EMAIL_DOMAIN = "email_domain";
    public static final String LEAD_DAYS = "equipment_lead_days";
    public static final String URGENT_PER_WEEK = "urgent_flags_per_week";
    public static final String FALLBACK_HOURS = "fallback_hours";
    public static final String GD_DAYS_AHEAD = "gd_days_ahead";
    public static final String STRIKE_LIMIT = "strike_limit";
    public static final String STRIKE_BLOCK_DAYS = "strike_block_days";
    public static final String LEADER_SUCCESSION = "leader_succession";

    private final SettingRepository repo;

    public String get(String key, String fallback) {
        return repo.findBySettingKey(key).map(Setting::getSettingValue).orElse(fallback);
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String emailDomain() {
        return get(EMAIL_DOMAIN, "thapar.edu").trim().toLowerCase();
    }

    public List<Setting> all() {
        return repo.findAllByOrderBySettingKeyAsc();
    }

    /** Checks a value before saving, so a typo can't silently fall back to a default or lock users out. */
    public void validate(String key, String value) {
        String v = value == null ? "" : value.trim();
        switch (key) {
            case EMAIL_DOMAIN -> {
                if (!v.toLowerCase().matches("[a-z0-9-]+(\\.[a-z0-9-]+)+")) {
                    throw new BookingException("Enter a domain like thapar.edu (no @, no spaces).");
                }
            }
            case LEADER_SUCCESSION -> {
                if (!v.equalsIgnoreCase("NEXT_MEMBER") && !v.equalsIgnoreCase("FACULTY_PICKS")) {
                    throw new BookingException("Leader succession must be NEXT_MEMBER or FACULTY_PICKS.");
                }
            }
            case LEAD_DAYS, FALLBACK_HOURS, GD_DAYS_AHEAD, STRIKE_LIMIT, STRIKE_BLOCK_DAYS, URGENT_PER_WEEK -> {
                int n;
                try {
                    n = Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    throw new BookingException("Enter a whole number.");
                }
                int min = key.equals(URGENT_PER_WEEK) ? 0 : 1;
                if (n < min || n > 365) throw new BookingException("Enter a number between " + min + " and 365.");
            }
            default -> throw new BookingException("Unknown setting: " + key);
        }
    }

    @Transactional
    public void set(String key, String value) {
        validate(key, value);
        Setting s = repo.findBySettingKey(key).orElseGet(() -> {
            Setting n = new Setting();
            n.setSettingKey(key);
            return n;
        });
        s.setSettingValue(value.trim());
        repo.save(s);
    }
}
