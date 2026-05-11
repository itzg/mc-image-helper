package me.itzg.helpers.oci;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RegistryAuthJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, BasicCredentials> byHost;

    private RegistryAuthJson(Map<String, BasicCredentials> byHost) {
        this.byHost = byHost;
    }

    public static RegistryAuthJson load(Path explicitPath) {
        final Path path = explicitPath != null
            ? explicitPath
            : defaultPath();
        if (path == null || !Files.isReadable(path)) {
            log.debug("No registry auth file readable at {}", path);
            return empty();
        }
        try {
            final JsonNode root = MAPPER.readTree(path.toFile());
            final JsonNode auths = root.path("auths");
            final Map<String, BasicCredentials> hosts = new TreeMap<>();
            if (auths.isObject()) {
                final Iterator<Map.Entry<String, JsonNode>> it = auths.fields();
                while (it.hasNext()) {
                    final Map.Entry<String, JsonNode> entry = it.next();
                    parseEntry(entry.getValue())
                        .ifPresent(creds -> hosts.put(normaliseHost(entry.getKey()), creds));
                }
            }
            log.debug("Loaded credentials for {} host(s) from {}", hosts.size(), path);
            return new RegistryAuthJson(hosts);
        } catch (IOException e) {
            log.warn("Failed to parse registry auth file at {}: {}", path, e.getMessage());
            return empty();
        }
    }

    public static RegistryAuthJson empty() {
        return new RegistryAuthJson(java.util.Collections.emptyMap());
    }

    public Optional<BasicCredentials> forHost(String host) {
        return Optional.ofNullable(byHost.get(normaliseHost(host)));
    }

    private static Path defaultPath() {
        final String home = System.getProperty("user.home");
        if (home == null) {
            return null;
        }
        final Path containersAuth = Paths.get(home, ".config", "containers", "auth.json");
        if (Files.isReadable(containersAuth)) {
            return containersAuth;
        }
        return Paths.get(home, ".docker", "config.json");
    }

    private static String normaliseHost(String host) {
        if ("index.docker.io".equals(host) || "https://index.docker.io/v1/".equals(host)) {
            return "docker.io";
        }
        String h = host;
        final int proto = h.indexOf("://");
        if (proto >= 0) {
            h = h.substring(proto + 3);
        }
        final int slash = h.indexOf('/');
        if (slash >= 0) {
            h = h.substring(0, slash);
        }
        return h.toLowerCase();
    }

    private static Optional<BasicCredentials> parseEntry(JsonNode entry) {
        final String authBlob = entry.path("auth").asText(null);
        if (authBlob != null && !authBlob.isEmpty()) {
            try {
                final String decoded = new String(Base64.getDecoder().decode(authBlob));
                final int colon = decoded.indexOf(':');
                if (colon > 0) {
                    return Optional.of(new BasicCredentials(
                        decoded.substring(0, colon),
                        decoded.substring(colon + 1)
                    ));
                }
            } catch (IllegalArgumentException ignored) {
                // fall through and try username/password fields
            }
        }
        final String username = entry.path("username").asText(null);
        final String password = entry.path("password").asText(null);
        if (username != null && password != null) {
            return Optional.of(new BasicCredentials(username, password));
        }
        return Optional.empty();
    }

    @Value
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BasicCredentials {
        String username;
        String password;
    }
}
