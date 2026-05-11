package me.itzg.helpers.oci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import me.itzg.helpers.errors.InvalidParameterException;
import org.junit.jupiter.api.Test;

class OciReferenceTest {

    @Test
    void parsesGhcrReferenceWithTag() {
        final OciReference ref = OciReference.parse("ghcr.io/some-org/modpack/tech:latest");

        assertThat(ref.getRegistry()).isEqualTo("ghcr.io");
        assertThat(ref.getRepository()).isEqualTo("some-org/modpack/tech");
        assertThat(ref.getTag()).isEqualTo("latest");
        assertThat(ref.getDigest()).isNull();
    }

    @Test
    void toleratesOciScheme() {
        final OciReference ref = OciReference.parse("oci://ghcr.io/owner/pack:v1");

        assertThat(ref.getRegistry()).isEqualTo("ghcr.io");
        assertThat(ref.getRepository()).isEqualTo("owner/pack");
        assertThat(ref.getTag()).isEqualTo("v1");
    }

    @Test
    void parsesDigestReference() {
        final OciReference ref = OciReference.parse(
            "ghcr.io/owner/pack@sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        assertThat(ref.getDigest()).startsWith("sha256:");
        assertThat(ref.getTag()).isNull();
        assertThat(ref.identifier()).isEqualTo(ref.getDigest());
    }

    @Test
    void prefersDigestOverTagWhenBothAreGiven() {
        final OciReference ref = OciReference.parse(
            "ghcr.io/owner/pack:v1@sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        assertThat(ref.getTag()).isEqualTo("v1");
        assertThat(ref.getDigest()).startsWith("sha256:");
        assertThat(ref.identifier()).isEqualTo(ref.getDigest());
    }

    @Test
    void defaultsRegistryWhenNoneInRef() {
        final OciReference ref = OciReference.parse("library/alpine:3");

        assertThat(ref.getRegistry()).isEqualTo("docker.io");
        assertThat(ref.getRepository()).isEqualTo("library/alpine");
    }

    @Test
    void defaultsTagWhenNoneInRef() {
        final OciReference ref = OciReference.parse("ghcr.io/owner/pack");

        assertThat(ref.getTag()).isEqualTo("latest");
    }

    @Test
    void recognisesPortedLocalhostRegistry() {
        final OciReference ref = OciReference.parse("localhost:5000/some/repo:v1");

        assertThat(ref.getRegistry()).isEqualTo("localhost:5000");
        assertThat(ref.getRepository()).isEqualTo("some/repo");
        assertThat(ref.getTag()).isEqualTo("v1");
    }

    @Test
    void rejectsEmptyReference() {
        assertThatThrownBy(() -> OciReference.parse(""))
            .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> OciReference.parse(null))
            .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void rejectsNonSha256Digests() {
        assertThatThrownBy(() -> OciReference.parse("ghcr.io/owner/pack@md5:abc"))
            .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void rejectsTruncatedSha256Digest() {
        assertThatThrownBy(() -> OciReference.parse("ghcr.io/owner/pack@sha256:abc"))
            .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void rejectsNonHexSha256Digest() {
        assertThatThrownBy(() -> OciReference.parse(
            "ghcr.io/owner/pack@sha256:zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"))
            .isInstanceOf(InvalidParameterException.class);
    }
}
