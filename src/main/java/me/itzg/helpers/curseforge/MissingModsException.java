package me.itzg.helpers.curseforge;

import lombok.Getter;

import java.util.List;

public class MissingModsException extends RuntimeException {
    @Getter
    private final List<PathWithInfo> needsDownload;

    public MissingModsException(List<PathWithInfo> needsDownload) {

        this.needsDownload = needsDownload;
    }
}
