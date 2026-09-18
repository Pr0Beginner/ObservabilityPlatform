package org.zmy.observabilityplatform.logging.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogDomainTest {
    private final SensitiveDataProtector protector = new SensitiveDataProtector();
    private final LogFingerprintGenerator fingerprints = new LogFingerprintGenerator();

    @Test
    void masksCommonSecrets() {
        String value = protector.protect("phone=13812345678 password=hunter2 token=Bearer abc.def");

        assertThat(value).doesNotContain("13812345678", "hunter2", "abc.def");
    }

    @Test
    void normalizesVolatileValuesForFingerprinting() {
        String first = fingerprints.generate("orders", "ERROR", "timeout id=123 host=10.1.2.3");
        String second = fingerprints.generate("orders", "ERROR", "timeout id=456 host=10.9.8.7");

        assertThat(first).isEqualTo(second);
    }
}
