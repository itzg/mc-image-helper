package me.itzg.helpers.libraries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LibraryCleaner
 */
@Slf4j
@AllArgsConstructor
public class LibraryCleaner {
    private static String INSTALLED_LIBRARY_FOLDER = "libraries";

    // @TODO I believe all server jar types save libs to "/libraries" however will
    // have to verify when implementing for further types

    private final LibraryListPaths libraryListPath;
    private final Path serverJar;
    private final Path workingFolder;
    private final Path libraryFolder;

    public LibraryCleaner(Path serverJar, LibraryListPaths libraryListPath) {
        this.serverJar = serverJar;
        this.libraryListPath = libraryListPath;

        this.workingFolder = serverJar.getParent();
        this.libraryFolder = workingFolder.resolve(INSTALLED_LIBRARY_FOLDER);
    }

    /**
     * cleanLibraries reads currently installed libraries against required libraries
     * for Server Jar, removes non-required libraries
     */
    public void cleanLibraries() {
        List<String> requiredLibraries, installedLibraries, oldLibraries;

        try {
            requiredLibraries = readJarLibraries(serverJar, libraryListPath);
        } catch (Exception e) {
            log.debug("Failed to read server jar libraries", e);
            return;
        }

        try {
            installedLibraries = readInstalledLibraries(libraryFolder);
        } catch (Exception e) {

            // Will fail on fresh install as no libs installed
            // Therefore no /library created
            log.debug("Failed to read installed libraries {}", e);
            return;
        }

        oldLibraries = compareInstalledRequiredLibraries(installedLibraries, requiredLibraries);

        if (oldLibraries.isEmpty()) {
            return;
        }

        log.info("Removing " + String.valueOf(oldLibraries.size()) + " deprecated installed libraries");

        for (String s : oldLibraries) {
            try {
                Files.delete(libraryFolder.resolve(s));
                deleteEmptyParentDirectories(libraryFolder, libraryFolder.resolve(s));
            } catch (Exception e) {
                log.debug("Failed to delete library {} {}", s, e);
            }

        }
    }

    /**
     * Reads required libraries from inside Jarfile Manifest
     * 
     * @param jarFile         Path to server Jar
     * @param libraryListPath Path to library list inside jar
     * @return List of all libraries required by server jar
     * @throws IOException
     */
    private List<String> readJarLibraries(Path jarFile, LibraryListPaths libraryListPath) throws IOException {
        List<String> libs = new ArrayList<String>();

        JarFile serverJar = new JarFile(jarFile.toString());
        JarEntry libraryList = serverJar.getJarEntry(libraryListPath.getPATH());

        InputStream is = serverJar.getInputStream(libraryList);
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);

        libs = br.lines()
                .map(String::trim)
                .map(s -> s.split("\\s+"))
                .filter(parts -> parts.length > 1)
                .map(parts -> parts[2])
                .collect(Collectors.toList());

        serverJar.close();
        return libs;
    }

    /**
     * @param libraryFolder Folder where libraries are currently installed
     * @return returns list of all individual library Jars currently installed
     * @throws IOException
     */
    private List<String> readInstalledLibraries(Path libraryFolder) throws IOException {
        List<String> libs = new ArrayList<String>();

        libs = Files.walk(libraryFolder)
                .filter(Files::isRegularFile)
                .map(libraryFolder::relativize)
                .map(Path::toString)
                .collect(Collectors.toList());

        return libs;
    }

    /**
     * Takes the differance of currently installed and required libraries
     * 
     * @param installed List of currently installed libraries
     * @param required  List of libraries required by Server Jar
     * @return List of currently installed libraries not required by the Server Jar
     */
    private List<String> compareInstalledRequiredLibraries(List<String> installed, List<String> required) {
        List<String> installedNotRequired = new ArrayList<String>(installed);

        for (String s : installed) {
            if (required.contains(s)) {
                installedNotRequired.remove(s);
            }
        }

        return installedNotRequired;
    }

    /**
     * Takes currently delete file, moves upwards in filesystem and deletes any
     * empty folders
     * 
     * @param baseRoot    Root folder where libraries are installed
     * @param deletedFile Path to recently deleted file
     */
    private void deleteEmptyParentDirectories(Path baseRoot, Path deletedFile) {
        Path current = deletedFile.getParent();
        while (current != null && !current.equals(baseRoot)) {
            try (Stream<Path> entries = Files.list(current)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
            } catch (IOException e) {
                log.debug("Failed to inspect directory {}", current, e);
                return;
            }
            try {
                Files.delete(current);
            } catch (IOException e) {
                log.debug("Failed to delete empty directory {}", current, e);
                return;
            }
            current = current.getParent();
        }
    }
}