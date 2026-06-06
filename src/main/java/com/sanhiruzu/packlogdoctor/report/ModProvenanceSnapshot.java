package com.sanhiruzu.packlogdoctor.report;

import java.time.Instant;

public record ModProvenanceSnapshot(
        String fileName,
        String modId,
        String displayName,
        String version,
        String modrinthHash,
        String curseForgeFingerprint,
        Instant createdAt,
        Instant modifiedAt,
        String confidence,
        String note
) {
}
