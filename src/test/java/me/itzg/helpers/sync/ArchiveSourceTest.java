package me.itzg.helpers.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import me.itzg.helpers.errors.InvalidParameterException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ArchiveSourceTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "world.zip|world.zip|''",
        "world.zip{saves/world}|world.zip|saves/world",
        "world.zip{./saves//world/}|world.zip|saves/world",
        "world.zip{saves\\world}|world.zip|saves/world",
        "world.zip{.}|world.zip|''",
        "world.zip{ saves/world ; saves/other }|world.zip|saves/world;saves/other",
        "world.zip{saves/world;saves/world2}|world.zip|saves/world;saves/world2",
        "a@b{1}.zip|a@b{1}.zip|''",
        "a{b}.zip{world}|a{b}.zip|world",
        "https://example.com/world.zip|https://example.com/world.zip|''",
        "https://example.com/world.zip{saves/world}|https://example.com/world.zip|saves/world",
        "https://user:pass@example.com/world.zip{world}|https://user:pass@example.com/world.zip|world",
    })
    void parses(String value, String source, String directories) {
        assertThat(ArchiveSource.parse(value))
            .isEqualTo(new ArchiveSource(source,
                directories.isEmpty() ? List.of() : List.of(directories.split(";"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "world.zip{}",
        "world.zip{ }",
        "world.zip{saves;}",
        "world.zip{;saves}",
        "world.zip{/saves}",
        "world.zip{\\saves}",
        "world.zip{C:/saves}",
        "world.zip{..}",
        "world.zip{saves/../..}",
        "world.zip{saves\\..\\other}",
        "world.zip{saves;../other}",
        "https://example.com/world.zip{../world}",
    })
    void rejectsInvalidPath(String value) {
        assertThatThrownBy(() -> ArchiveSource.parse(value))
            .isInstanceOf(InvalidParameterException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "world.zip{saves;saves}",
        "world.zip{saves;saves/world}",
        "world.zip{saves/world;saves}",
        "world.zip{.;saves}",
    })
    void rejectsOverlappingPaths(String value) {
        assertThatThrownBy(() -> ArchiveSource.parse(value))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("overlap");
    }
}
