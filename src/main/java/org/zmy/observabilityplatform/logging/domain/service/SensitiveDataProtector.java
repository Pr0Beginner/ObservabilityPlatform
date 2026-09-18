package org.zmy.observabilityplatform.logging.domain.service;

import lombok.Value;

import java.util.List;
import java.util.regex.Pattern;

public class SensitiveDataProtector {
    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(?i)(password|passwd|pwd)(\\s*[=:]\\s*)[^\\s,;]+"), "$1$2***"),
            new Rule(Pattern.compile("(?i)(authorization|token)(\\s*[=:]\\s*)(bearer\\s+)?[^\\s,;]+"), "$1$2***"),
            new Rule(Pattern.compile("(?i)(cookie)(\\s*[=:]\\s*)[^\\r\\n]+"), "$1$2***"),
            new Rule(Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)"), "1**********")
    );

    public String protect(String value) {
        String protectedValue = value == null ? "" : value;
        for (Rule rule : RULES) {
            protectedValue = rule.getPattern().matcher(protectedValue).replaceAll(rule.getReplacement());
        }
        return protectedValue;
    }

    @Value
    private static class Rule {
        Pattern pattern;
        String replacement;
    }
}
