package me.itzg.helpers.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.itzg.helpers.errors.GenericException;

public final class Uris {

    private static final Pattern PLACEHOLDERS = Pattern.compile("\\{((?<tag>.+?):)?.*?}");
    private static final Pattern URI_DETECT = Pattern.compile("^(http|https)://");
    public static final String ENC_UTF_8 = "utf-8";

    /**
     * Replaces placeholders with the syntax {@code {var}} with the corresponding value. The
     * actual name within the placeholder is not used, but just provided for readability.
     * Placeholders can be prefixed with a tag like {@code {tag:var}} where the tag <code>raw</code>
     * is currently supported to indicate the value should not be URL encoded.
     */
    public static String populate(String url, Object... values) {
        if (values.length == 0) {
            return url;
        }

        Matcher m = PLACEHOLDERS.matcher(url);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (m.find() && i < values.length) {
            m.appendReplacement(sb, values[i].toString());
            ++i;
        }
        m.appendTail(sb);

        if (m.find()) {
            throw new GenericException("Too many values provided for placeholders");
        }
        else if (i != values.length) {
            throw new GenericException("Not enough placeholders for provided values");
        }

        return sb.toString();
    }

    /**
     * @param url URL with placeholders like {@code https://example.com/path/{var}}
     * @param values replaces {@code {...}} placeholders in {@code url}
     */
    public static URI populateToUri(String url, Object... values) {
        final String populated = populate(url, values);
        final int queryPos = populated.indexOf('?');
        if (queryPos >= 0) {
            final String query = populated.substring(queryPos + 1);
            return encodeToUri(populated.substring(0, queryPos), query, false);
        }
        return encodeToUri(populated, null, false);
    }

    public static URI populateToUri(String url, Uris.QueryParameters queryParameters, Object... values) {
        if (queryParameters == null || queryParameters.isEmpty()) {
            return populateToUri(url, values);
        }
        final String populated = populate(url, values);
        final String query = queryParameters.build();
        return encodeToUri(populated, query, true);
    }

    /**
     *
     * @param url url that does not contain a query part and might still need the path/filename to be URL encoded
     * @param query query part
     * @param queryPreEncoded if true, the {@code query} is assumed to be already encoded.
     *                        For example, {@code ids=["fabric-api","cloth-config"]} should already be pre-encoded into {@code ids%5B%5D=fabric-api&ids%5B%5D=cloth-config}
     * @return fully encoded and legal {@link URI}
     */
    private static URI encodeToUri(String url, String query, boolean queryPreEncoded) {
        try {
            // NOTE unable to use URI.create(String) to pre-parse the given url since its
            // path may have a filename with spaces in it. And that's one of the main points
            // of this method.

            // 1. Extract Scheme (e.g., "http")
            int schemeEnd = url.indexOf("://");
            if (schemeEnd == -1) {
                throw new IllegalArgumentException("URL missing scheme (e.g. http://): " + url);
            }
            String scheme = url.substring(0, schemeEnd);

            // 2. Separate Authority (host:port) from Path
            int pathStart = url.indexOf('/', schemeEnd + 3);
            String authority;
            String path;

            if (pathStart == -1) {
                authority = url.substring(schemeEnd + 3);
                path = "";
            } else {
                authority = url.substring(schemeEnd + 3, pathStart);
                path = url.substring(pathStart);
            }

            // 3. Construct URI — the 5-arg constructor converts spaces in 'path' to '%20'
            if (!queryPreEncoded) {
                return new URI(scheme, authority, path, query, null);
            }

            // 4. Handle pre-encoded query safely
            URI uriWithoutQuery = new URI(scheme, authority, path, null, null);
            if (query == null || query.isBlank()) {
                return uriWithoutQuery;
            }

            // Combine the encoded base with the pre-encoded query
            String fullUriString = uriWithoutQuery.toASCIIString() + "?" + query;
            return URI.create(fullUriString);

        } catch (URISyntaxException e) {
            throw new GenericException("Failed to encode URI string: " + url, e);
        }
    }

    public static boolean isUri(String value) {
        final Matcher m = URI_DETECT.matcher(value);
        return m.lookingAt();
    }

    private Uris() {
    }

    public static class QueryParameters {
        private final Map<String, String> parameters = new HashMap<>();

        public static QueryParameters queryParameters() {
            return new QueryParameters();
        }

        public boolean isEmpty() {
            return parameters.isEmpty();
        }

        /**
         * @param value add query parameter if not null
         */
        public QueryParameters add(String name, String value) {
            if (value != null) {
                parameters.put(name, value);
            }
            return this;
        }

        /**
         * Adds a query parameter formatted into {@code ["str","str"]}
         * @param values adds the query parameter is not null and not empty
         */
        public QueryParameters addStringArray(String name, Collection<String> values) {
            if (values != null && !values.isEmpty()) {
                parameters.put(name,
                    values.stream()
                        .map(s -> "\"" + s + "\"")
                        .collect(Collectors.joining(",", "[", "]"))
                    );
            }
            return this;
        }

        public QueryParameters addStringArray(String name, String value) {
            if (value != null) {
                return addStringArray(name, Collections.singletonList(value));
            }
            return this;
        }

        public String build() {
            return !parameters.isEmpty() ?
                    parameters.entrySet().stream()
                            .map(entry ->
                                entry.getKey() + "=" +
                                    URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                            .collect(Collectors.joining("&"))
                    : "";
        }
    }
}
