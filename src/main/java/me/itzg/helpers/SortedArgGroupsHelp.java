package me.itzg.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import picocli.CommandLine.Help;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.IOrdered;
import picocli.CommandLine.Model.OptionSpec;

/**
 * Renders the synopsis of argument groups with their options in a stable order.
 * Picocli 4.7.7 renders them in reflection order, which varies from one JVM run to the next
 * for options declared on setter methods.
 * <p>
 * Options are sorted by arity and name, as picocli does once it includes the fix for
 * <a href="https://github.com/remkop/picocli/issues/2294">remkop/picocli#2294</a>.
 * With {@code sortSynopsis = false}, options and subgroups are sorted by their {@code order}
 * with ties between options broken by arity and name.
 */
public class SortedArgGroupsHelp extends Help {

    private static final Comparator<OptionSpec> OPTION_SORT = Help.createShortOptionArityAndNameComparator();

    public SortedArgGroupsHelp(CommandSpec commandSpec, ColorScheme colorScheme) {
        super(commandSpec, colorScheme);
    }

    @Override
    protected Ansi.Text createDetailedSynopsisGroupsText(Set<ArgSpec> outparam_groupArgs) {
        Ansi.Text groupText = ansi().new Text(0);
        for (ArgGroupSpec group : commandSpec().argGroups()) {
            if (group.validate()) { // non-validating groups are not shown in the synopsis
                groupText = groupText.concat(" ").concat(synopsisText(group, outparam_groupArgs));
            }
        }
        return groupText;
    }

    /**
     * Same as {@link ArgGroupSpec#synopsisText(ColorScheme, Set)} other than the sorting of options.
     */
    private Ansi.Text synopsisText(ArgGroupSpec group, Set<ArgSpec> outparam_groupArgs) {
        if (!group.positionalParameters().isEmpty()) {
            return group.synopsisText(colorScheme(), outparam_groupArgs);
        }

        final Ansi.Text synopsis = commandSpec().usageMessage().sortSynopsis() ?
            sortedByName(group, outparam_groupArgs)
            : sortedByOrder(group, outparam_groupArgs);

        final boolean grouped = group.args().size() > 1 || !group.subgroups().isEmpty();
        final boolean required = group.multiplicity().min() > 0;
        Ansi.Text result = ansi().new Text(0)
            .concat(required ? (grouped ? "(" : "") : "[")
            .concat(synopsis)
            .concat(required ? (grouped ? ")" : "") : "]");
        int i = 1;
        for (; i < group.multiplicity().min(); i++) {
            result = result.concat(" (").concat(synopsis).concat(")");
        }
        if (group.multiplicity().isVariable()) {
            result = result.concat("...");
        }
        else {
            for (; i < group.multiplicity().max(); i++) {
                result = result.concat(" [").concat(synopsis).concat("]");
            }
        }
        return result;
    }

    private Ansi.Text sortedByName(ArgGroupSpec group, Set<ArgSpec> outparam_groupArgs) {
        final String infix = infix(group);
        final List<OptionSpec> options = new ArrayList<>(group.options());
        options.sort(OPTION_SORT);

        Ansi.Text synopsis = ansi().new Text(0);
        for (OptionSpec option : options) {
            synopsis = concatOption(synopsis, infix, option, outparam_groupArgs);
        }
        for (ArgGroupSpec subgroup : group.subgroups()) {
            if (!synopsis.toString().isEmpty()) {
                synopsis = synopsis.concat(infix);
            }
            synopsis = synopsis.concat(synopsisText(subgroup, outparam_groupArgs));
        }
        return synopsis;
    }

    private Ansi.Text sortedByOrder(ArgGroupSpec group, Set<ArgSpec> outparam_groupArgs) {
        final String infix = infix(group);
        final List<IOrdered> components = new ArrayList<>(group.options());
        components.addAll(group.subgroups());
        components.sort(Comparator.comparingInt(IOrdered::order)
            .thenComparing((o1, o2) -> o1 instanceof OptionSpec && o2 instanceof OptionSpec ?
                OPTION_SORT.compare((OptionSpec) o1, (OptionSpec) o2) : 0
            )
        );

        Ansi.Text synopsis = ansi().new Text(0);
        for (IOrdered component : components) {
            if (component instanceof OptionSpec) {
                synopsis = concatOption(synopsis, infix, (OptionSpec) component, outparam_groupArgs);
            }
            else {
                // picocli does not add the infix before a subgroup in this case
                synopsis = synopsis.concat(synopsisText((ArgGroupSpec) component, outparam_groupArgs));
            }
        }
        return synopsis;
    }

    private Ansi.Text concatOption(Ansi.Text synopsis, String infix, OptionSpec option, Set<ArgSpec> outparam_groupArgs) {
        outparam_groupArgs.add(option);
        // non-empty text starts with a space, which is replaced by the infix
        final Ansi.Text optionText = createDetailedSynopsisOptionsText(
            Collections.emptySet(), Collections.singletonList(option), null, false
        );
        if (optionText.toString().isEmpty()) {
            return synopsis;
        }
        return (synopsis.toString().isEmpty() ? synopsis : synopsis.concat(infix))
            .concat(optionText.substring(1));
    }

    private static String infix(ArgGroupSpec group) {
        return group.exclusive() ? " | " : " ";
    }
}
