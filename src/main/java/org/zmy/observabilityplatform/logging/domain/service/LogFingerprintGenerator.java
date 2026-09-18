package org.zmy.observabilityplatform.logging.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class LogFingerprintGenerator {
    public String generate(String service, String level, String message) {
        // 抹平 UUID、IP、数字和空白差异，使同类日志能够聚合到同一指纹。
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
