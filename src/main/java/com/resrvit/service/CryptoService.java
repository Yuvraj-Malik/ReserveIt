package com.resrvit.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM for chat messages at rest. The key comes from RESRVIT_CHAT_KEY (base64, 32 bytes).
 * Each message gets a fresh 96-bit IV; the conversation id is bound in as associated data so a
 * ciphertext can't be moved to another thread.
 */
@Service
@Slf4j
public class CryptoService {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${resrvit.chat-key:}")
    private String keyB64;

    private final Environment env;
    private SecretKey key;

    public CryptoService(Environment env) {
        this.env = env;
    }

    public record Sealed(String ciphertext, String iv) {}

    @PostConstruct
    void init() throws Exception {
        byte[] raw;
        if (keyB64 == null || keyB64.isBlank()) {
            if (Arrays.asList(env.getActiveProfiles()).contains("prod")) {
                throw new IllegalStateException("RESRVIT_CHAT_KEY must be set in production (openssl rand -base64 32).");
            }
            log.warn("RESRVIT_CHAT_KEY is not set; using a fixed development key. Never do this in production.");
            raw = MessageDigest.getInstance("SHA-256").digest("resrvit-development-key".getBytes(StandardCharsets.UTF_8));
        } else {
            raw = Base64.getDecoder().decode(keyB64.trim());
            if (raw.length != 32) throw new IllegalStateException("RESRVIT_CHAT_KEY must decode to 32 bytes (AES-256).");
        }
        key = new SecretKeySpec(raw, "AES");
    }

    public Sealed encrypt(String plaintext, long conversationId) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(aad(conversationId));
            byte[] out = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new Sealed(Base64.getEncoder().encodeToString(out), Base64.getEncoder().encodeToString(iv));
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String ciphertext, String iv, long conversationId) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(iv)));
            c.updateAAD(aad(conversationId));
            return new String(c.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Could not decrypt a message in conversation {}: {}", conversationId, e.getMessage());
            return "[message could not be decrypted]";
        }
    }

    private static byte[] aad(long conversationId) {
        return ("conversation:" + conversationId).getBytes(StandardCharsets.UTF_8);
    }
}
