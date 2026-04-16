package me.itzg.helpers.libraries;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum LibraryListPaths {
    PAPER("META-INF/libraries.list"),
    FORGE("bootstrap-shim.list");

    private String PATH;
}