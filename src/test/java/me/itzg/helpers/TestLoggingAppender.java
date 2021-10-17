package me.itzg.helpers;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;

public class TestLoggingAppender extends AppenderBase<LoggingEvent> {

  private static final List<LoggingEvent> events = new ArrayList<>();

  @Override
  protected void append(LoggingEvent e) {
    events.add(e);
  }

  public static List<LoggingEvent> getEvents() {
    return events;
  }

  public static void reset() {
    events.clear();
  }
}
