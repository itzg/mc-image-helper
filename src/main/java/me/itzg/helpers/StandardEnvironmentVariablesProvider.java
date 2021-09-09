package me.itzg.helpers;

public class StandardEnvironmentVariablesProvider implements EnvironmentVariablesProvider {
    @Override
    public String get(String name) {
        return System.getenv(name);
    }
}
