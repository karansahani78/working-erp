package com.educationerp.communication;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills {@code {{variable}}} placeholders in a template.
 *
 * <p>A placeholder with no value is left visible rather than quietly blanked. A message that
 * still reads "NPR {{amount}}" is obviously wrong to whoever receives it, whereas a receipt
 * that quietly omits the amount looks like a real receipt.
 */
public final class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

    private TemplateRenderer() {
    }

    public static String render(String template, Map<String, String> values) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values == null ? null : values.get(matcher.group(1));
            // appendReplacement treats $ and \ in the value specially, so they are escaped.
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement(value == null ? matcher.group(0) : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** The variable names a template refers to, so an editor can be told what it needs. */
    public static java.util.List<String> variablesOf(String template) {
        java.util.List<String> names = new java.util.ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(template == null ? "" : template);
        while (matcher.find()) {
            if (!names.contains(matcher.group(1))) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }
}
