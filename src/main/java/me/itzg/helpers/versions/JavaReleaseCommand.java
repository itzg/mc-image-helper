package me.itzg.helpers.versions;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;

@Command(name = "java-release", description = "Outputs the Java release number, such as 8, 11, 17")
public class JavaReleaseCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        final String versionStr = System.getProperty("java.version");

        final String[] parts = versionStr.split("\\.");

        try {
            final int major = Integer.parseInt(parts[0]);
            final Integer minor = parts.length >= 2 ? Integer.parseInt(parts[1]) : null;
            if (major == 1 && minor != null) {
                System.out.println(minor);
            }
            else if (major >= 9) {
                System.out.println(major);
            }
            else {
                System.err.println("Unexpected version: " + versionStr);
                return ExitCode.SOFTWARE;
            }
        } catch (NumberFormatException e) {
            System.err.println("Malformed version: " + versionStr);
            return ExitCode.SOFTWARE;
        }

        return ExitCode.OK;
    }
}
