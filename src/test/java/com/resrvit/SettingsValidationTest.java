package com.resrvit;

import com.resrvit.service.BookingException;
import com.resrvit.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** System settings are validated so a typo can't silently fall back to a default or lock everyone out. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SettingsValidationTest {

    @Autowired SettingsService settings;

    @Test
    void numbersMustBeWholeAndInRange() {
        assertThatThrownBy(() -> settings.set(SettingsService.LEAD_DAYS, "abc")).isInstanceOf(BookingException.class);
        assertThatThrownBy(() -> settings.set(SettingsService.STRIKE_LIMIT, "0")).isInstanceOf(BookingException.class);
        assertThatThrownBy(() -> settings.set(SettingsService.GD_DAYS_AHEAD, "1000")).isInstanceOf(BookingException.class);
        settings.set(SettingsService.LEAD_DAYS, " 10 ");
        assertThat(settings.getInt(SettingsService.LEAD_DAYS, 7)).isEqualTo(10);
    }

    @Test
    void emailDomainMustLookLikeADomain() {
        assertThatThrownBy(() -> settings.set(SettingsService.EMAIL_DOMAIN, "@thapar")).isInstanceOf(BookingException.class);
        assertThatThrownBy(() -> settings.set(SettingsService.EMAIL_DOMAIN, "thapar edu")).isInstanceOf(BookingException.class);
        settings.set(SettingsService.EMAIL_DOMAIN, "thapar.edu");
        assertThat(settings.emailDomain()).isEqualTo("thapar.edu");
    }

    @Test
    void unknownKeysAndBadSuccessionAreRejected() {
        assertThatThrownBy(() -> settings.set("made_up_key", "1")).isInstanceOf(BookingException.class);
        assertThatThrownBy(() -> settings.set(SettingsService.LEADER_SUCCESSION, "RANDOM")).isInstanceOf(BookingException.class);
        settings.set(SettingsService.LEADER_SUCCESSION, "FACULTY_PICKS");
    }
}
