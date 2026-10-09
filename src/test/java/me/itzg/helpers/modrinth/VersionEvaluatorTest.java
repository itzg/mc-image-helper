package me.itzg.helpers.modrinth;

import static me.itzg.helpers.modrinth.model.VersionType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.List;
import java.util.stream.Stream;
import me.itzg.helpers.modrinth.model.Project;
import me.itzg.helpers.modrinth.model.Version;
import me.itzg.helpers.modrinth.model.VersionType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VersionEvaluatorTest {

    public static Stream<Arguments> pickVersion_args() {
        return Stream.of(
            argumentSet("release only, release first",
                VersionTypePref.parse("release"),
                List.of(version("1", release), version("2", beta)),
                version("1", release)
            ),
            argumentSet("release only, beta first",
                VersionTypePref.parse("release"),
                List.of(version("1", beta), version("2", release)),
                version("2", release)
            ),
            argumentSet("allow alpha, prefer best, release first",
                VersionTypePref.parse("alpha+"),
                List.of(version("1", release), version("2", beta)),
                version("1", release)
            ),
            argumentSet("allow alpha, prefer best, alpha first",
                VersionTypePref.parse("alpha+"),
                List.of(version("1", alpha), version("2", release), version("3", beta)),
                version("2", release)
            ),
            argumentSet("allow alpha, prefer best, beta fallback",
                VersionTypePref.parse("alpha+"),
                List.of(version("1", alpha), version("2", beta)),
                version("2", beta)
            ),
            argumentSet("allow alpha, prefer best, alpha fallback",
                VersionTypePref.parse("alpha+"),
                List.of(version("1", alpha)),
                version("1", alpha)
            ),
            argumentSet("allow beta, prefer best, beta fallback",
                VersionTypePref.parse("beta+"),
                List.of(version("1", beta)),
                version("1", beta)
            ),
            argumentSet("allow release, prefer best, release",
                VersionTypePref.parse("release+"),
                List.of(version("1", release)),
                version("1", release)
            )
        );
    }

    private static Version version(String id, VersionType versionType) {
        return new Version().setId(id).setVersionType(versionType);
    }

    @ParameterizedTest
    @MethodSource("pickVersion_args")
    void pickVersion(VersionTypePref pref, List<Version> versions, Version expected) {
        final Project project = new Project().setTitle("Test Project");

        Version picked = VersionEvaluator.pickVersion(project, versions, pref);
        assertEquals(expected, picked);
    }
}
