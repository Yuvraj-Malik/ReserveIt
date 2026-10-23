package com.resrvit;

import com.resrvit.service.CryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CryptoServiceTest {

    @Autowired
    CryptoService crypto;

    @Test
    void roundTripsAndBindsToConversation() {
        CryptoService.Sealed a = crypto.encrypt("Are the probes available?", 7);
        CryptoService.Sealed b = crypto.encrypt("Are the probes available?", 7);
        assertThat(a.ciphertext()).isNotEqualTo(b.ciphertext()); // fresh IV each time
        assertThat(a.ciphertext()).doesNotContain("probes");
        assertThat(crypto.decrypt(a.ciphertext(), a.iv(), 7)).isEqualTo("Are the probes available?");
        // Moving the ciphertext to another conversation fails authentication.
        assertThat(crypto.decrypt(a.ciphertext(), a.iv(), 8)).isEqualTo("[message could not be decrypted]");
    }
}
