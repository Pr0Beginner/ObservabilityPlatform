package org.zmy.observabilityplatform;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureDependencyTest {
    @Test
    void domainAndApplicationDoNotImportOuterAdapters() throws Exception {
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String path = file.toString().replace('\\', '/');
                boolean domain = path.contains("/domain/");
                boolean application = path.contains("/application/");
                if (!domain && !application) {
                    continue;
                }
                for (String line : Files.readAllLines(file)) {
                    if (!line.startsWith("import ")) {
                        continue;
                    }
                    boolean adapter = line.contains(".infrastructure.") || line.contains(".interfaces.")
                            || line.contains("org.springframework.r2dbc.") || line.contains("io.r2dbc.")
                            || line.contains("org.springframework.kafka.") || line.contains("io.grpc.");
                    boolean domainFramework = domain && (line.contains(".application.")
                            || line.contains("org.springframework.") || line.contains("com.fasterxml.jackson."));
                    if (adapter || domainFramework) {
                        violations.add(path + ": " + line);
                    }
                }
            }
        }
        assertThat(violations).as("DDD dependencies must point toward the domain").isEmpty();
    }
}
