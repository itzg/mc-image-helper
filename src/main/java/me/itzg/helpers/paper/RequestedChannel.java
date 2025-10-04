package me.itzg.helpers.paper;

import lombok.Getter;
import lombok.ToString;
import me.itzg.helpers.paper.model.Channel;

@ToString
@Getter
public enum RequestedChannel {
    DEFAULT(Channel.STABLE, Channel.RECOMMENDED),
    EXPERIMENTAL(Channel.ALPHA, Channel.BETA),
    ALPHA(Channel.ALPHA);

    private final Channel[] mappedTo;

    RequestedChannel(Channel... mappedTo) {
        this.mappedTo = mappedTo;
    }
}
