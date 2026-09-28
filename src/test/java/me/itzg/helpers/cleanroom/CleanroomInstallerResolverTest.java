package me.itzg.helpers.cleanroom;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetch.Options;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Try not run this frequently (rate limit 60 REST call/hour)
 */
@WireMockTest
public class CleanroomInstallerResolverTest {

    public static Stream<Arguments> resolve_args() {
        // installerVersion, expectedInstallerVersion
        return Stream.of(
            // try latest installer to download specific loader
//            arguments("latest", "0.1.4"),
            // try specific installer to download latest loader
            arguments("0.1.4", "0.1.4")
            // this will fail
//            ,arguments("latest", "0.0.0"),
//            arguments("0.1.4", "0.1.3"),
//            arguments("0.1.4.1", "0.1.4.1")
        );
    }

    @ParameterizedTest
    @MethodSource("resolve_args")
    void resolve(String installerVersion, String expectedInstallerVersion) {
        try (SharedFetch sharedFetch = Fetch.sharedFetch("install-cleanroom", Options.builder().build())) {
            final CleanroomInstallerResolver resolver = new CleanroomInstallerResolver(
                sharedFetch,
                installerVersion, CleanroomInstallerResolver.LATEST, // requestedCleanroomVersion only used by installer itself
                CleanroomInstallerResolver.DEFAULT_RELEASE_URL
            );

            final CleanroomVersion versionPair = resolver.resolve(null, installerVersion);
            assertThat(versionPair).isNotNull();
            assertThat(versionPair.installerVersion()).isEqualTo(expectedInstallerVersion);
        }
    }

}
