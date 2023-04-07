package me.itzg.helpers.errors;

@EmitsExitCode(200)
public class InvalidParameterException extends RuntimeException {

  public InvalidParameterException(String message) {
    super(message);
  }

  public InvalidParameterException(String message, Throwable cause) {
    super(message, cause);
  }
}
