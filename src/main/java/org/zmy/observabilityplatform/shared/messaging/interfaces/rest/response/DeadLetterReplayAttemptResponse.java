package org.zmy.observabilityplatform.shared.messaging.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterReplayAttempt;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterReplayAttemptResponse {
    private String id;
    private String deadLetterId;
    private String status;
    private Instant startedAt;
    private Instant completedAt;
    private String failureReason;

    public static DeadLetterReplayAttemptResponse from(DeadLetterReplayAttempt attempt) {
        return new DeadLetterReplayAttemptResponse(attempt.getId(), attempt.getDeadLetterId(),
                attempt.getStatus().name(), attempt.getStartedAt(), attempt.getCompletedAt(),
                attempt.getFailureReason());
    }
}
