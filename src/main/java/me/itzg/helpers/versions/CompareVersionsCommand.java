package me.itzg.helpers.versions;

import java.util.concurrent.Callable;
import me.itzg.helpers.errors.InvalidParameterException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "compare-versions",
    description = "Used for shell scripting, exits with success(0) when comparison is satisfied or 1 when not"
)
public class CompareVersionsCommand implements Callable<Integer> {

    @Parameters(index = "0")
    String leftVersion;

    @Parameters(index = "1")
    Comparison comparison;

    @Parameters(index = "2")
    String rightVersion;

    @Override
    public Integer call() throws Exception {
        if (leftVersion.isEmpty()) {
            throw new InvalidParameterException("Left version is required");
        }
        if (rightVersion.isEmpty()) {
            throw new InvalidParameterException("Right version is required");
        }

        if (comparison == Comparison.lt && McVersioning.compare(leftVersion, rightVersion) < 0) {
            return 0;
        }
        else {
            return 1;
        }
    }
}
