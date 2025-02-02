package me.itzg.helpers.forge;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@RequiredArgsConstructor
@ToString @EqualsAndHashCode
public class VersionPair {

    final String minecraft;
    final String forge;
    @Setter
    String variantOverride;
}
