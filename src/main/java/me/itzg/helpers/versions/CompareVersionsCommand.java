package me.itzg.helpers.versions;

import java.util.concurrent.Callable;
import me.itzg.helpers.errors.InvalidParameterException;
import org.apache.maven.artifact.versioning.ComparableVersion;
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

        char leftVersionChannel = leftVersion.charAt(0);
        leftVersionChannel = Character.isDigit(leftVersionChannel) ? 'r' : leftVersionChannel;
        char rightVersionChannel = rightVersion.charAt(0);
        rightVersionChannel = Character.isDigit(rightVersionChannel) ? 'r' : rightVersionChannel;

        if (comparison == Comparison.lt && leftVersionChannel < rightVersionChannel) {
            return 0;
        }
        else if (comparison == Comparison.lt && leftVersionChannel > rightVersionChannel) {
            return 1;
        }

        if (leftVersion.startsWith("a") || leftVersion.startsWith("b")) {
            leftVersion = leftVersion.substring(1);
        }
        if (rightVersion.startsWith("a") || rightVersion.startsWith("b")) {
            rightVersion = rightVersion.substring(1);
        }

        final ComparableVersion lhs = new ComparableVersion(leftVersion);
        final ComparableVersion rhs = new ComparableVersion(rightVersion);

        if (comparison == Comparison.lt && lhs.compareTo(rhs) < 0) {
            return 0;
        }
        else {
            return 1;
        }
    }
}
