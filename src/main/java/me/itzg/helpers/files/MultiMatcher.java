package me.itzg.helpers.files;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jspecify.annotations.Nullable;

import me.itzg.helpers.errors.InvalidParameterException;

/**
 * MultiMatcher matches using either a literal substring or a regex pattern.
 * 
 * <p> Patterns enclosed in forward slashes are evaluated as regex patterns
 * using {@link java.util.regex.Matcher#find()} other non-null patterns
 * are matched using {@link String#contains(CharSequence)}. A null pattern
 * matches every input
 * 
 */
public class MultiMatcher {

    private final Pattern regex;
    private final String substring;

    /**
     *
     * @param pattern null to indicate always matching, a substring, or a regex indicated by a leading and trailing "/".
     *                The regex can include the anchors "^" and/or "$" to match the entire string, beginning or end.
     */
    public MultiMatcher(@Nullable String pattern) {
        if (pattern == null) {
            regex = null;
            substring = null;
            return;
        }

        if (pattern.length() >= 2 && pattern.startsWith("/") && pattern.endsWith("/")) {
            pattern = pattern.substring(1, pattern.length()-1);

            try {
                regex = Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw new InvalidParameterException("Invalid regex pattern", e);
            }

            substring = null;
        }
        else {
            substring = pattern;
            regex = null;
        }
    }

    public boolean matches(String input) {
        if (regex == null && substring == null) {
            return true;
        }
        else if (regex != null) {
            return regex.matcher(input).find();
        }
        else {
            return input.contains(substring);
        }
    }
}
