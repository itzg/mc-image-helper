package me.itzg.helpers;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.InvalidParameterException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "show-all-subcommand-usage", description = "Renders all of the subcommand usage as markdown sections for README")
@Slf4j
public class ShowAllSubcommandUsage implements Callable<Integer> {

    static final String OVERVIEW_START = "<!-- START of documentation generated using `mc-image-helper --help` -->";
    static final String OVERVIEW_END = "<!-- END of documentation generated using `mc-image-helper --help` -->";
    static final String SUBCOMMANDS_START = "<!-- START of documentation generated using `mc-image-helper show-all-subcommand-usage` -->";
    static final String SUBCOMMANDS_END = "<!-- END of documentation generated using `mc-image-helper show-all-subcommand-usage` -->";

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        final PrintWriter out = spec.commandLine().getOut();
        out.printf("%n_The following is generated using `%s`_%n%n%s", spec.qualifiedName(),
                renderSubcommandUsage().replace("\n", System.lineSeparator()));
        out.flush();
        return ExitCode.OK;
    }

    @Command(name = "update-readme", mixinStandardHelpOptions = true, description = "Updates the generated command documentation in README")
    public Integer updateReadme(
            @Parameters(index = "0", arity = "0..1", paramLabel = "README", defaultValue = "./README.md", description = "Path to README file to update") Path readme)
            throws IOException {
        final String existing = Files.readString(readme);
        final String updated = renderReadme(existing);
        if (!existing.equals(updated)) {
            Files.writeString(readme, updated);
        }
        return ExitCode.OK;
    }

    @Command(name = "check-readme", mixinStandardHelpOptions = true, description = "Checks that README command documentation is current; exits with 1 if out of date")
    public Integer checkReadme(
            @Parameters(index = "0", arity = "0..1", paramLabel = "README", defaultValue = "./README.md", description = "Path to README file to check") Path readme)
            throws IOException {
        final String existing = Files.readString(readme);
        if (!existing.equals(renderReadme(existing))) {
            log.error("{} is out of date. Regenerate it with: {} update-readme \"{}\"",
                    readme, spec.qualifiedName(), readme);
            return ExitCode.SOFTWARE;
        }
        return ExitCode.OK;
    }

    private String renderSubcommandUsage() {
        return spec.parent().subcommands().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "### " + entry.getKey() + "\n\n" + renderUsage(entry.getValue()))
                .collect(Collectors.joining());
    }

    private String renderUsage(CommandLine command) {
        // Keep generated documentation independent of terminal size and color support.
        command.setUsageHelpAutoWidth(false);
        command.setUsageHelpWidth(80);
        return "```\n" + command.getUsageMessage(Ansi.OFF).replace("\r\n", "\n") + "```\n\n";
    }

    private String renderReadme(String existing) {
        final int overviewStart = findUniqueMarker(existing, OVERVIEW_START);
        final int overviewEnd = findUniqueMarker(existing, OVERVIEW_END);
        final int subcommandsStart = findUniqueMarker(existing, SUBCOMMANDS_START);
        final int subcommandsEnd = findUniqueMarker(existing, SUBCOMMANDS_END);

        if (overviewStart >= overviewEnd
                || overviewEnd >= subcommandsStart
                || subcommandsStart >= subcommandsEnd) {
            throw new InvalidParameterException("README documentation markers are out of order");
        }

        final String overview = "\n\n" + renderUsage(spec.parent().commandLine());
        final String subcommands = "\n\n" + renderSubcommandUsage();
        return existing.substring(0, overviewStart + OVERVIEW_START.length()) + overview
                + existing.substring(overviewEnd, subcommandsStart + SUBCOMMANDS_START.length()) + subcommands
                + existing.substring(subcommandsEnd);
    }

    private static int findUniqueMarker(String existing, String marker) {
        final int index = existing.indexOf(marker);
        if (index == -1 || index != existing.lastIndexOf(marker)) {
            throw new InvalidParameterException("README documentation marker must appear exactly once: " + marker);
        }
        return index;
    }
}
