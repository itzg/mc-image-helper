package me.itzg.helpers.errors;

import picocli.CommandLine.ExitCode;

@EmitsExitCode(ExitCode.USAGE)
public class InvalidParameterException extends RuntimeException {

  public InvalidParameterException(String message) {
    super(message);
  }

  public InvalidParameterException(String message, Throwable cause) {
    super(message, cause);
  }
}
