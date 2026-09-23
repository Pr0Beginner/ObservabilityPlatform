package org.zmy.observabilityplatform.logging.infrastructure.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zmy.observabilityplatform.logging.domain.service.LogEntryFactory;
import org.zmy.observabilityplatform.logging.domain.service.LogFingerprintGenerator;
import org.zmy.observabilityplatform.logging.domain.service.LogParser;
import org.zmy.observabilityplatform.logging.domain.service.LogParserRegistry;
import org.zmy.observabilityplatform.logging.domain.service.SensitiveDataProtector;
import org.zmy.observabilityplatform.logging.domain.service.TraceReconstructionService;
import org.zmy.observabilityplatform.logging.infrastructure.parser.JsonSensitiveDataProtector;

import java.util.List;

@Configuration
public class LoggingDomainConfiguration {
    @Bean
    LogFingerprintGenerator logFingerprintGenerator() {
        return new LogFingerprintGenerator();
    }

    @Bean
    SensitiveDataProtector sensitiveDataProtector(ObjectMapper objectMapper) {
        return new JsonSensitiveDataProtector(objectMapper);
    }

    @Bean
    LogParserRegistry logParserRegistry(List<LogParser> parsers) {
        return new LogParserRegistry(parsers);
    }

    @Bean
    LogEntryFactory logEntryFactory(LogParserRegistry parserRegistry,
                                    SensitiveDataProtector sensitiveDataProtector,
                                    LogFingerprintGenerator fingerprintGenerator) {
        return new LogEntryFactory(parserRegistry, sensitiveDataProtector, fingerprintGenerator);
    }

    @Bean
    TraceReconstructionService traceReconstructionService() {
        return new TraceReconstructionService();
    }
}
