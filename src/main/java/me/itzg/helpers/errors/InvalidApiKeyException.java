package me.itzg.helpers.errors;

import picocli.CommandLine.ExitCode;

@EmitsExitCode(ExitCode.USAGE)
public class InvalidApiKeyException extends InvalidParameterException {

    public InvalidApiKeyException(String message) {
        super(message);
    }

    public InvalidApiKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
