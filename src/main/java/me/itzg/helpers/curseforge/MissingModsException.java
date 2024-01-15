package me.itzg.helpers.curseforge;

import java.util.List;
import lombok.Getter;

@Getter
public class MissingModsException extends RuntimeException {
    private final List<PathWithInfo> needsDownload;

    public MissingModsException(List<PathWithInfo> needsDownload) {

        this.needsDownload = needsDownload;
    }
}
