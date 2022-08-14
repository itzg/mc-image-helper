package me.itzg.helpers.forge;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(name = "install-forge", mixinStandardHelpOptions = true)
public class InstallForgeCommand implements Callable<Integer> {

    public static final Pattern ALLOWED_FORGE_VERSION = Pattern.compile(
        String.join("|", ForgeInstaller.LATEST, ForgeInstaller.RECOMMENDED, "\\d+(\\.\\d+)"),
        Pattern.CASE_INSENSITIVE
    );

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = "--minecraft-version", required = true)
    String minecraftVersion;

    private String forgeVersion;
    @Option(names = "--forge-version", required = true, defaultValue = ForgeInstaller.RECOMMENDED,
        description = "A specific Forge version or to auto-resolve the version provide 'latest' or 'recommended'."
            + " Default value is ${DEFAULT-VALUE}"
    )
    public void setForgeVersion(String forgeVersion) {
        if (!ALLOWED_FORGE_VERSION.matcher(forgeVersion).matches()) {
            throw new ParameterException(spec.commandLine(),
                "Invalid value for --forge-version"
                );
        }
        this.forgeVersion = forgeVersion.toLowerCase();
    }

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--results-file", description = "A key=value file suitable for scripted environment variables. Currently includes"
        + "\n  SERVER: the entry point jar or script")
    Path resultsFile;

    @Option(names = "--force-reinstall")
    boolean forceReinstall;

    @Override
    public Integer call() throws Exception {
        final ForgeInstaller installer = new ForgeInstaller();
        installer.install(minecraftVersion, forgeVersion, outputDirectory, resultsFile, forceReinstall);

        return ExitCode.OK;
    }
}
