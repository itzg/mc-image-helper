package me.itzg.helpers.hangar;

import lombok.Data;
import me.itzg.helpers.errors.InvalidParameterException;
import org.jetbrains.annotations.Nullable;

@Data
public class ProjectRef {
    private final String slugOrId;
    /**
     * When null, the latest version of the requested channel is selected
     */
    @Nullable
    private final String versionName;
    /**
     * When true, this project is skipped with a warning when it is not available.
     * Marked by appending {@code ?} to the project slug/ID, e.g. {@code Maintenance?}
     */
    private final boolean optional;

    public static ProjectRef parse(String projectRef) {
        final String[] parts = projectRef.split(":", 2);

        final String slugOrId = parts[0].endsWith("?") ? parts[0].substring(0, parts[0].length() - 1) : parts[0];
        if (slugOrId.isEmpty()) {
            throw new InvalidParameterException("Invalid project reference: " + projectRef);
        }

        return new ProjectRef(
            slugOrId,
            parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null,
            parts[0].endsWith("?")
        );
    }
}
