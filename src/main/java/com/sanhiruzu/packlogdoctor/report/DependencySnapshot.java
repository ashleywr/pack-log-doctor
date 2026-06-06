package com.sanhiruzu.packlogdoctor.report;

public record DependencySnapshot(
        String modId,
        String versionRange,
        String type,
        String side,
        String ordering,
        String installedVersion,
        String status,
        String referralUrl
) {
}
