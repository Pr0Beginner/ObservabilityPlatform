package org.zmy.observabilityplatform.incident.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
public final class AnomalyDetectionSettings {
    private final int errorThreshold;
    private final int minimumRequests;
    private final int minimumErrorCodeCount;
    private final double requestSpikeRatio;
    private final double requestDropRatio;
    private final double failureRateThreshold;
    private final double errorCodeRateThreshold;
    private final double baselineMultiplier;
    private final int comparisonPeriodMinutes;
    private final int recoveryWindows;

    public AnomalyDetectionSettings(int errorThreshold, int minimumRequests, int minimumErrorCodeCount,
                                    double requestSpikeRatio, double requestDropRatio,
                                    double failureRateThreshold, double errorCodeRateThreshold,
                                    double baselineMultiplier, int comparisonPeriodMinutes,
                                    int recoveryWindows) {
        if (errorThreshold < 1 || minimumRequests < 1 || minimumErrorCodeCount < 1
                || comparisonPeriodMinutes < 1 || recoveryWindows < 1) {
            throw new IllegalArgumentException("Anomaly counts, comparison period and recovery windows must be positive");
        }
        if (!Double.isFinite(requestSpikeRatio) || requestSpikeRatio <= 1
                || !Double.isFinite(requestDropRatio) || requestDropRatio < 0 || requestDropRatio >= 1
                || !isRate(failureRateThreshold) || !isRate(errorCodeRateThreshold)
                || !Double.isFinite(baselineMultiplier) || baselineMultiplier <= 1) {
            throw new IllegalArgumentException("Invalid anomaly ratio configuration");
        }
        this.errorThreshold = errorThreshold;
        this.minimumRequests = minimumRequests;
        this.minimumErrorCodeCount = minimumErrorCodeCount;
        this.requestSpikeRatio = requestSpikeRatio;
        this.requestDropRatio = requestDropRatio;
        this.failureRateThreshold = failureRateThreshold;
        this.errorCodeRateThreshold = errorCodeRateThreshold;
        this.baselineMultiplier = baselineMultiplier;
        this.comparisonPeriodMinutes = comparisonPeriodMinutes;
        this.recoveryWindows = recoveryWindows;
    }

    public static AnomalyDetectionSettings defaults() {
        return new AnomalyDetectionSettings(3, 20, 5, 2.0, 0.5,
                0.1, 0.05, 2.0, 1_440, 2);
    }

    public Instant baselineWindowOf(Instant currentWindow) {
        return Objects.requireNonNull(currentWindow, "currentWindow must not be null")
                .minus(Duration.ofMinutes(comparisonPeriodMinutes));
    }

    public boolean isRepeatedError(long count) {
        return count >= errorThreshold;
    }

    public boolean isNoTraffic(long currentTotal, long baselineTotal) {
        return baselineTotal >= minimumRequests && currentTotal == 0;
    }

    public boolean isRequestVolumeSpike(long currentTotal, long baselineTotal) {
        return baselineTotal >= minimumRequests && currentTotal >= minimumRequests
                && ratio(currentTotal, baselineTotal) >= requestSpikeRatio;
    }

    public boolean isRequestVolumeDrop(long currentTotal, long baselineTotal) {
        return baselineTotal >= minimumRequests && currentTotal > 0
                && ratio(currentTotal, baselineTotal) <= requestDropRatio;
    }

    public boolean isFailureRateSpike(long currentFailures, long currentTotal,
                                      long baselineFailures, long baselineTotal) {
        double currentRate = rate(currentFailures, currentTotal);
        double baselineRate = rate(baselineFailures, baselineTotal);
        return currentTotal >= minimumRequests && currentRate >= failureRateThreshold
                && increasedFromBaseline(currentRate, baselineRate);
    }

    public boolean isErrorCodeCountSpike(long currentErrors, long baselineErrors) {
        return currentErrors >= minimumErrorCodeCount
                && increasedFromBaseline(currentErrors, baselineErrors);
    }

    public boolean isErrorCodeRateSpike(long currentErrors, long currentTotal,
                                        long baselineErrors, long baselineTotal) {
        double currentRate = rate(currentErrors, currentTotal);
        double baselineRate = rate(baselineErrors, baselineTotal);
        return currentTotal >= minimumRequests && currentErrors >= minimumErrorCodeCount
                && currentRate >= errorCodeRateThreshold
                && increasedFromBaseline(currentRate, baselineRate);
    }

    public double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private boolean increasedFromBaseline(double current, double baseline) {
        return baseline == 0 ? current > 0 : current >= baseline * baselineMultiplier;
    }

    private static double ratio(long current, long baseline) {
        return baseline == 0 ? 0 : (double) current / baseline;
    }

    private static boolean isRate(double value) {
        return Double.isFinite(value) && value > 0 && value <= 1;
    }
}
