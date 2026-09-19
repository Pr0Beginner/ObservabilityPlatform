package org.zmy.observabilityplatform.bootstrap.configuration;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class DatabaseMigrationConfiguration {
    @Bean(initMethod = "migrate")
    @ConditionalOnProperty(name = "app.migration.enabled", havingValue = "true", matchIfMissing = true)
    Flyway flyway(
            @Value("${app.migration.jdbc-url}") String jdbcUrl,
            @Value("${app.migration.username}") String username,
            @Value("${app.migration.password}") String password,
            @Value("${app.migration.baseline-on-migrate:false}") boolean baselineOnMigrate) {
        return Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion("1")
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load();
    }
}
