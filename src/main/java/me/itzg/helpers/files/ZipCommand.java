package me.itzg.helpers.files;

import java.io.IOException;
import java.nio.file.Path;

import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "zip", description = "Zip related helpers, asserting on zip-slips and extracting zips")
public class ZipCommand {
    @Option(names = { "--help", "-h" }, usageHelp = true)
    boolean help;

    @Command(name = "check-zip-slip")
    public Integer checkZipSlip(
            @Parameters(index = "0", paramLabel = "zip", description = "Path to zip file")
            Path zip
            ) throws IOException {
        return Zip.validate(zip) ? ExitCode.OK : ExitCode.SOFTWARE;
    }

}