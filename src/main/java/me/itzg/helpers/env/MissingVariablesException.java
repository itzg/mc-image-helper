package me.itzg.helpers.env;

import java.util.List;

public class MissingVariablesException extends RuntimeException {
    private final List<String> variables;

    public MissingVariablesException(List<String> variables) {
        super("Missing one or more variables");
        this.variables = variables;
    }

    public List<String> getVariables() {
        return variables;
    }
}
