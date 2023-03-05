package me.itzg.helpers.errors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)

/**
 * Used to annotate an {@link Exception} class with a status code to produce.
 */
public @interface EmitsExitCode {

  /**
   * @return typically a value of 200 or greater to indicate a custom, application exit code
   * and 300 or greater for subcommand specific exceptions.
   */
  int value();
}
