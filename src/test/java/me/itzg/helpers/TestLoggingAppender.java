package me.itzg.helpers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

public class TestLoggingAppender extends AppenderBase<ILoggingEvent> {

  private static final List<ILoggingEvent> events = new ArrayList<>();

  public static synchronized List<ILoggingEvent> captureLogs(Class<?> loggerClass, Runnable action) {
    reset();
    final Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
    final TestLoggingAppender appender = new TestLoggingAppender();
    appender.start();
    final Level previousLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(appender);
    final List<ILoggingEvent> capturedEvents = new ArrayList<>();
    try {
      action.run();
    }
    finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
      appender.stop();
      capturedEvents.addAll(events);
      reset();
    }

    return capturedEvents;
  }

  @Override
  protected void append(ILoggingEvent e) {
    events.add(e);
  }

  public static synchronized List<ILoggingEvent> getEvents() {
    return events;
  }

  public static synchronized void reset() {
    events.clear();
  }
}
