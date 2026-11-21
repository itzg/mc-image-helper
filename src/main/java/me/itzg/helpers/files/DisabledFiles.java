package me.itzg.helpers.files;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Launchers and users commonly disable a mod or plugin by renaming {@code example.jar} to
 * {@code example.jar.disabled}. A disabled file is still considered installed, so it isn't downloaded again.
 */
public class DisabledFiles {

    public static final String SUFFIX = ".disabled";

    private DisabledFiles() {
    }

    /**
     * @return the path of the disabled variant of the given file, such as {@code mods/example.jar.disabled}
     */
    public static Path disabledVariant(Path file) {
        return file.resolveSibling(file.getFileName().toString() + SUFFIX);
    }

    /**
     * @return true if the file exists as given or in its other state, enabled or disabled
     */
    public static boolean existsEnabledOrDisabled(Path file) {
        if (Files.exists(file)) {
            return true;
        }
        final String filename = file.getFileName().toString();
        if (filename.endsWith(SUFFIX) && filename.length() > SUFFIX.length()) {
            return Files.exists(file.resolveSibling(filename.substring(0, filename.length() - SUFFIX.length())));
        }
        return Files.exists(disabledVariant(file));
    }
}
