package com.sanhiruzu.packlogdoctor.analysis;

public record FixSuggestion(String safety, String title, String detail, String url, String actionLabel, String actionBody) {
    public FixSuggestion(String safety, String title, String detail) {
        this(safety, title, detail, "", "", "");
    }
}
