package me.itzg.helpers.files;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class TabularOutput {
    private final char headerDivider;
    private final String columnDivider;
    private final String[] headers;
    private final List<String[]> rows = new ArrayList<>();
    private final int[] widths;
    private final Integer[] widthLimits;
    private final String[] trimSuffixes;

    public TabularOutput(char headerDivider, String columnDivider, String... headers) {
        this.headerDivider = headerDivider;
        this.columnDivider = columnDivider;
        this.headers = headers;
        this.widths = new int[headers.length];
        this.widthLimits = new Integer[headers.length];
        this.trimSuffixes = new String[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
    }

    public void addRow(String... cells) {
        if (cells.length != headers.length) {
            throw new IllegalArgumentException(String.format("Row has %d columns but header has %d", cells.length, headers.length));
        }

        final String[] trimmed = trimToWidthLimits(cells);
        rows.add(trimmed);

        for (int i = 0; i < cells.length; i++) {
            widths[i] = Math.max(widths[i], trimmed[i].length());
        }
    }

    private String[] trimToWidthLimits(String[] cells) {
        final String[] result = new String[cells.length];
        for (int i = 0; i < cells.length; i++) {
            if (widthLimits[i] != null && cells[i].length() > widthLimits[i]) {
                result[i] = cells[i].substring(0, widthLimits[i]) + "...";
            }
            else {
                result[i] = cells[i];
            }

            if (trimSuffixes[i] != null && result[i].endsWith(trimSuffixes[i])) {
                result[i] = result[i].substring(0, result[i].length() - trimSuffixes[i].length());
            }
        }
        return result;
    }

    public void output(PrintWriter out) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            sb.append("%-").append(widths[i]).append("s");
            if (i < widths.length - 1) {
                sb.append(columnDivider);
            }
        }
        sb.append("%n");
        final String format = sb.toString();

        out.printf(format, (Object[]) headers);

        for (int i = 0; i < widths.length; i++) {
            for (int j = 0; j < widths[i]; j++) {
                out.print(headerDivider);
            }
            if (i < widths.length - 1) {
                out.print(columnDivider);
            }
        }
        out.println();

        for (String[] row : rows) {
            out.printf(format, (Object[]) row);
        }
    }

    public TabularOutput limitColumnWidth(int colIndex, int maxWidth) {
        widthLimits[colIndex] = maxWidth;
        return this;
    }

    public TabularOutput trimSuffix(int colIndex, String suffix) {
        trimSuffixes[colIndex] = suffix;
        return this;
    }
}
