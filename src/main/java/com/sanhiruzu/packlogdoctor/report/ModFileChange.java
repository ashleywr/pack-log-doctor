package com.sanhiruzu.packlogdoctor.report;

public record ModFileChange(
        String fileName,
        String changeType,
        String detail
) {
}
