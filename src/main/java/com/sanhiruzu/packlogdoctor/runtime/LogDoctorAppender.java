package com.sanhiruzu.packlogdoctor.runtime;

import com.sanhiruzu.packlogdoctor.analysis.LogObservation;
import com.sanhiruzu.packlogdoctor.analysis.Severity;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

final class LogDoctorAppender extends AbstractAppender {
    private final LogDoctorRuntime runtime;
    private final SuppressionConfig suppressionConfig;

    LogDoctorAppender(LogDoctorRuntime runtime, SuppressionConfig suppressionConfig) {
        super("PackLogDoctorAppender", null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
        this.runtime = runtime;
        this.suppressionConfig = suppressionConfig;
    }

    @Override
    public void append(LogEvent event) {
        String loggerName = event.getLoggerName();
        String message = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();

        if (suppressionConfig.isSuppressed(loggerName, message)) {
            return;
        }

        Severity severity = severityOf(event.getLevel());
        runtime.observeLogEvent(
                Instant.ofEpochMilli(event.getTimeMillis()),
                severity,
                event.getLevel() == null ? "" : event.getLevel().name(),
                loggerName,
                event.getThreadName(),
                message,
                throwableSummary(event.getThrown())
        );
    }

    private Severity severityOf(Level level) {
        if (level == null) {
            return null;
        }
        if (level.isMoreSpecificThan(Level.FATAL)) {
            return Severity.FATAL;
        }
        if (level.isMoreSpecificThan(Level.ERROR)) {
            return Severity.ERROR;
        }
        if (level.isMoreSpecificThan(Level.WARN)) {
            return Severity.WARN;
        }
        return null;
    }

    private String throwableSummary(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        String text = writer.toString();
        return text.length() > 16_000 ? text.substring(0, 16_000) : text;
    }
}
