package me.itzg.helpers.files;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.itzg.helpers.errors.InvalidParameterException;
import org.ini4j.Config;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "ini-path", description = "Extracts a field from an INI file")
public class IniPathCommand implements Callable<Integer> {

    private static final String EXPRESSION_SYNTAX_DESC = "section/option, section/option[index], /option, /option[index]";
    private final static Pattern expressions = Pattern.compile(
        "(?<section>.+?)?/(?<key>[^\\[]+?)(\\[(?<index>\\d+)])?"
    );

    @Option(names = "--file", paramLabel = "FILE", description = "An INI file to query. If not set, reads stdin")
    File iniFile;

    @Parameters(arity = "1",
        paramLabel = "ref",
        description = EXPRESSION_SYNTAX_DESC)
    String query;


    @Override
    public Integer call() throws Exception {
        final Matcher matcher = expressions.matcher(query);
        if (!matcher.matches()) {
            throw new InvalidParameterException("Query expression is invalid. Should be " +  EXPRESSION_SYNTAX_DESC);
        }

        final Ini ini = new Ini();
        final Config iniCfg = Config.getGlobal().clone();
        iniCfg.setGlobalSection(true);
        ini.setConfig(iniCfg);
        ini.load(iniFile);

        final String sectionKey = matcher.group("section");
        final String fieldKey = matcher.group("key");
        final String indexStr = matcher.group("index");

        final Section section = ini.get(sectionKey != null ? sectionKey : Config.DEFAULT_GLOBAL_SECTION_NAME);
        if (section == null) {
            throw new InvalidParameterException("Section not found: " + sectionKey);
        }

        if (indexStr == null) {
            final String value = section.fetch(fieldKey);
            if (value == null) {
                throw new InvalidParameterException("Field not found: " + fieldKey + " in section: " + sectionKey);
            }

            System.out.println(value);
        }
        else {
            final int index = Integer.parseInt(indexStr);
            if (index <= 0) {
                throw new InvalidParameterException("Index must be greater than 0.");
            }
            final String value = section.fetch(fieldKey, index);
            if (value == null) {
                throw new InvalidParameterException("Field not found: " + fieldKey + " with index: " + index + " in section: " + sectionKey);
            }

            System.out.println(value);
        }

        return ExitCode.OK;
    }

}
