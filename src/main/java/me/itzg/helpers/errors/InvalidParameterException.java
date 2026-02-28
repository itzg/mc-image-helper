package me.itzg.helpers.errors;

import lombok.Getter;
import picocli.CommandLine.ExitCode;

@EmitsExitCode(ExitCode.USAGE)
public class InvalidParameterException extends RuntimeException {

    /**
     * Indicates if the causal chain should be displayed by {@link ExceptionHandler} during non-debug logging.
     */
    @Getter
    final boolean showCauses;

    public InvalidParameterException(String message) {
        this(message, false);
    }

    public InvalidParameterException(String message, boolean showCauses) {
        super(message);
        this.showCauses = showCauses;
    }

    public InvalidParameterException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public InvalidParameterException(String message, Throwable cause, boolean showCauses) {
        super(message, cause);
        this.showCauses = showCauses;
    }
}
