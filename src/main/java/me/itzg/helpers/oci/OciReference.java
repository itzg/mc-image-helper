package me.itzg.helpers.oci;

import java.util.regex.Pattern;
import lombok.Value;
import me.itzg.helpers.errors.InvalidParameterException;

@Value
public class OciReference {

    public static final String DEFAULT_REGISTRY = "docker.io";
    public static final String DEFAULT_TAG = "latest";

    private static final Pattern SHA256_DIGEST = Pattern.compile("sha256:[0-9a-fA-F]{64}");

    String registry;
    String repository;
    String tag;
    String digest;

    public static OciReference parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new InvalidParameterException("OCI reference must not be empty");
        }
        String value = raw;
        if (value.startsWith("oci://")) {
            value = value.substring("oci://".length());
        }

        String digest = null;
        final int atIdx = value.lastIndexOf('@');
        if (atIdx >= 0) {
            digest = value.substring(atIdx + 1);
            value = value.substring(0, atIdx);
            if (!SHA256_DIGEST.matcher(digest).matches()) {
                throw new InvalidParameterException(
                    "Digest must match sha256:<64 hex chars> in OCI reference: " + raw);
            }
        }

        // The colon separating tag is always after the last slash
        // (registry hosts can contain colons for ports, e.g. localhost:5000/foo).
        String tag = null;
        final int lastSlash = value.lastIndexOf('/');
        final int colonIdx = value.indexOf(':', Math.max(0, lastSlash));
        if (colonIdx > 0) {
            tag = value.substring(colonIdx + 1);
            value = value.substring(0, colonIdx);
        }

        // A registry is recognised when the first segment contains a dot,
        // a colon, or is "localhost".
        final String registry;
        final String repository;
        final int slash = value.indexOf('/');
        if (slash > 0) {
            final String firstSegment = value.substring(0, slash);
            if (firstSegment.contains(".") || firstSegment.contains(":")
                || firstSegment.equals("localhost")) {
                registry = firstSegment;
                repository = value.substring(slash + 1);
            } else {
                registry = DEFAULT_REGISTRY;
                repository = value;
            }
        } else {
            registry = DEFAULT_REGISTRY;
            repository = value;
        }

        if (repository.isEmpty()) {
            throw new InvalidParameterException(
                "OCI reference is missing a repository name: " + raw);
        }

        if (digest == null && tag == null) {
            tag = DEFAULT_TAG;
        }

        return new OciReference(registry, repository, tag, digest);
    }

    public String identifier() {
        return digest != null ? digest : tag;
    }

    @Override
    public String toString() {
        if (digest != null) {
            return registry + "/" + repository + "@" + digest;
        }
        return registry + "/" + repository + ":" + tag;
    }
}
