package me.itzg.helpers.modrinth;

import lombok.Getter;
import lombok.ToString;
import me.itzg.helpers.modrinth.model.Project;
import org.jetbrains.annotations.Nullable;

@Getter
@ToString
public class NoFilesAvailableException extends RuntimeException {

    private final Project project;
    private final Loader loader;
    private final String gameVersion;

    public NoFilesAvailableException(Project project, @Nullable Loader loader, String gameVersion) {
        super(String.format("No files are available for the project '%s' for loader %s and Minecraft version %s",
            project.getTitle(), loader, gameVersion
        ));

        this.project = project;
        this.loader = loader;
        this.gameVersion = gameVersion;
    }
}
