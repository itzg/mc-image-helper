package me.itzg.helpers.curseforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import me.itzg.helpers.curseforge.ModpacksPageUrlParser.Parsed;
import me.itzg.helpers.errors.InvalidParameterException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModpacksPageUrlParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.curseforge.com/minecraft/modpacks/create-perfect-world",
        "https://beta.curseforge.com/minecraft/modpacks/create-perfect-world",
        "https://www.curseforge.com/minecraft/modpacks/create-perfect-world/",
        "https://www.curseforge.com/minecraft/modpacks/create-perfect-world/files",
        "https://www.curseforge.com/minecraft/modpacks/create-perfect-world/files/",
    })
    void justSlug(String url) {
        final Parsed parsed = ModpacksPageUrlParser.parse(url);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getSlug()).isEqualTo("create-perfect-world");
        assertThat(parsed.getFileId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.curseforge.com/minecraft/modpacks/create-perfect-world/files/5181367"
    })
    void slugAndFileId(String url) {
        final Parsed parsed = ModpacksPageUrlParser.parse(url);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getSlug()).isEqualTo("create-perfect-world");
        assertThat(parsed.getFileId()).isEqualTo(5181367);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "https://www.google.com",
        "https://www.curseforge.com/minecraft/modpacks/create-perfect-world/wrong/",
        "https://www.curseforge.com/minecraft/modpacks/create-perfect-world/invalid/5181367"
    })
    void invalid(String url) {
        assertThatThrownBy(() -> ModpacksPageUrlParser.parse(url))
            .isInstanceOf(InvalidParameterException.class);
    }
}