package me.itzg.helpers.logger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ANSIConstants;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;

public class CustomHighlight extends ForegroundCompositeConverterBase<ILoggingEvent> {

    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        Level level = event.getLevel();
        switch (level.toInt()) {
        case Level.ERROR_INT:
            return ANSIConstants.BOLD + ANSIConstants.RED_FG; // same as default color scheme
        case Level.WARN_INT:
            return ANSIConstants.YELLOW_FG;// same as default color scheme
        case Level.INFO_INT:
        default:
            return ANSIConstants.DEFAULT_FG;
        }
    }

}