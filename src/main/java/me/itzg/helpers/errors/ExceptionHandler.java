package me.itzg.helpers.errors;

import java.net.UnknownHostException;
import java.time.Instant;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.McImageHelper;
import me.itzg.helpers.http.FailedRequestException;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.IExitCodeExceptionMapper;
import picocli.CommandLine.ParseResult;
import reactor.core.Exceptions;

@Slf4j
public class ExceptionHandler implements IExecutionExceptionHandler {

    private final McImageHelper mcImageHelper;

    public ExceptionHandler(McImageHelper mcImageHelper) {
        this.mcImageHelper = mcImageHelper;
    }

    @Override
    public int handleExecutionException(Exception e, CommandLine commandLine, ParseResult parseResult) {

        if (!mcImageHelper.isSilent()) {
            final UnknownHostException unknownHost = findCause(e, UnknownHostException.class);
            if (e instanceof InvalidParameterException) {
                if (((InvalidParameterException) e).isShowCauses()) {
                    logExceptionWithoutStacktrace(e, commandLine);
                }
                else {
                    log.error("Invalid parameter provided for '{}' command: {}", commandLine.getCommandName(), e.getMessage());
                }
                log.debug("Invalid parameter details", e);
            }
            else if (Exceptions.isMultiple(e)) {
                logCompositeExceptions(e, commandLine);
                log.debug("Composite Exception details", e);
            }
            else if (e instanceof FailedRequestException) {
                log.error("'{}' command failed. Version is {}: {}", commandLine.getCommandName(), McImageHelper.getVersion(), e.getMessage());
                log.debug("Failed request details", e);
            }
            else if (e instanceof RateLimitException) {
                logUnexpectedException(e, commandLine);
                final RateLimitException rle = (RateLimitException) e;
                final Instant delayUntil = rle.getDelayUntil();
                if (delayUntil != null) {
                    log.warn("Rate limit response recommends waiting until {}", delayUntil);
                }
            }
            else if (unknownHost != null) {
                log.error("'{}' command failed. Version is {}: {}: {}. Check network connectivity and DNS.",
                    commandLine.getCommandName(), McImageHelper.getVersion(),
                    unknownHost.getClass().getSimpleName(),
                    unknownHost.getMessage() != null ? unknownHost.getMessage().trim() : "unknown host"
                );
                log.debug("Unknown host details", e);
            }
            else {
                logUnexpectedException(e, commandLine);
            }
        }

        final IExitCodeExceptionMapper mapper = commandLine.getExitCodeExceptionMapper();
        return mapper != null ? mapper.getExitCode(e) : ExitCode.SOFTWARE;
    }

    /**
     * @return the first throwable in the causal chain of the given one that is of the given type, or null
     */
    static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        return null;
    }

    private static void logUnexpectedException(Exception e, CommandLine commandLine) {
        log.error("'{}' command failed. Version is {}",
            commandLine.getCommandName(),
            McImageHelper.getVersion(),
            e
        );
    }

    private static void logExceptionWithoutStacktrace(Exception e, CommandLine commandLine) {
        log.error("'{}' command failed. Version is {}: {}",
            commandLine.getCommandName(),
            McImageHelper.getVersion(),
            ExceptionDetailer.buildCausalMessages(e)
        );
    }

    private static void logCompositeExceptions(Exception e, CommandLine commandLine) {
        final String failures = Exceptions
            .unwrapMultipleExcludingTracebacks(e)
            .stream()
            .map(ExceptionDetailer::buildCausalMessages)
            .collect(Collectors.joining("; "));

        log.error("'{}' command failed. Version is {}: {}", commandLine.getCommandName(), McImageHelper.getVersion(), failures);
    }
}