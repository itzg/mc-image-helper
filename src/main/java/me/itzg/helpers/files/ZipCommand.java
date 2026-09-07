package me.itzg.helpers.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import me.itzg.helpers.errors.InvalidParameterException;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "zip", description = "Zip related helpers, asserting on zip-slips and extracting zips")
public class ZipCommand {
    @Option(names = { "--help", "-h" }, usageHelp = true)
    boolean help;

    @Command(name = "check-zip-slip", description = "Checks if a zip contains a zip-slip")
    public Integer checkZipSlip(
            @Parameters(index = "0", paramLabel = "ZIP", description = "Path to zip file") Path zip)
            throws IOException {


        isZipAndExists(zip);

        return Zip.containsZipSlip(zip) ? ExitCode.SOFTWARE : ExitCode.OK;
    }
    private static void isZipAndExists(Path zip) throws InvalidParameterException, IOException {
        if (!Files.exists(zip)) {
            throw new InvalidParameterException("File does not exist at: " + zip.toAbsolutePath());
        } else if (!Files.probeContentType(zip).equals("application/zip")) {
            throw new InvalidParameterException("File is not a zip: " + zip.toAbsolutePath());
        }
    }

}