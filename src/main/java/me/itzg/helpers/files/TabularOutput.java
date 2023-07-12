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

    public TabularOutput(char headerDivider, String columnDivider, String... headers) {
        this.headerDivider = headerDivider;
        this.columnDivider = columnDivider;
        this.headers = headers;
        this.widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
    }

    public void addRow(String... cells) {
        if (cells.length != headers.length) {
            throw new IllegalArgumentException(String.format("Row has %d columns but header has %d", cells.length, headers.length));
        }

        rows.add(cells);

        for (int i = 0; i < cells.length; i++) {
            widths[i] = Math.max(widths[i], cells[i].length());
        }
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
}
