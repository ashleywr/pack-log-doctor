package com.sanhiruzu.packlogdoctor.runtime;

import com.sanhiruzu.packlogdoctor.analysis.LogIssueAnalyzer;
import com.sanhiruzu.packlogdoctor.analysis.LogFileIssueAnalyzer;
import com.sanhiruzu.packlogdoctor.analysis.LogObservation;
import com.sanhiruzu.packlogdoctor.PackLogDoctor;
import com.sanhiruzu.packlogdoctor.analysis.StartupTimingAnalyzer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.sanhiruzu.packlogdoctor.report.ReportConfig;
import com.sanhiruzu.packlogdoctor.report.DependencySnapshot;
import com.sanhiruzu.packlogdoctor.report.ModFileChange;
import com.sanhiruzu.packlogdoctor.report.ModFileSnapshot;
import com.sanhiruzu.packlogdoctor.report.ModProvenanceSnapshot;
import com.sanhiruzu.packlogdoctor.report.ModDependencySnapshot;
import com.sanhiruzu.packlogdoctor.report.ReportEnvironment;
import com.sanhiruzu.packlogdoctor.report.ReportSnapshot;
import com.sanhiruzu.packlogdoctor.report.ReportWriter;
import com.sanhiruzu.packlogdoctor.report.UncaughtExceptionSnapshot;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class LogDoctorRuntime {
    private static final AtomicReference<LogDoctorRuntime> INSTALLED = new AtomicReference<>();
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (com.google.gson.JsonSerializer<Instant>)
                    (value, type, context) -> new JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(value)))
            .registerTypeAdapter(Instant.class, (com.google.gson.JsonDeserializer<Instant>)
                    (value, type, context) -> Instant.parse(value.getAsString()))
            .setPrettyPrinting()
            .create();

    private final LogIssueAnalyzer analyzer;
    private final StartupTimingAnalyzer startupTimingAnalyzer;
    private final ReportWriter reportWriter;
    private final ReportConfig config;
    private final Path gameDir;
    private final Set<String> loadedModIds;
    private final ReportEnvironment environment;
    private final SuppressionConfig suppressionConfig;
    private final List<UncaughtExceptionSnapshot> uncaughtExceptions = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean writingReport = new AtomicBoolean();
    private final AtomicBoolean terminalReportWritten = new AtomicBoolean();
    private final AtomicBoolean startupReportWritten = new AtomicBoolean();

    private LogDoctorRuntime(Path gameDir, Set<String> loadedModIds, ReportConfig config) {
        this.gameDir = gameDir;
        this.config = config;
        this.loadedModIds = Set.copyOf(loadedModIds);
        this.analyzer = new LogIssueAnalyzer(loadedModIds);
        this.startupTimingAnalyzer = new StartupTimingAnalyzer();
        this.reportWriter = new ReportWriter(gameDir, config);
        this.environment = environmentSnapshot(gameDir, config);
        this.suppressionConfig = SuppressionConfig.load(gameDir.resolve("pack-log-doctor").resolve("suppression-config.json"));
    }

    public static LogDoctorRuntime install() {
        LogDoctorRuntime existing = INSTALLED.get();
        if (existing != null) {
            return existing;
        }

        Path gameDir = FMLPaths.GAMEDIR.get();
        LogDoctorRuntime created = new LogDoctorRuntime(gameDir, loadedModIds(), ReportConfig.loadOrCreate(gameDir));
        if (!INSTALLED.compareAndSet(null, created)) {
            return INSTALLED.get();
        }

        created.installAppender();
        created.installUncaughtExceptionHandler();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> created.writeReport("shutdown"), "PackLogDoctor report writer"));
        return created;
    }

    void observe(LogObservation observation) {
        if (!writingReport.get()) {
            analyzer.observe(observation);
            if (shouldWriteStartupReport(observation)) {
                Thread writer = new Thread(() -> writeReport("startup-complete"), "PackLogDoctor startup report writer");
                writer.setDaemon(true);
                writer.start();
            }
        }
    }

    void observeLogEvent(Instant timestamp, com.sanhiruzu.packlogdoctor.analysis.Severity severity, String level, String loggerName, String threadName, String message, String throwableSummary) {
        if (writingReport.get()) {
            return;
        }
        startupTimingAnalyzer.observe(timestamp, level, loggerName, threadName, message);
        if (severity != null) {
            observe(new LogObservation(timestamp, severity, loggerName, threadName, message, throwableSummary));
        }
    }

    public void writeReport(String trigger) {
        if ("shutdown".equals(trigger) && terminalReportWritten.get()) {
            return;
        }
        if (!writingReport.compareAndSet(false, true)) {
            return;
        }
        try {
            ReportSnapshot snapshot = snapshot(trigger);
            reportWriter.write(snapshot);
            if ("shutdown".equals(trigger) && startupReportWritten.get() && !hasFatalIssue(snapshot)) {
                writeSuccessfulLaunchBaseline(environment.modFiles());
            }
            if ("shutdown".equals(trigger) || "uncaught-exception".equals(trigger)) {
                terminalReportWritten.set(true);
            }
        } catch (Throwable throwable) {
        } finally {
            writingReport.set(false);
        }
    }

    private ReportSnapshot snapshot(String trigger) {
        Path latestLog = gameDir.resolve("logs").resolve("latest.log");
        if (Files.isRegularFile(latestLog)) {
            try {
                LogFileIssueAnalyzer.Result result = new LogFileIssueAnalyzer(loadedModIds).analyze(latestLog);
                return new ReportSnapshot(
                        Instant.now(),
                        trigger + "+latest-log",
                        environment,
                        loadedModIds,
                        result.loudestMods(),
                        startupTimingAnalyzer.snapshot(),
                        uncaughtExceptionSnapshots(),
                        result.issues()
                );
            } catch (Throwable throwable) {
            }
        }
        return new ReportSnapshot(
                Instant.now(),
                trigger,
                environment,
                loadedModIds,
                analyzer.loudestMods(),
                startupTimingAnalyzer.snapshot(),
                uncaughtExceptionSnapshots(),
                analyzer.snapshots()
        );
    }

    private List<UncaughtExceptionSnapshot> uncaughtExceptionSnapshots() {
        synchronized (uncaughtExceptions) {
            return List.copyOf(uncaughtExceptions);
        }
    }

    private boolean hasFatalIssue(ReportSnapshot snapshot) {
        return snapshot.issues().stream()
                .anyMatch(issue -> issue.severity() == com.sanhiruzu.packlogdoctor.analysis.Severity.FATAL);
    }

    public Path htmlReportPath() {
        return reportWriter.htmlPath();
    }

    public Path draftIndexPath() {
        return reportWriter.draftsPath();
    }

    private void installAppender() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        LogDoctorAppender appender = new LogDoctorAppender(this, suppressionConfig);
        appender.start();
        configuration.addAppender(appender);
        configuration.getRootLogger().addAppender(appender, Level.INFO, null);
        for (LoggerConfig loggerConfig : configuration.getLoggers().values()) {
            loggerConfig.addAppender(appender, Level.INFO, null);
        }
        context.updateLoggers();
    }

    private boolean shouldWriteStartupReport(LogObservation observation) {
        if (!Boolean.TRUE.equals(config.writeStartupReport()) || !startupReportWritten.compareAndSet(false, true)) {
            return false;
        }
        String logger = observation.loggerName().toLowerCase(java.util.Locale.ROOT);
        String message = observation.message().toLowerCase(java.util.Locale.ROOT);
        if (logger.contains("modernfix") && message.startsWith("game took ") && message.endsWith(" seconds to start")) {
            return true;
        }
        startupReportWritten.set(false);
        return false;
    }

    private void installUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                Instant timestamp = Instant.now();
                String stackTrace = stackTrace(throwable);
                uncaughtExceptions.add(new UncaughtExceptionSnapshot(
                        timestamp,
                        thread.getName(),
                        throwable.toString(),
                        stackTrace
                ));
                observe(new LogObservation(
                        timestamp,
                        com.sanhiruzu.packlogdoctor.analysis.Severity.FATAL,
                        "uncaught-exception",
                        thread.getName(),
                        throwable.toString(),
                        stackTrace
                ));
                writeReport("uncaught-exception");
            } catch (Throwable reportFailure) {
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        String text = writer.toString();
        return text.length() > 24_000 ? text.substring(0, 24_000) : text;
    }

    private static Set<String> loadedModIds() {
        Set<String> modIds = new TreeSet<>();
        try {
            ModList.get().getMods().forEach(mod -> modIds.add(mod.getModId()));
        } catch (Throwable ignored) {
            modIds.add("unknown");
        }
        return modIds;
    }

    private static ReportEnvironment environmentSnapshot(Path gameDir, ReportConfig config) {
        Runtime runtime = Runtime.getRuntime();
        Map<String, String> modVersions = loadedModVersions();
        Map<String, ModDependencySnapshot> modDependencies = loadedModDependencies(modVersions);
        List<ModFileSnapshot> modFiles = modFiles(gameDir);
        return new ReportEnvironment(
                config.modpackName(),
                config.modpackVersion(),
                minecraftVersion(),
                modVersions.getOrDefault("neoforge", "unknown"),
                modVersions.getOrDefault(PackLogDoctor.MOD_ID, "unknown"),
                System.getProperty("java.version", "unknown"),
                System.getProperty("java.vendor", "unknown"),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", "unknown"),
                System.getProperty("os.arch", "unknown"),
                runtime.availableProcessors(),
                ReportEnvironment.toMb(runtime.maxMemory()),
                ReportEnvironment.toMb(runtime.totalMemory()),
                ReportEnvironment.toMb(runtime.freeMemory()),
                modVersions,
                modDependencies,
                modFiles,
                modFileChangesSinceLastSuccess(gameDir, modFiles),
                modProvenance(gameDir, modFiles, modDependencies)
        );
    }

    private static List<ModProvenanceSnapshot> modProvenance(
            Path gameDir,
            List<ModFileSnapshot> modFiles,
            Map<String, ModDependencySnapshot> dependencies
    ) {
        Map<String, JsonObject> crashAssistantCache = crashAssistantModData(gameDir);
        Map<String, ModDependencySnapshot> dependencyByFile = new LinkedHashMap<>();
        for (ModDependencySnapshot dependency : dependencies.values()) {
            dependencyByFile.putIfAbsent(dependency.fileName(), dependency);
        }

        return modFiles.stream()
                .filter(ModFileSnapshot::enabled)
                .limit(80)
                .map(file -> {
                    JsonObject cached = crashAssistantCache.get(file.fileName());
                    ModDependencySnapshot dependency = dependencyByFile.get(file.fileName());
                    String modId = cachedString(cached, "modId", dependency == null ? "" : dependency.modId());
                    String name = cachedString(cached, "name", dependency == null ? "" : dependency.displayName());
                    String version = cachedString(cached, "version", dependency == null ? "" : dependency.version());
                    String modrinthHash = cachedString(cached, "modrinthHash", "");
                    String curseForgeFingerprint = cachedString(cached, "curseForgeHash", "");
                    boolean hasExactCache = !modrinthHash.isBlank() || !curseForgeFingerprint.isBlank();
                    String confidence = hasExactCache ? "exact local cache" : "local file only";
                    String note = hasExactCache
                            ? "Hash/fingerprint identifies this exact jar in local cache; local timestamps only show when it arrived or changed in this instance."
                            : "No release metadata found locally; timestamps are local filesystem times, not build or release dates.";
                    return new ModProvenanceSnapshot(
                            file.fileName(),
                            modId,
                            name,
                            version,
                            modrinthHash,
                            curseForgeFingerprint,
                            file.createdAt(),
                            file.modifiedAt(),
                            confidence,
                            note
                    );
                })
                .toList();
    }

    private static Map<String, JsonObject> crashAssistantModData(Path gameDir) {
        Path cacheDir = gameDir.resolve("local").resolve("crash_assistant").resolve("mod_data_cache_v4");
        if (!Files.isDirectory(cacheDir)) {
            return Map.of();
        }
        Map<String, JsonObject> byJar = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> paths = Files.list(cacheDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".mod_data.json"))
                    .forEach(path -> {
                        try {
                            JsonObject object = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                            String jarName = cachedString(object, "jarName", "");
                            if (!jarName.isBlank()) {
                                byJar.put(jarName, object);
                            }
                        } catch (Throwable ignored) {
                            // Cache files are optional evidence; report writing must never fail because one is malformed.
                        }
                    });
        } catch (Throwable ignored) {
            return Map.of();
        }
        return byJar;
    }

    private static String cachedString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static List<ModFileChange> modFileChangesSinceLastSuccess(Path gameDir, List<ModFileSnapshot> current) {
        List<ModFileSnapshot> baseline = readSuccessfulLaunchBaseline(gameDir);
        if (baseline.isEmpty()) {
            return List.of();
        }

        Map<String, ModFileSnapshot> previousByKey = new LinkedHashMap<>();
        for (ModFileSnapshot file : baseline) {
            previousByKey.put(logicalModFileName(file.fileName()), file);
        }
        Map<String, ModFileSnapshot> currentByKey = new LinkedHashMap<>();
        for (ModFileSnapshot file : current) {
            currentByKey.put(logicalModFileName(file.fileName()), file);
        }

        java.util.ArrayList<ModFileChange> changes = new java.util.ArrayList<>();
        for (Map.Entry<String, ModFileSnapshot> entry : currentByKey.entrySet()) {
            ModFileSnapshot now = entry.getValue();
            ModFileSnapshot previous = previousByKey.remove(entry.getKey());
            if (previous == null) {
                changes.add(new ModFileChange(now.fileName(), now.enabled() ? "added-enabled" : "added-disabled", "File was not present at the last successful launch."));
                continue;
            }
            if (previous.enabled() != now.enabled()) {
                changes.add(new ModFileChange(now.fileName(), now.enabled() ? "enabled" : "disabled", "Enabled state changed since the last successful launch."));
            }
            if (previous.sizeBytes() != now.sizeBytes()) {
                changes.add(new ModFileChange(now.fileName(), "size-changed", "Size changed from " + previous.sizeBytes() + " to " + now.sizeBytes() + " bytes."));
            }
            if (!previous.fileName().equals(now.fileName()) && previous.enabled() == now.enabled()) {
                changes.add(new ModFileChange(now.fileName(), "renamed", "File name changed from " + previous.fileName() + "."));
            }
        }
        for (ModFileSnapshot removed : previousByKey.values()) {
            changes.add(new ModFileChange(removed.fileName(), "removed", "File was present at the last successful launch but is not present now."));
        }
        return changes;
    }

    private static List<ModFileSnapshot> readSuccessfulLaunchBaseline(Path gameDir) {
        Path path = successfulLaunchBaselinePath(gameDir);
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try {
            ModFileSnapshot[] files = GSON.fromJson(Files.readString(path), ModFileSnapshot[].class);
            return files == null ? List.of() : List.of(files);
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private void writeSuccessfulLaunchBaseline(List<ModFileSnapshot> modFiles) {
        Path path = successfulLaunchBaselinePath(gameDir);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(modFiles));
        } catch (Throwable throwable) {
        }
    }

    private static Path successfulLaunchBaselinePath(Path gameDir) {
        return gameDir.resolve("pack-log-doctor").resolve("state").resolve("last-successful-mod-files.json");
    }

    private static String logicalModFileName(String fileName) {
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".jar.disabled")) {
            return lower.substring(0, lower.length() - ".disabled".length());
        }
        return lower;
    }

    private static List<ModFileSnapshot> modFiles(Path gameDir) {
        Path modsDir = gameDir.resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            return List.of();
        }
        try (java.util.stream.Stream<Path> paths = Files.list(modsDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        return name.endsWith(".jar") || name.endsWith(".jar.disabled");
                    })
                    .map(LogDoctorRuntime::modFileSnapshot)
                    .sorted(Comparator.comparing(ModFileSnapshot::createdAt).reversed()
                            .thenComparing(ModFileSnapshot::modifiedAt, Comparator.reverseOrder())
                            .thenComparing(ModFileSnapshot::fileName))
                    .toList();
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static ModFileSnapshot modFileSnapshot(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            String fileName = path.getFileName().toString();
            return new ModFileSnapshot(
                    fileName,
                    attributes.size(),
                    attributes.creationTime().toInstant(),
                    attributes.lastModifiedTime().toInstant(),
                    !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".disabled")
            );
        } catch (Throwable ignored) {
            String fileName = path.getFileName().toString();
            return new ModFileSnapshot(fileName, 0L, Instant.EPOCH, Instant.EPOCH,
                    !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".disabled"));
        }
    }

    private static String minecraftVersion() {
        try {
            return SharedConstants.getCurrentVersion().getName();
        } catch (Throwable throwable) {
            return "unknown";
        }
    }

    private static Map<String, String> loadedModVersions() {
        Map<String, String> versions = new LinkedHashMap<>();
        try {
            ModList.get().getMods().stream()
                    .sorted((left, right) -> left.getModId().compareToIgnoreCase(right.getModId()))
                    .forEach(mod -> versions.put(mod.getModId(), mod.getVersion().toString()));
        } catch (Throwable ignored) {
            versions.put("unknown", "unknown");
        }
        return versions;
    }

    private static Map<String, ModDependencySnapshot> loadedModDependencies(Map<String, String> modVersions) {
        Map<String, ModDependencySnapshot> dependencies = new LinkedHashMap<>();
        try {
            ModList.get().getMods().stream()
                    .sorted((left, right) -> left.getModId().compareToIgnoreCase(right.getModId()))
                    .forEach(mod -> dependencies.put(mod.getModId(), dependencySnapshot(mod, modVersions)));
        } catch (Throwable ignored) {
            dependencies.put("unknown", new ModDependencySnapshot("unknown", "unknown", "unknown", "unknown", "unknown", List.of()));
        }
        return dependencies;
    }

    private static ModDependencySnapshot dependencySnapshot(IModInfo mod, Map<String, String> modVersions) {
        return new ModDependencySnapshot(
                mod.getModId(),
                mod.getDisplayName(),
                mod.getVersion().toString(),
                mod.getOwningFile().getFile().getFileName(),
                mod.getOwningFile().getFile().getType().name(),
                configString(mod, "issueTrackerURL"),
                configString(mod, "displayURL"),
                configString(mod, "updateJSONURL"),
                mod.getDependencies().stream()
                        .map(dependency -> dependencySnapshot(dependency, modVersions))
                        .toList()
        );
    }

    private static String configString(IModInfo mod, String key) {
        try {
            return ((IModFileInfo) mod.getOwningFile()).getConfig()
                    .<String>getConfigElement(key)
                    .map(Object::toString)
                    .orElse("");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static DependencySnapshot dependencySnapshot(IModInfo.ModVersion dependency, Map<String, String> modVersions) {
        String installedVersion = modVersions.getOrDefault(dependency.getModId(), "");
        return new DependencySnapshot(
                dependency.getModId(),
                dependency.getVersionRange().toString(),
                dependency.getType().name(),
                dependency.getSide().name(),
                dependency.getOrdering().name(),
                installedVersion,
                dependencyStatus(dependency, installedVersion),
                dependency.getReferralURL().map(Object::toString).orElse("")
        );
    }

    private static String dependencyStatus(IModInfo.ModVersion dependency, String installedVersion) {
        boolean installed = !installedVersion.isBlank();
        return switch (dependency.getType()) {
            case REQUIRED -> {
                if (!installed) {
                    yield "missing";
                }
                yield dependency.getVersionRange().containsVersion(new org.apache.maven.artifact.versioning.DefaultArtifactVersion(installedVersion))
                        ? "present"
                        : "wrong-version";
            }
            case OPTIONAL -> installed ? "optional-present" : "optional-missing";
            case INCOMPATIBLE -> installed ? "incompatible-present" : "not-present";
            case DISCOURAGED -> installed ? "discouraged-present" : "not-present";
        };
    }
}
