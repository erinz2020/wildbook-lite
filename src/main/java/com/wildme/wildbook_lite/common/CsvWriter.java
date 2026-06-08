package com.wildme.wildbook_lite.common;

/**
 * Minimal RFC-4180 CSV escaper. Wraps a field in quotes only when it
 * contains a comma, double-quote, CR, or LF, and doubles internal quotes.
 *
 * Why hand-rolled instead of pulling Jackson-CSV or OpenCSV: keep zero
 * extra deps; export is small enough.
 */
public final class CsvWriter {

    private CsvWriter() {}

    public static String row(Object... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(fields[i]));
        }
        return sb.toString();
    }

    public static String escape(Object value) {
        if (value == null) return "";
        String s = value.toString();
        boolean needsQuote = s.indexOf(',') >= 0
                          || s.indexOf('"') >= 0
                          || s.indexOf('\n') >= 0
                          || s.indexOf('\r') >= 0;
        if (!needsQuote) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
