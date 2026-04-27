package me.itzg.helpers.libraries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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

        final List<String> requiredLibraries;
        try {
            requiredLibraries = readJarLibraries(serverJar, libraryListPath);
        } catch (Exception e) {
            log.warn("Failed to read server jar libraries", e);
            return;
        }

        final List<String> installedLibraries;


        if (!Files.isDirectory(libraryFolder)) {
            log.debug("Failed to read library folder {}", libraryFolder);
        }

        try {
            installedLibraries = readInstalledLibraries(libraryFolder);
        } catch (IOException e) {
            log.warn("Failed to read installed libraries", e);
            return;
        }

        final List<String> oldLibraries = compareInstalledRequiredLibraries(installedLibraries, requiredLibraries);

        if (oldLibraries.isEmpty()) {
            return;
        }

        log.info("Removing {} deprecated installed libraries", String.valueOf(oldLibraries.size()));

        for (String s : oldLibraries) {
            try {
                Files.delete(libraryFolder.resolve(s));
            } catch (Exception e) {
                log.warn("Failed to delete library {}", s, e);
            }

        }

        deleteEmptyDirectories(libraryFolder);
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
        final List<String> libs;

        try (JarFile serverJar = new JarFile(jarFile.toString());) {

            JarEntry libraryList = serverJar.getJarEntry(libraryListPath.getPath());

            if (libraryList == null) {
                throw new IOException("Failed to read library list for server jar");
            }

            try (
                    InputStream is = serverJar.getInputStream(libraryList);
                    InputStreamReader isr = new InputStreamReader(is);
                    BufferedReader br = new BufferedReader(isr);) {

                // Libraries List file contains rows of entries, split into three columns
                // <sha256> <groupId:artifactId:version> <path>, for example
                // 5d803eb7348a27468c6172daef2f0d77260dd63cde377ebfa73e36789ae8fcee com.mojang:logging:1.6.11 com/mojang/logging/1.6.11/logging-1.6.11.jar
                // 7e9941bbdcca244d878ea95bfff788fd9ba6a65af757f24be6c632930d61c7ed com.mysql:mysql-connector-j:9.2.0 com/mysql/mysql-connector-j/9.2.0/mysql-connector-j-9.2.0.jar
                //
                // When the libraries get expanded, they simply extract the jars referenced in
                // the path column to ./libraries/{path}
                //
                // Reader iterates through each line, trimming whitespace, then selecting the
                // third column, fetching the path of
                // the jar

                libs = br.lines()
                        .map(String::trim)
                        .map(s -> s.split("\\s+"))
                        .filter(parts -> parts.length > 1)
                        .map(parts -> parts[2])
                        .collect(Collectors.toList());

            }

        } 

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
                .filter(path -> path.getFileName().endsWith(".jar"))
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
        HashSet<String> installedNotRequired = new HashSet<String>(installed);

        installedNotRequired.removeAll(required);

        return installedNotRequired.stream().collect(Collectors.toList());
    }

    /**
     * Reads all files inside directory, deletes empty subdirectories
     *
     * @param root Root directory to search inside
     */
    private void deleteEmptyDirectories(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder())
                    .filter(Files::isDirectory)
                    .forEach(dir -> {

                        try (Stream<Path> files = Files.list(dir)) {
                            if (files.findAny().isEmpty()) {
                                Files.delete(dir);
                            }
                        } catch (IOException e) {
                            log.error("Failed to delete directory {}", dir, e);
                        }
                    });

        } catch (Exception e) {
            log.error("Failed to walk directory {}", root, e);
        }
    }
}