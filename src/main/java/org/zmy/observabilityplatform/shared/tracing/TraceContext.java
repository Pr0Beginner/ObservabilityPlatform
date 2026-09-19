package org.zmy.observabilityplatform.shared.tracing;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@ToString
@EqualsAndHashCode
public final class TraceContext {
    private static final Pattern TRACE_PARENT = Pattern.compile(
            "^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String flags;

    private TraceContext(String traceId, String spanId, String parentSpanId, String flags) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.flags = flags;
    }

    public static TraceContext root() {
        return new TraceContext(randomHex(16), randomHex(8), null, "01");
    }

    public static Optional<TraceContext> continueFrom(String traceParent) {
        if (traceParent == null) {
            return Optional.empty();
        }
        Matcher matcher = TRACE_PARENT.matcher(traceParent.trim().toLowerCase());
        if (!matcher.matches() || isZero(matcher.group(1)) || isZero(matcher.group(2))) {
            return Optional.empty();
        }
        return Optional.of(new TraceContext(matcher.group(1), randomHex(8), matcher.group(2), matcher.group(3)));
    }

    public TraceContext child() {
        return new TraceContext(traceId, randomHex(8), spanId, flags);
    }

    public String traceParent() {
        return "00-" + traceId + "-" + spanId + "-" + flags;
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        String encoded;
        do {
            RANDOM.nextBytes(value);
            encoded = HexFormat.of().formatHex(value);
        } while (isZero(encoded));
        return encoded;
    }

    private static boolean isZero(String value) {
        return value.chars().allMatch(character -> character == '0');
    }
}
