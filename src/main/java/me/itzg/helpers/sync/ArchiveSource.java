package me.itzg.helpers.sync;

import java.util.ArrayList;
import java.util.List;
import me.itzg.helpers.errors.InvalidParameterException;

/**
 * The source part of an archive entry, {@code archive[{path[;path...]}]}, where the optional paths select
 * directories within the archive.
 *
 * @param source local path or URL of the archive
 * @param directories normalized, slash-separated directories within the archive; empty for the whole archive
 */
record ArchiveSource(String source, List<String> directories) {

    private static final char PATHS_START = '{';
    private static final char PATHS_END = '}';
    private static final String PATHS_DELIMITER = ";";

    static ArchiveSource parse(String value) {
        final int start = value.lastIndexOf(PATHS_START);
        if (start < 0 || !value.endsWith(String.valueOf(PATHS_END))) {
            return new ArchiveSource(value, List.of());
        }

        final String paths = value.substring(start + 1, value.length() - 1);
        if (paths.isBlank()) {
            throw new InvalidParameterException("Archive paths within {} are empty: " + value);
        }

        final List<String> directories = new ArrayList<>();
        for (final String path : paths.split(PATHS_DELIMITER, -1)) {
            final String directory = normalizeDirectory(path.trim());
            for (final String other : directories) {
                if (contains(other, directory) || contains(directory, other)) {
                    throw new InvalidParameterException(
                        String.format("Archive paths '%s' and '%s' overlap: %s", other, directory, value));
                }
            }
            directories.add(directory);
        }

        if (directories.size() == 1 && directories.get(0).isEmpty()) {
            return new ArchiveSource(value.substring(0, start), List.of());
        }
        return new ArchiveSource(value.substring(0, start), List.copyOf(directories));
    }

    /**
     * @return whether {@code inner} is {@code outer} or within it
     */
    private static boolean contains(String outer, String inner) {
        return outer.isEmpty() || inner.equals(outer) || inner.startsWith(outer + "/");
    }

    private static String normalizeDirectory(String path) {
        if (path.isEmpty()) {
            throw new InvalidParameterException("Archive path is empty");
        }
        if (path.startsWith("/") || path.startsWith("\\") || path.matches("^[A-Za-z]:.*")) {
            throw new InvalidParameterException("Archive path must be relative: " + path);
        }

        final List<String> parts = new ArrayList<>();
        for (final String part : path.split("[/\\\\]")) {
            if (part.equals("..")) {
                throw new InvalidParameterException("Archive path must not contain '..': " + path);
            }
            if (!part.isEmpty() && !part.equals(".")) {
                parts.add(part);
            }
        }
        return String.join("/", parts);
    }
}
