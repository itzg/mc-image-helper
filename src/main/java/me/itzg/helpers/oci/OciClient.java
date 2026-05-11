package me.itzg.helpers.oci;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.McImageHelper;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.get.ExtendedRequestRetryStrategy;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.oci.RegistryAuthJson.BasicCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;

@Slf4j
public class OciClient implements AutoCloseable {

    private static final String ACCEPT_MANIFESTS =
        OciManifest.MEDIA_TYPE_OCI + "," + OciManifest.MEDIA_TYPE_IMAGE_MANIFEST_V2;

    private static final ObjectMapper MAPPER = ObjectMappers.defaultMapper();

    private static final int MAX_REDIRECTS = 8;
    private static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(10);
    // Response timeout is time-to-first-byte; large blob bodies stream fine after that
    private static final Timeout RESPONSE_TIMEOUT = Timeout.ofSeconds(60);

    private final CloseableHttpClient http;
    private final RegistryAuthJson auths;
    private final String scheme;
    private final Map<String, String> tokenCache = new HashMap<>();

    public OciClient(RegistryAuthJson auths) {
        this(auths, "https");
    }

    // Visible for tests so they can run against a plain-HTTP WireMock registry
    OciClient(RegistryAuthJson auths, String scheme) {
        this.auths = auths;
        this.scheme = scheme;
        // Redirects are walked manually so the Authorization header is not
        // forwarded to CDN pre-signed URLs (GHCR 307s to Azure Blob Storage).
        this.http = HttpClients.custom()
            .setUserAgent("itzg/mc-image-helper/" + McImageHelper.getVersion() + " (cmd=install-oci-pack)")
            .setRetryStrategy(new ExtendedRequestRetryStrategy(3, 1))
            .setDefaultRequestConfig(RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setResponseTimeout(RESPONSE_TIMEOUT)
                .build())
            .build();
    }

    public OciManifest fetchManifest(OciReference ref) {
        final URI uri = URI.create(String.format("%s://%s/v2/%s/manifests/%s",
            scheme, ref.getRegistry(), ref.getRepository(), ref.identifier()));
        log.debug("Fetching manifest {}", uri);

        try (CloseableHttpResponse response = followRedirects(uri, ref, ACCEPT_MANIFESTS)) {
            final int status = response.getCode();
            if (status != 200) {
                throw new GenericException(String.format(
                    "Failed to fetch OCI manifest %s: HTTP %d %s",
                    ref, status, response.getReasonPhrase()));
            }
            final String contentType = contentTypeMime(response);
            if (isImageIndex(contentType)) {
                throw new GenericException(String.format(
                    "OCI reference %s resolved to an image index (%s); "
                        + "supply a per-platform manifest digest instead",
                    ref, contentType));
            }
            final HttpEntity entity = response.getEntity();
            if (entity == null) {
                throw new GenericException("OCI manifest response had no body: " + ref);
            }
            try (InputStream in = entity.getContent()) {
                final JsonNode tree = MAPPER.readTree(in);
                if (tree.has("manifests") && !tree.has("layers")) {
                    throw new GenericException(String.format(
                        "OCI reference %s resolved to an image index; "
                            + "supply a per-platform manifest digest instead", ref));
                }
                return MAPPER.treeToValue(tree, OciManifest.class);
            }
        } catch (IOException e) {
            throw new GenericException("Failed to read OCI manifest " + ref, e);
        }
    }

    public void downloadBlob(OciReference ref, String digest, Path target) {
        if (Files.isRegularFile(target) && verifyExisting(target, digest)) {
            log.debug("Layer {} already on disk at {}; skipping download", digest, target);
            return;
        }
        final URI uri = URI.create(String.format("%s://%s/v2/%s/blobs/%s",
            scheme, ref.getRegistry(), ref.getRepository(), digest));
        log.debug("Downloading blob {}", uri);

        try (CloseableHttpResponse response = followRedirects(uri, ref, null)) {
            final int status = response.getCode();
            if (status != 200) {
                throw new GenericException(String.format(
                    "Failed to download OCI blob %s: HTTP %d %s",
                    digest, status, response.getReasonPhrase()));
            }
            final HttpEntity entity = response.getEntity();
            if (entity == null) {
                throw new GenericException("OCI blob response had no body: " + digest);
            }
            Files.createDirectories(target.getParent());
            final MessageDigest sha = newSha256();
            try (InputStream in = entity.getContent();
                 OutputStream out = Files.newOutputStream(target)) {
                final byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    sha.update(buf, 0, n);
                    out.write(buf, 0, n);
                }
            }
            final String actual = "sha256:" + toHex(sha.digest());
            if (!actual.equalsIgnoreCase(digest)) {
                Files.deleteIfExists(target);
                throw new GenericException(String.format(
                    "Digest mismatch for OCI blob: expected %s, got %s",
                    digest, actual));
            }
        } catch (IOException e) {
            throw new GenericException("Failed to download OCI blob " + digest, e);
        }
    }

    private CloseableHttpResponse followRedirects(URI startUri, OciReference ref, String accept)
        throws IOException {
        URI current = startUri;
        boolean firstHop = true;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            final HttpGet request = new HttpGet(current);
            if (firstHop && accept != null) {
                request.setHeader(HttpHeaders.ACCEPT, accept);
            }
            final CloseableHttpResponse response = firstHop
                ? executeWithAuth(request, ref)
                : http.execute(request);
            final int status = response.getCode();
            if (status >= 300 && status < 400) {
                final String location = headerValue(response, "Location");
                response.close();
                if (location == null || location.isEmpty()) {
                    throw new GenericException(String.format(
                        "Registry returned HTTP %d with no Location header for %s",
                        status, current));
                }
                final URI next = current.resolve(location);
                log.debug("Following redirect ({} -> {})", status, next);
                current = next;
                firstHop = false;
                continue;
            }
            return response;
        }
        throw new GenericException("Exceeded redirect limit while fetching " + startUri);
    }

    @Override
    public void close() throws IOException {
        http.close();
    }

    private CloseableHttpResponse executeWithAuth(ClassicHttpRequest request, OciReference ref)
        throws IOException {
        final String scope = "repository:" + ref.getRepository() + ":pull";
        final String username = auths.forHost(ref.getRegistry())
            .map(BasicCredentials::getUsername)
            .orElse("anonymous");
        final String cacheKey = ref.getRegistry() + "|" + scope + "|" + username;
        final String cachedToken = tokenCache.get(cacheKey);
        if (cachedToken != null) {
            request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cachedToken);
        }

        final CloseableHttpResponse response = http.execute(request);
        if (response.getCode() != 401) {
            return response;
        }
        final String challenge = headerValue(response, "WWW-Authenticate");
        response.close();

        final Map<String, String> params = parseBearerChallenge(challenge);
        final String realm = params.get("realm");
        if (realm == null) {
            throw new GenericException(String.format(
                "Registry %s required auth but no Bearer challenge was provided",
                ref.getRegistry()));
        }
        final String token = fetchToken(realm, params, ref);
        tokenCache.put(cacheKey, token);
        request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return http.execute(request);
    }

    private String fetchToken(String realm, Map<String, String> params, OciReference ref)
        throws IOException {
        final StringBuilder url = new StringBuilder(realm);
        url.append(realm.contains("?") ? '&' : '?');
        if (params.containsKey("service")) {
            url.append("service=").append(URLEncoder.encode(params.get("service"), StandardCharsets.UTF_8))
                .append('&');
        }
        url.append("scope=").append(URLEncoder.encode(
            params.getOrDefault("scope", "repository:" + ref.getRepository() + ":pull"),
            StandardCharsets.UTF_8));

        final HttpGet tokenRequest = new HttpGet(url.toString());
        tokenRequest.setHeader(HttpHeaders.ACCEPT, "application/json");
        auths.forHost(ref.getRegistry()).ifPresent(creds -> applyBasic(tokenRequest, creds));

        try (CloseableHttpResponse response = http.execute(tokenRequest)) {
            final int status = response.getCode();
            if (status != 200) {
                throw new GenericException(String.format(
                    "Token endpoint %s returned HTTP %d %s",
                    realm, status, response.getReasonPhrase()));
            }
            final HttpEntity entity = response.getEntity();
            if (entity == null) {
                throw new GenericException("Token endpoint returned no body: " + realm);
            }
            try (InputStream in = entity.getContent()) {
                final JsonNode body = MAPPER.readTree(in);
                String token = body.path("token").asText(null);
                if (token == null) {
                    token = body.path("access_token").asText(null);
                }
                if (token == null) {
                    throw new GenericException(
                        "Token endpoint did not return a token field: " + realm);
                }
                return token;
            }
        }
    }

    private static void applyBasic(ClassicHttpRequest request, BasicCredentials creds) {
        final String pair = creds.getUsername() + ":" + creds.getPassword();
        final String b64 = Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
        request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + b64);
    }

    private static String headerValue(CloseableHttpResponse response, String name) {
        return Arrays.stream(response.getHeaders(name))
            .findFirst().map(Header::getValue).orElse(null);
    }

    private static String contentTypeMime(CloseableHttpResponse response) {
        final String raw = headerValue(response, HttpHeaders.CONTENT_TYPE);
        if (raw == null) {
            return null;
        }
        try {
            final ContentType ct = ContentType.parse(raw);
            return ct.getMimeType();
        } catch (RuntimeException e) {
            return raw;
        }
    }

    private static boolean isImageIndex(String mimeType) {
        return OciManifest.MEDIA_TYPE_OCI_INDEX.equalsIgnoreCase(mimeType)
            || OciManifest.MEDIA_TYPE_IMAGE_INDEX_V2.equalsIgnoreCase(mimeType);
    }

    private static Map<String, String> parseBearerChallenge(String header) {
        final Map<String, String> params = new HashMap<>();
        if (header == null || !header.regionMatches(true, 0, "Bearer", 0, "Bearer".length())) {
            return params;
        }
        final String body = header.substring("Bearer".length()).trim();
        final StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < body.length(); i++) {
            final char c = body.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (c == ',' && !inQuotes) {
                addParam(params, current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            addParam(params, current.toString());
        }
        return params;
    }

    private static void addParam(Map<String, String> params, String pair) {
        final int eq = pair.indexOf('=');
        if (eq < 0) {
            return;
        }
        params.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
    }

    private static boolean verifyExisting(Path target, String expectedDigest) {
        if (!"sha256:".regionMatches(true, 0, expectedDigest, 0, "sha256:".length())) {
            return false;
        }
        try {
            final MessageDigest sha = newSha256();
            try (InputStream in = Files.newInputStream(target)) {
                final byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    sha.update(buf, 0, n);
                }
            }
            return ("sha256:" + toHex(sha.digest())).equalsIgnoreCase(expectedDigest);
        } catch (IOException e) {
            log.debug("Could not verify existing layer at {}: {}", target, e.getMessage());
            return false;
        }
    }

    /** SHA-256 is JDK-guaranteed; treat absence as a JRE installation bug. */
    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 unavailable in this JRE", e);
        }
    }

    private static String toHex(byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
