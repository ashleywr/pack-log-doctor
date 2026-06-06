package com.sanhiruzu.packlogdoctor.report;

import java.util.List;
import java.util.Map;

public record ReportEnvironment(
        String modpackName,
        String modpackVersion,
        String minecraftVersion,
        String neoForgeVersion,
        String packLogDoctorVersion,
        String javaVersion,
        String javaVendor,
        String osName,
        String osVersion,
        String osArch,
        int availableProcessors,
        long maxMemoryMb,
        long totalMemoryMb,
        long freeMemoryMb,
        Map<String, String> modVersions,
        Map<String, ModDependencySnapshot> modDependencies,
        List<ModFileSnapshot> modFiles,
        List<ModFileChange> modFileChangesSinceLastSuccess,
        List<ModProvenanceSnapshot> modProvenance
) {
    public static ReportEnvironment unknown() {
        return new ReportEnvironment(
                "",
                "",
                "unknown",
                "unknown",
                "unknown",
                System.getProperty("java.version", "unknown"),
                System.getProperty("java.vendor", "unknown"),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", "unknown"),
                System.getProperty("os.arch", "unknown"),
                Runtime.getRuntime().availableProcessors(),
                toMb(Runtime.getRuntime().maxMemory()),
                toMb(Runtime.getRuntime().totalMemory()),
                toMb(Runtime.getRuntime().freeMemory()),
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    public ReportEnvironment(
            String modpackName,
            String modpackVersion,
            String minecraftVersion,
            String neoForgeVersion,
            String packLogDoctorVersion,
            String javaVersion,
            String javaVendor,
            String osName,
            String osVersion,
            String osArch,
            int availableProcessors,
            long maxMemoryMb,
            long totalMemoryMb,
            long freeMemoryMb,
            Map<String, String> modVersions
    ) {
        this(
                modpackName,
                modpackVersion,
                minecraftVersion,
                neoForgeVersion,
                packLogDoctorVersion,
                javaVersion,
                javaVendor,
                osName,
                osVersion,
                osArch,
                availableProcessors,
                maxMemoryMb,
                totalMemoryMb,
                freeMemoryMb,
                modVersions,
                Map.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    public ReportEnvironment(
            String modpackName,
            String modpackVersion,
            String minecraftVersion,
            String neoForgeVersion,
            String packLogDoctorVersion,
            String javaVersion,
            String javaVendor,
            String osName,
            String osVersion,
            String osArch,
            int availableProcessors,
            long maxMemoryMb,
            long totalMemoryMb,
            long freeMemoryMb,
            Map<String, String> modVersions,
            Map<String, ModDependencySnapshot> modDependencies
    ) {
        this(
                modpackName,
                modpackVersion,
                minecraftVersion,
                neoForgeVersion,
                packLogDoctorVersion,
                javaVersion,
                javaVendor,
                osName,
                osVersion,
                osArch,
                availableProcessors,
                maxMemoryMb,
                totalMemoryMb,
                freeMemoryMb,
                modVersions,
                modDependencies,
                List.of(),
                List.of(),
                List.of()
        );
    }

    public ReportEnvironment(
            String modpackName,
            String modpackVersion,
            String minecraftVersion,
            String neoForgeVersion,
            String packLogDoctorVersion,
            String javaVersion,
            String javaVendor,
            String osName,
            String osVersion,
            String osArch,
            int availableProcessors,
            long maxMemoryMb,
            long totalMemoryMb,
            long freeMemoryMb,
            Map<String, String> modVersions,
            Map<String, ModDependencySnapshot> modDependencies,
            List<ModFileSnapshot> modFiles
    ) {
        this(
                modpackName,
                modpackVersion,
                minecraftVersion,
                neoForgeVersion,
                packLogDoctorVersion,
                javaVersion,
                javaVendor,
                osName,
                osVersion,
                osArch,
                availableProcessors,
                maxMemoryMb,
                totalMemoryMb,
                freeMemoryMb,
                modVersions,
                modDependencies,
                modFiles,
                List.of(),
                List.of()
        );
    }

    public ReportEnvironment(
            String modpackName,
            String modpackVersion,
            String minecraftVersion,
            String neoForgeVersion,
            String packLogDoctorVersion,
            String javaVersion,
            String javaVendor,
            String osName,
            String osVersion,
            String osArch,
            int availableProcessors,
            long maxMemoryMb,
            long totalMemoryMb,
            long freeMemoryMb,
            Map<String, String> modVersions,
            Map<String, ModDependencySnapshot> modDependencies,
            List<ModFileSnapshot> modFiles,
            List<ModFileChange> modFileChangesSinceLastSuccess
    ) {
        this(
                modpackName,
                modpackVersion,
                minecraftVersion,
                neoForgeVersion,
                packLogDoctorVersion,
                javaVersion,
                javaVendor,
                osName,
                osVersion,
                osArch,
                availableProcessors,
                maxMemoryMb,
                totalMemoryMb,
                freeMemoryMb,
                modVersions,
                modDependencies,
                modFiles,
                modFileChangesSinceLastSuccess,
                List.of()
        );
    }

    public static long toMb(long bytes) {
        return bytes / 1024L / 1024L;
    }
}
