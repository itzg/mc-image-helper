package me.itzg.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;

class SortedArgGroupsHelpTest {

    @Test
    void sortsSetterOptionsWithinGroupByName() {
        final String usage = new CommandLine(new SetterGroupCommand())
            .setHelpFactory(SortedArgGroupsHelp::new)
            .getUsageMessage(Ansi.OFF)
            .replace("\r\n", "\n");

        assertThat(usage).startsWith("Usage: setter-group [[--alpha] [--mid] [--zeta]]\n");
    }

    @Test
    void breaksTiesByNameWhenSortedByOrder() {
        final String usage = new CommandLine(new OrderedGroupCommand())
            .setHelpFactory(SortedArgGroupsHelp::new)
            .getUsageMessage(Ansi.OFF)
            .replace("\r\n", "\n");

        assertThat(usage).startsWith("Usage: ordered-group [[[--alpha] [--zeta]] | --url=<url>]\n");
    }

    @Command(name = "setter-group")
    static class SetterGroupCommand {

        @ArgGroup(exclusive = false)
        Group group = new Group();

        static class Group {

            @Option(names = "--zeta")
            public void setZeta(boolean zeta) {
            }

            @Option(names = "--hidden", hidden = true)
            public void setHidden(boolean hidden) {
            }

            @Option(names = "--alpha")
            public void setAlpha(boolean alpha) {
            }

            @Option(names = "--mid")
            public void setMid(boolean mid) {
            }
        }
    }

    @Command(name = "ordered-group", sortSynopsis = false)
    static class OrderedGroupCommand {

        @ArgGroup
        Choice choice = new Choice();

        static class Choice {

            @Option(names = "--url", order = 1)
            String url;

            @ArgGroup(exclusive = false, order = 0)
            Tied tied = new Tied();
        }

        static class Tied {

            @Option(names = "--zeta")
            public void setZeta(boolean zeta) {
            }

            @Option(names = "--alpha")
            public void setAlpha(boolean alpha) {
            }
        }
    }
}
