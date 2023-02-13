package me.itzg.helpers.env;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import me.itzg.helpers.env.Interpolator.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterpolatorTest {

    @Mock
    EnvironmentVariablesProvider varProvider;

    @Test
    void typicalReplacements() throws IOException {
        when(varProvider.get("CFG_VAR_FILE"))
            .thenReturn(null);
        when(varProvider.get("CFG_VAR"))
            .thenReturn("new-value");

        final Interpolator interpolator = new Interpolator(varProvider, "CFG_");
        final Result<String> result = interpolator.interpolate("${IGNORE_THIS}\n${CFG_VAR}\n");
        assertThat(result.getContent())
            .isEqualTo("${IGNORE_THIS}\nnew-value\n");
        assertThat(result.getReplacementCount()).isEqualTo(1);
    }

    @Test
    void interpolateToValueWithDollarSign() throws IOException {
        when(varProvider.get("CFG_HAS_DOLLAR_VALUE_FILE"))
            .thenReturn(null);
        when(varProvider.get("CFG_HAS_DOLLAR_VALUE"))
            .thenReturn("$(SOME_VALUE)");

        final Interpolator interpolator = new Interpolator(varProvider, "CFG_");
        final Result<String> result = interpolator.interpolate("Interpolate this: ${CFG_HAS_DOLLAR_VALUE}");
        assertThat(result.getContent())
            .isEqualTo("Interpolate this: $(SOME_VALUE)");

    }
}