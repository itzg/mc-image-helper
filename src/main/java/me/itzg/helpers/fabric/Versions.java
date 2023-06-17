package me.itzg.helpers.fabric;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
@EqualsAndHashCode(callSuper = false)
@ToString
public class Versions extends Origin {

    private String game;
    private String loader;
    @EqualsAndHashCode.Exclude
    private String installer;

}
