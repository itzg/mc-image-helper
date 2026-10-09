package me.itzg.helpers.files;

import com.sshtools.jini.Data;
import com.sshtools.jini.INI;
import com.sshtools.jini.INIParseException;
import com.sshtools.jini.INIReader;
import com.sshtools.jini.INIReader.DuplicateAction;
import com.sshtools.jini.INIReader.MultiValueMode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.itzg.helpers.errors.InvalidParameterException;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;

@Command(name = "ini-path", description = "Extracts a field from an INI file")
public class IniPathCommand implements Callable<Integer> {

    private String sectionKey;
    private String fieldKey;
    private Integer index;

    private static final String EXPRESSION_SYNTAX_DESC = "section/option, section/option[index], /option, /option[index]";
    private final static Pattern expressions = Pattern.compile(
            "(?<section>.+?)?/(?<key>[^\\[]+?)(\\[(?<index>\\d+)])?");

    @Parameters(paramLabel = "FILE", description = "An INI file to query")
    File iniFile;

    @Parameters(arity = "1", paramLabel = "REF", description = EXPRESSION_SYNTAX_DESC)
    String query;

    private void parseKeys(String query) {
        final Matcher matcher = expressions.matcher(query);

        if (!matcher.matches()) {
            throw new InvalidParameterException("Query expression is invalid. Should be " + EXPRESSION_SYNTAX_DESC);
        }

        this.sectionKey = matcher.group("section");
        this.fieldKey = matcher.group("key");
        final String indexStr = matcher.group("index");
        try {
            this.index = indexStr != null ? Integer.valueOf(indexStr) : null;
        } catch (NumberFormatException e) {
            throw new InvalidParameterException(
                "Index is too large: " + indexStr + ". Maximum supported index is " + Integer.MAX_VALUE + ".", e);
        }
    }

    @Override
    public Integer call() throws Exception {
        parseKeys(query);
        final INI ini = loadIni();
        System.out.println(resolveValue(ini));

        return ExitCode.OK;
    }

    private INI loadIni() {
        final Path path = iniFile.toPath();
        if (!Files.exists(path)) {
            throw new InvalidParameterException("File does not exist at: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new InvalidParameterException("Not a regular file: " + path);
        }

        try {
            return new INIReader.Builder()
                .withCaseSensitiveKeys()
                .withCaseSensitiveSections()
                .withoutNestedSections()
                .withMultiValueMode(MultiValueMode.REPEATED_KEY)
                .withDuplicateKeysAction(DuplicateAction.APPEND)
                .withDuplicateSectionAction(DuplicateAction.MERGE)
                .withoutInlineComments()
                .withoutStringQuoting()
                .build()
                .read(path);
        } catch (INIParseException e) {
            throw new InvalidParameterException("Invalid INI file at: " + path + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new InvalidParameterException("Unable to read INI file at: " + path + ": " + e.getMessage(), e);
        }
    }

    private String resolveValue(INI ini) {
        final Data data = sectionKey != null
            ? ini.sectionOr(sectionKey).orElseThrow(() -> new InvalidParameterException(
                "Section not found: " + sectionKey + " in INI: " + iniFile.toPath()))
            : ini;
        final String[] values = data.getAllOr(fieldKey)
            .orElseThrow(() -> new InvalidParameterException(
                "Field not found: " + fieldKey + " in section: "
                    + (sectionKey != null ? sectionKey : "global") + " in INI: " + iniFile.toPath()));
        if (values.length == 0) {
            throw new InvalidParameterException(
                "Field has no values for query: " + query + " in INI: " + iniFile.toPath());
        }

        // Unindexed lookups return the last value, matching ini4j.
        final int valueIndex = index != null ? index : values.length - 1;
        if (valueIndex >= values.length) {
            throw new InvalidParameterException(
                "Index out of range: " + valueIndex + " for query: " + query
                    + ". Valid indexes: 0-" + (values.length - 1) + " in INI: " + iniFile.toPath());
        }
        return values[valueIndex];
    }
}