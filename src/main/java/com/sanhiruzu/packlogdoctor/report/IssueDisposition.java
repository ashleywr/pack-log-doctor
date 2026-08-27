package com.sanhiruzu.packlogdoctor.report;

public record IssueDisposition(String status, String note) {
    public IssueDisposition {
        status = clean(status, "unexpected");
        note = clean(note, "");
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.strip();
    }

    public boolean isUnexpected() {
        return "unexpected".equals(status);
    }
}
