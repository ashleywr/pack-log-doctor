package com.sanhiruzu.packlogdoctor.analysis;

public enum Severity {
    WARN(1),
    ERROR(2),
    FATAL(3);

    private final int weight;

    Severity(int weight) {
        this.weight = weight;
    }

    public boolean isAtLeast(Severity other) {
        return weight >= other.weight;
    }

    public static Severity max(Severity left, Severity right) {
        return left.weight >= right.weight ? left : right;
    }
}
