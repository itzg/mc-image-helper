package me.itzg.helpers.versions;

import static me.itzg.helpers.versions.McVersioning.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import me.itzg.helpers.errors.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class McVersioningTest {

    @SuppressWarnings("ConstantValue")
    @Test
    void handlesNull() {
        final String result = validateMinecraftVersion(null);
        assertThat(result).isNull();

        assertThat(isLatest(null)).isTrue();
    }

    @Test
    void handlesSnapshot() {
        assertThat(
            validateMinecraftVersion("SNAPSHOT")
        ).isEqualTo("SNAPSHOT");
        assertThat(isSnapshot("SNAPSHOT")).isTrue();
    }

    @Test
    void handlesLatest() {
        assertThat(
            validateMinecraftVersion("latest")
        ).isEqualTo("latest");
        assertThat(isLatest("latest")).isTrue();
    }

    @ParameterizedTest
    @MethodSource("handlesValidVersions_args")
    void handlesValidVersions(String version, boolean expectValid, boolean expectNull) {
        if (expectValid) {
            assertThat(validateMinecraftVersion(version))
                .isEqualTo(expectNull ? null : version);
        }
        else {
            assertThrows(InvalidParameterException.class,
                () -> validateMinecraftVersion(version)
            );
        }
    }

    public static Stream<Arguments> handlesValidVersions_args() {

        return Stream.of(
            arguments("", true, true),
            arguments("26.2-snapshot-2", true, false),
            arguments("26.1.2", true, false),
            arguments("26.1.2-rc-1", true, false),
            arguments("26.1.1-rc-1", true, false),
            arguments("26.1", true, false),
            arguments("26.1-rc-3", true, false),
            arguments("26.1-pre-3", true, false),
            arguments("26.1-snapshot-11", true, false),
            arguments("1.21.11-rc3", true, false),
            arguments("1.21.11-pre5", true, false),
            arguments("25w46a", true, false),
            arguments("25w14craftmine", true, false),
            arguments("1.21", true, false),
            arguments("1.21-rc1", true, false),
            arguments("1.21-pre4", true, false),
            arguments("24w14potato", true, false),
            arguments("23w13a_or_b", true, false),
            arguments("1.19.1", true, false),
            arguments("22w13oneblockatatime", true, false),
            arguments("20w14infinite", true, false),
            arguments("1.14.2 Pre-Release 4", true, false),
            arguments("1.14", true, false),
            arguments("1.14 Pre-Release 5", true, false),
            arguments("3D Shareware v1.34", true, false),
            arguments("1.1", true, false),
            arguments("1.0", true, false),
            arguments("b1.8.1", true, false),
            arguments("b1.8", true, false),
            arguments("b1.5_01", true, false),
            arguments("a1.2.6", true, false),
            arguments("a1.2.4_01", true, false),
            arguments("a1.2.2b", true, false),
            arguments("a1.2.2a", true, false),
            arguments("inf-20100618", true, false),
            arguments("c0.30_01c", true, false),
            arguments("c0.0.13a", true, false),
            arguments("c0.0.13a_03", true, false),
            arguments("c0.0.11a", true, false),
            arguments("rd-132211", true, false),
            arguments("invalid", false, false),
            arguments("[mc-image-helper] 12:05:22.111 INFO", false, false)
        );
    }
}