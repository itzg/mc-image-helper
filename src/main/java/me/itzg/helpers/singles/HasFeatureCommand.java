package me.itzg.helpers.singles;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "has-feature",
    description = "Check if a subcommand is available and optionally if it has specific options (arguments)")
public class HasFeatureCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Parameters(index = "0", description = "The subcommand name to check for availability")
    String subcommand;

    @Parameters(index = "1", arity = "0..*", description = "Optional option names to check within the subcommand (e.g., 'help' for --help or -h)")
    String[] arguments;

    @Override
    public Integer call() throws Exception {
        Map<String, CommandLine> subcommands = spec.parent().subcommands();

        if (!subcommands.containsKey(subcommand)) {
            return ExitCode.SOFTWARE; // Subcommand not found
        }

        if (arguments != null && arguments.length > 0) {
            CommandSpec subSpec = subcommands.get(subcommand).getCommandSpec();
            for (String argument : arguments) {
                boolean hasOption = subSpec.options().stream()
                    .anyMatch(opt -> Arrays.stream(opt.names())
                        .anyMatch(name -> name.equals("--" + argument) ||
                                         (argument.length() == 1 && name.equals("-" + argument))));
                if (!hasOption) {
                    return ExitCode.SOFTWARE; // Option not found in subcommand
                }
            }
        }

        return ExitCode.OK; // Subcommand (and options, if specified) are available
    }
}
