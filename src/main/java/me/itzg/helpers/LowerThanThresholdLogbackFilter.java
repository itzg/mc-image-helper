package me.itzg.helpers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import lombok.ToString;

/**
 * Provides the opposite filtering of {@link ch.qos.logback.classic.filter.ThresholdFilter}
 */
@ToString
public class LowerThanThresholdLogbackFilter extends Filter<ILoggingEvent> {

  /**
   * Logging events at or above this level will be filtered away.
   */
  private Level level = Level.WARN;

  @SuppressWarnings("unused")
  public void setLevel(String level) {
    this.level = Level.toLevel(level);
  }

  @Override
  public FilterReply decide(ILoggingEvent event) {
    if (!isStarted()) {
      return FilterReply.NEUTRAL;
    }

    return event.getLevel().isGreaterOrEqual(level) ?
        FilterReply.DENY : FilterReply.NEUTRAL;
  }
}
