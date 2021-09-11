package me.itzg.helpers.sync;

public class StandardEnvironmentVariablesProvider implements EnvironmentVariablesProvider {
    @Override
    public String get(String name) {
        return System.getenv(name);
    }
}
