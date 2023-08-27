package me.itzg.helpers.env;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

public class MappedEnvVarProvider implements EnvironmentVariablesProvider {

    final Map<String,String> values = new HashMap<>();

    private MappedEnvVarProvider() {

    }

    public static MappedEnvVarProvider of(String... nameValue) {
        assertThat(nameValue.length).isEven();

        final MappedEnvVarProvider provider = new MappedEnvVarProvider();
        for (int i = 0; i < nameValue.length; i += 2) {
            provider.values.put(nameValue[i], nameValue[i + 1]);
        }

        return provider;
    }

    @Override
    public String get(String name) {
        return values.get(name);
    }
}
