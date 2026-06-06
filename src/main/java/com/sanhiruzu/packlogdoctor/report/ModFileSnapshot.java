package com.sanhiruzu.packlogdoctor.report;

import java.time.Instant;

public record ModFileSnapshot(
        String fileName,
        long sizeBytes,
        Instant createdAt,
        Instant modifiedAt,
        boolean enabled
) {
}
