package org.zmy.observabilityplatform.shared.messaging.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterMessageResponse {
    private String id;
    private String originalTopic;
    private String messageKey;
    private String status;
    private String failureType;
    private String failureReason;
    private int sourcePartition;
    private long sourceOffset;
    private Instant failedAt;
    private Instant replayedAt;

    public static DeadLetterMessageResponse from(DeadLetterMessage message) {
        return new DeadLetterMessageResponse(message.getId(), message.getOriginalTopic(), message.getMessageKey(),
                message.getStatus().name(), message.getFailureType(), message.getFailureReason(),
                message.getSourcePartition(), message.getSourceOffset(),
                message.getFailedAt(), message.getReplayedAt());
    }
}
