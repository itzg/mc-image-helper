package me.itzg.helpers.forge;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Main class embedded into the trimmed Forge installer jar during tests.
 * The fixture only contains version.json, so the real installer cannot be executed.
 */
public class TrimmedForgeInstaller {

    public static void main(String[] args) throws Exception {
        System.out.println(
            "The server installed successfully, you should now be able to run the file forge-1.20.2-48.1.0.jar"
        );
        Files.write(Path.of("forge-1.20.2-48.1.0.jar"), new byte[0]);
    }
}
