package org.zmy.observabilityplatform.retention.infrastructure.configuration;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.zmy.observabilityplatform.retention.application.model.DataRetentionPolicy;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.retention")
public class DataRetentionProperties {
    @Min(1)
    private int metricWindowDays = 7;
    @Min(0)
    private int metricBaselineSafetyHours = 24;
    @Min(1)
    private int notificationDays = 90;
    @Min(1)
    private int replayedDeadLetterDays = 30;
    @Min(1)
    private int unresolvedDeadLetterDays = 180;
    @Min(1)
    private int auditDays = 365;
    @Min(1)
    private int batchSize = 500;
    @Min(1)
    private int maxBatchesPerRun = 20;

    public DataRetentionPolicy toPolicy() {
        return new DataRetentionPolicy(Duration.ofDays(metricWindowDays),
                Duration.ofHours(metricBaselineSafetyHours), Duration.ofDays(notificationDays),
                Duration.ofDays(replayedDeadLetterDays), Duration.ofDays(unresolvedDeadLetterDays),
                Duration.ofDays(auditDays), batchSize, maxBatchesPerRun);
    }
}
