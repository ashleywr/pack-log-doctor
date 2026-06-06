package com.sanhiruzu.packlogdoctor.report;

import java.util.List;

public record ModDependencySnapshot(
        String modId,
        String displayName,
        String version,
        String fileName,
        String fileType,
        String issueTrackerUrl,
        String displayUrl,
        String updateUrl,
        List<DependencySnapshot> dependencies
) {
    public ModDependencySnapshot(
            String modId,
            String displayName,
            String version,
            String fileName,
            String fileType,
            List<DependencySnapshot> dependencies
    ) {
        this(modId, displayName, version, fileName, fileType, "", "", "", dependencies);
    }
}
