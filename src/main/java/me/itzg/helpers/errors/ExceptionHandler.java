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
            final UnknownHostException unknownHost = ExceptionDetailer.findCause(e, UnknownHostException.class);
            if (e instanceof InvalidParameterException) {
                if (((InvalidParameterException) e).isShowCauses()) {
                    logErrorWithMessage(commandLine, ExceptionDetailer.buildCausalMessages(e));
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
                logErrorWithMessage(commandLine, e.getMessage());
                log.debug("Failed request details", e);
            }
            else if (e instanceof final RateLimitException rle) {
                logUnexpectedException(e, commandLine);
                final Instant delayUntil = rle.getDelayUntil();
                if (delayUntil != null) {
                    log.warn("Rate limit response recommends waiting until {}", delayUntil);
                }
            }
            else if (unknownHost != null) {
                logErrorWithMessage(commandLine,
                    String.format(
                        "%s: %s. Check network connectivity and DNS.",
                        unknownHost.getClass().getSimpleName(),
                        unknownHost.getMessage() != null ? unknownHost.getMessage().trim() : "unknown host"
                    )
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

    private static void logUnexpectedException(Exception e, CommandLine commandLine) {
        log.error("'{}' command failed. Version is {}",
            commandLine.getCommandName(),
            McImageHelper.getVersion(),
            e
        );
    }

    private static void logErrorWithMessage(CommandLine commandLine, String message) {
        log.error("'{}' command failed. Version is {}: {}",
            commandLine.getCommandName(),
            McImageHelper.getVersion(),
            message
        );
    }

    private static void logCompositeExceptions(Exception e, CommandLine commandLine) {
        final String failures = Exceptions
            .unwrapMultipleExcludingTracebacks(e)
            .stream()
            .map(ExceptionDetailer::buildCausalMessages)
            .collect(Collectors.joining("; "));

        logErrorWithMessage(commandLine, failures);
    }
}