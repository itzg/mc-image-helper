package me.itzg.helpers.errors;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.McImageHelper;
import me.itzg.helpers.http.FailedRequestException;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.IExitCodeExceptionMapper;
import picocli.CommandLine.ParseResult;

@Slf4j
public class ExceptionHandler implements IExecutionExceptionHandler {

    private final McImageHelper mcImageHelper;

    public ExceptionHandler(McImageHelper mcImageHelper) {
        this.mcImageHelper = mcImageHelper;
    }

    @Override
    public int handleExecutionException(Exception e, CommandLine commandLine, ParseResult parseResult) {

        if (!mcImageHelper.isSilent()) {
            if (e instanceof InvalidParameterException) {
                log.error("Invalid parameter provided for '{}' command: {}", commandLine.getCommandName(), e.getMessage());
                log.debug("Invalid parameter details", e);
            }
            else if (e instanceof FailedRequestException) {
                logUnexpectedException(e, commandLine);
                log.debug("Failed request details: {}", e.toString());
            }
            else if (e instanceof RateLimitException) {
                logUnexpectedException(e, commandLine);
                final RateLimitException rle = (RateLimitException) e;
                final Instant delayUntil = rle.getDelayUntil();
                if (delayUntil != null) {
                    log.warn("Rate limit response recommends waiting until {}", delayUntil);
                }
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
}
