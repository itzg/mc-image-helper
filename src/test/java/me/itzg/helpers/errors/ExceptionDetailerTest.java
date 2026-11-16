package me.itzg.helpers.errors;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class ExceptionDetailerTest {

    @Test
    void buildsCausalMessages() {
        final RuntimeException error = new RuntimeException("outer", new UnknownHostException("inner"));

        assertThat(ExceptionDetailer.buildCausalMessages(error))
            .isEqualTo("RuntimeException: outer; UnknownHostException: inner");
    }

    @Test
    void findsCauseByType() {
        final UnknownHostException cause = new UnknownHostException("inner");
        final RuntimeException error = new RuntimeException("outer", cause);

        assertThat(ExceptionDetailer.findCause(error, UnknownHostException.class)).isSameAs(cause);
        assertThat(ExceptionDetailer.findCause(error, IllegalArgumentException.class)).isNull();
    }

    @Test
    void handlesNullThrowable() {
        assertThat(ExceptionDetailer.buildCausalMessages(null)).isEmpty();
        assertThat(ExceptionDetailer.findCause(null, Throwable.class)).isNull();
    }
}
