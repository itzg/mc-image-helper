package me.itzg.helpers.get;

import java.util.List;
import lombok.Getter;
import lombok.ToString;
import me.itzg.helpers.errors.EmitsExitCode;

@EmitsExitCode(300)
@Getter @ToString
public class UnexpectedContentTypeException extends RuntimeException {

  private final String parsedContentType;
  private final List<String> expectedContentTypes;

  public UnexpectedContentTypeException(String parsedContentType, List<String> expectedContentTypes) {
    this.parsedContentType = parsedContentType;
    this.expectedContentTypes = expectedContentTypes;
  }

  @Override
  public String getMessage() {
    return String.format("Unexpected content type '%s', expected any of %s",
        parsedContentType,
        expectedContentTypes);
  }
}
