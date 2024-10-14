package me.itzg.helpers.modrinth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.stream.Stream;
import me.itzg.helpers.modrinth.model.VersionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class ProjectRefTest {
    private final String expectedSlug = "test_project1";
    private final String expectedVersionName = "test_version1";
    private ProjectRef projectRefUT;

    @Test
    void testProjectRefHasProjectSlug() {
        projectRefUT = new ProjectRef(this.expectedSlug, null);

        assertThat(projectRefUT.getIdOrSlug()).isEqualTo(expectedSlug);
    }

    @Test
    void testProjectRefHasVersionTypeWhenVersionIsType() {
        projectRefUT = new ProjectRef(this.expectedSlug, "release");
        assertThat(projectRefUT.getVersionType())
            .isEqualTo(VersionType.release);
        assertThat(projectRefUT.hasVersionId()).isFalse();
        assertThat(projectRefUT.hasVersionName()).isFalse();

        projectRefUT = new ProjectRef(this.expectedSlug, "beta");
        assertThat(projectRefUT.getVersionType())
            .isEqualTo(VersionType.beta);
        assertThat(projectRefUT.hasVersionId()).isFalse();
        assertThat(projectRefUT.hasVersionName()).isFalse();

        projectRefUT = new ProjectRef(this.expectedSlug, "alpha");
        assertThat(projectRefUT.getVersionType())
            .isEqualTo(VersionType.alpha);
        assertThat(projectRefUT.hasVersionId()).isFalse();
        assertThat(projectRefUT.hasVersionName()).isFalse();
    }

    @Test
    void testProjectRefHasVersionIDWhenVersionIs8CharAlphaNum() {
        String expectedVersionId = "abcdEF12";
        projectRefUT = new ProjectRef(this.expectedSlug, expectedVersionId);
        assertThat(projectRefUT.getVersionId()).isEqualTo(expectedVersionId);
        assertThat(projectRefUT.hasVersionType()).isFalse();
        assertThat(projectRefUT.hasVersionName()).isFalse();
    }

    @Test
    void testProjectRefHasVersionNameForOtherValues() {
        projectRefUT = new ProjectRef(
            this.expectedSlug, this.expectedVersionName);

        assertThat(projectRefUT.getVersionNumber())
            .isEqualTo(expectedVersionName);
        assertThat(projectRefUT.hasVersionId()).isFalse();
        assertThat(projectRefUT.hasVersionType()).isFalse();
    }

    @Test
    void fromPossibleUrlDefaultsToGeneratingRefWithPassedValues() {
        projectRefUT = ProjectRef.fromPossibleUrl(
            this.expectedSlug, this.expectedVersionName);

        assertThat(projectRefUT.getIdOrSlug())
            .isEqualTo(this.expectedSlug);
        assertThat(projectRefUT.getVersionNumber())
            .isEqualTo(expectedVersionName);
    }

    @Test
    void fromPossibleUrlExtractsProjectSlugFromUrl() {
        String projectUrl = "https://modrinth.com/modpack/" +
            this.expectedSlug;

        projectRefUT = ProjectRef.fromPossibleUrl(
            projectUrl, this.expectedVersionName);

        assertThat(projectRefUT.getIdOrSlug())
            .isEqualTo(this.expectedSlug);
        assertThat(projectRefUT.getVersionNumber())
            .isEqualTo(expectedVersionName);
    }

    @Test
    void fromPossibleUrlExtractsProjectVersionFromUrlWhenPresent() {
        String projectUrl = "https://modrinth.com/modpack/" +
            this.expectedSlug + "/version/" + this.expectedVersionName;

        projectRefUT = ProjectRef.fromPossibleUrl(
            projectUrl, "release");

        assertThat(projectRefUT.getIdOrSlug())
            .isEqualTo(this.expectedSlug);
        assertThat(projectRefUT.getVersionNumber())
            .isEqualTo(expectedVersionName);
    }

    @Test
    void fromPossibleUrlCreatesProjectRefWithProjectUrlWhenUrlIsNotModrinthProject() {
        String projectUri = "https://files.example.test/modpack/testpack.mrpack";

        projectRefUT = ProjectRef.fromPossibleUrl(
            projectUri, null);

        assertThat(projectRefUT.getProjectUri().toString())
            .isEqualTo(projectUri);
    }

    @Test
    void constructorPullsProjectSlugFromURI()
        throws URISyntaxException
    {
        URI projectUri = new URI(
            "https://files.example.test/modpack/" + expectedSlug + ".mrpack");

        assertThat(new ProjectRef(projectUri, null).getIdOrSlug())
            .isEqualTo(expectedSlug);
    }

    @ParameterizedTest
    @ValueSource(strings = {"slug.mrpack", "/slug.mrpack", "/abs/slug.mrpack", "rel/slug.mrpack", "slug"})
    void constructorPullsProjectSlugFromFileURI(String input) {
        URI projectUri = Paths.get(input).toUri();

        assertThat(new ProjectRef(projectUri, null).getIdOrSlug())
            .isEqualTo("slug");
    }

    @ParameterizedTest
    @MethodSource("parseProjectRef_parameters")
    void parseProjectRef(String input, String slugId, VersionType versionType, String versionId, String versionName, boolean datapack) {
        final ProjectRef result = ProjectRef.parse(input);
        assertThat(result.getIdOrSlug()).isEqualTo(slugId);
        assertThat(result.getVersionType()).isEqualTo(versionType);
        assertThat(result.getVersionId()).isEqualTo(versionId);
        assertThat(result.getVersionNumber()).isEqualTo(versionName);
        assertThat(result.isDatapack()).isEqualTo(datapack);
    }

    public static Stream<Arguments> parseProjectRef_parameters() {
        return Stream.of(
            argumentSet("just slugId","terralith", "terralith", null, null, null, false),
            argumentSet("datapack","datapack:terralith", "terralith", null, null, null, true),
            argumentSet("with version ID","terralith:rEF3UnUI", "terralith", null, "rEF3UnUI", null, false),
            argumentSet("with version type","terralith:release", "terralith", VersionType.release, null, null, false),
            argumentSet("with version name","terralith:2.5.5", "terralith", null, null, "2.5.5", false)
        );
    }
}
