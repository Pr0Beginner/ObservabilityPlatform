package org.zmy.observabilityplatform.logging.application.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogCursorCodecTest {
    private final LogCursorCodec codec = new LogCursorCodec();

    @Test
    void encodesAndDecodesAnOpaqueCursor() {
        LogCursor cursor = new LogCursor(1_789_710_600_000L, "log-42");

        String encoded = codec.encode(cursor);

        assertThat(encoded).doesNotContain(":");
        assertThat(codec.decode(encoded)).isEqualTo(cursor);
        assertThat(codec.decode(null)).isNull();
    }

    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> codec.decode("not-a-valid-cursor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid log cursor");
    }
}
