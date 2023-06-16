package me.itzg.helpers.versions;

import java.util.concurrent.Callable;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;

@Command(name = "resolve-minecraft-version", description = "Resolves and validate latest, snapshot, and specific versions")
public class ResolveMinecraftVersionCommand implements Callable<Integer> {

    @Parameters(arity = "1")
    String inputVersion;

    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Override
    public Integer call() throws Exception {
        try (SharedFetch sharedFetch = Fetch.sharedFetch("resolve-minecraft-version", sharedFetchArgs.options())) {
            final String resolved = new MinecraftVersionsApi(sharedFetch)
                .resolve(inputVersion)
                .block();
            if (resolved == null) {
                System.err.println("Unable to resolve version from "+inputVersion);
                return ExitCode.USAGE;
            }

            System.out.println(resolved);
        }

        return ExitCode.OK;
    }
}
