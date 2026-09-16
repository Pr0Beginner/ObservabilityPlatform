package org.zmy.observabilityplatform.logging.domain;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class LogFingerprintGenerator {
    public String generate(String service, String level, String message) {
        String normalized = (service + "|" + level + "|" + message)
                .toLowerCase()
                .replaceAll("[0-9a-f]{8}-[0-9a-f-]{27,}", "<uuid>")
                .replaceAll("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b", "<ip>")
                .replaceAll("\\d+", "<n>")
                .replaceAll("\\s+", " ")
                .trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
