package me.itzg.helpers.http;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
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
        StringBuffer sb = new StringBuffer();
        int i = 0;
        while (m.find() && i < values.length) {
            try {
                final String tag = m.group("tag");
                if ("raw".equals(tag)) {
                    m.appendReplacement(sb, values[i].toString());
                }
                else {
                    m.appendReplacement(sb, URLEncoder.encode(values[i].toString(), ENC_UTF_8));
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Internal error", e);
            }
            ++i;
        }
        m.appendTail(sb);

        return sb.toString();
    }

    /**
     * @param values replaces {@code {...}} placeholders in {@code url}
     */
    public static URI populateToUri(String url, Object... values) {
        return URI.create(populate(url, values));
    }

    public static URI populateToUri(String url, Uris.QueryParameters queryParameters, Object... values) {
        return URI.create(populate(url, values) + queryParameters.build());
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
                            .map(entry -> {
                                try {
                                    return entry.getKey() + "=" +
                                            URLEncoder.encode(entry.getValue(), ENC_UTF_8);
                                } catch (UnsupportedEncodingException e) {
                                    throw new GenericException("Trying to encode URL query parameter", e);
                                }
                            })
                            .collect(Collectors.joining("&", "?", ""))
                    : "";
        }
    }
}
