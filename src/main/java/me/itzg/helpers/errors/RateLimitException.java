package me.itzg.helpers.errors;

import java.time.Instant;
import lombok.Getter;

@Getter
public class RateLimitException extends RuntimeException {

    private final Instant delayUntil;

    public RateLimitException(Instant delayUntil, String message, Throwable cause) {
        super(message, cause);
        this.delayUntil = delayUntil;
    }
}
