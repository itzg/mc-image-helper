package me.itzg.helpers.errors;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Collectors;
import me.itzg.helpers.McImageHelper;
import me.itzg.helpers.TestLoggingAppender;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import reactor.core.Exceptions;

class ExceptionHandlerTest {

    @Test
    void logsUnknownHostWithoutStacktrace() {
        final List<ILoggingEvent> events = handle(new UnknownHostException("No such host is known (no-such-host.invalid)"));

        final List<ILoggingEvent> errors = eventsAt(events, Level.ERROR);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getFormattedMessage())
            .startsWith("'test' command failed. Version is ")
            .endsWith(": UnknownHostException: No such host is known (no-such-host.invalid)."
                + " Check network connectivity and DNS.");
        assertThat(errors.get(0).getThrowableProxy()).isNull();

        assertThat(eventsAt(events, Level.DEBUG))
            .anySatisfy(event -> assertThat(event.getThrowableProxy()).isNotNull());
    }

    @Test
    void logsUnknownHostWrappedByReactor() {
        final List<ILoggingEvent> events = handle(
            Exceptions.propagate(new UnknownHostException("Failed to resolve 'no-such-host.invalid' [A(1)] after 3 queries "))
        );

        final List<ILoggingEvent> errors = eventsAt(events, Level.ERROR);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getFormattedMessage())
            .endsWith(": UnknownHostException: Failed to resolve 'no-such-host.invalid' [A(1)] after 3 queries."
                + " Check network connectivity and DNS.");
        assertThat(errors.get(0).getThrowableProxy()).isNull();
    }

    @Test
    void keepsStacktraceForUnexpectedException() {
        final List<ILoggingEvent> events = handle(new IllegalStateException("boom"));

        final List<ILoggingEvent> errors = eventsAt(events, Level.ERROR);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getThrowableProxy()).isNotNull();
        assertThat(errors.get(0).getThrowableProxy().getMessage()).isEqualTo("boom");
    }

    private static List<ILoggingEvent> handle(Exception e) {
        final CommandLine commandLine = new CommandLine(new TestCommand());
        final ExceptionHandler handler = new ExceptionHandler(new McImageHelper());

        return TestLoggingAppender.captureLogs(ExceptionHandler.class, () ->
            assertThat(handler.handleExecutionException(e, commandLine, null))
                .isEqualTo(ExitCode.SOFTWARE)
        );
    }

    private static List<ILoggingEvent> eventsAt(List<ILoggingEvent> events, Level level) {
        return events.stream()
            .filter(event -> event.getLevel() == level)
            .collect(Collectors.toList());
    }

    @Command(name = "test")
    static class TestCommand implements Runnable {

        @Override
        public void run() {
        }
    }
}
