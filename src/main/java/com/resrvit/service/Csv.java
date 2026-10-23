package com.resrvit.service;

import java.util.ArrayList;
import java.util.List;

/** Minimal CSV helpers: quoted fields, doubled quotes, and escaping for output. */
public final class Csv {

    private Csv() {}

    public static String[] split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    public static String col(String[] c, int i) {
        return i < c.length && c[i] != null ? c[i].trim() : "";
    }

    public static String escape(Object v) {
        String s = v == null ? "" : v.toString();
        // Guard against spreadsheet formula injection.
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
