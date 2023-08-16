package me.itzg.helpers.errors;

import picocli.CommandLine.ExitCode;
import picocli.CommandLine.IExitCodeExceptionMapper;

public class ExitCodeMapper implements IExitCodeExceptionMapper {

  @Override
  public int getExitCode(Throwable exception) {
    if (exception instanceof ExitCodeProvider) {
      return ((ExitCodeProvider) exception).exitCode();
    }

    final EmitsExitCode annotation = exception.getClass().getAnnotation(EmitsExitCode.class);
    if (annotation != null) {
      return annotation.value();
    }

    return ExitCode.SOFTWARE;
  }
}
