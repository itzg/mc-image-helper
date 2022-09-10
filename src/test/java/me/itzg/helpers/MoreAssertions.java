package me.itzg.helpers;

import java.util.Arrays;
import org.assertj.core.api.ListAssert;

public class MoreAssertions {

    public static ListAssert<String> assertThatLines(String content) {
        final String[] lines = content.split("\n|\r\n|\r");
        return new ListAssert<>(Arrays.stream(lines, 0,
            lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length
        ));
    }
}
