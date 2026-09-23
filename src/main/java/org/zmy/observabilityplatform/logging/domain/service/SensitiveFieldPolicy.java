package org.zmy.observabilityplatform.logging.domain.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Format-independent rules; JSON parsing belongs to the infrastructure adapter. */
public final class SensitiveFieldPolicy {
    private static final List<String> SENSITIVE_NAMES = List.of(
            "password", "passwd", "pwd", "authorization", "token", "cookie", "apikey", "secret", "credential");
    private static final String KEY = "[\\w.-]*(?:password|passwd|pwd|authorization|token|cookie|api[_-]?key|secret|credential)[\\w.-]*";
    private static final String QUOTED_VALUE = "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'";
    private static final Pattern JSON_PAIR = Pattern.compile(
            "(?i)([\"']" + KEY + "[\"']\\s*:\\s*)(?:" + QUOTED_VALUE + "|[^\\s,;}]+)");
    private static final Pattern COOKIE = Pattern.compile("(?i)(\\bcookie\\s*[=:]\\s*)[^\\r\\n]+");
    private static final Pattern TEXT_PAIR = Pattern.compile(
            "(?i)(\\b" + KEY + "\\s*[=:]\\s*)(?:" + QUOTED_VALUE + "|(?:bearer\\s+)?[^\\s,;]+)");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    private SensitiveFieldPolicy() {
    }

    public static boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_NAMES.stream().anyMatch(normalized::contains);
    }

    public static String protectText(String value) {
        String result = JSON_PAIR.matcher(value == null ? "" : value).replaceAll("$1\"***\"");
        result = COOKIE.matcher(result).replaceAll("$1***");
        result = TEXT_PAIR.matcher(result).replaceAll("$1***");
        return PHONE.matcher(result).replaceAll("1**********");
    }
}
