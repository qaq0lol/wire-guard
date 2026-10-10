package com.proxy.wireopen.util;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Log manager providing structured in-memory log buffer, level filtering,
 * and weak-reference listener registration for real-time console rendering.
 */
public class LogManager {
    public static final int MAX_LOG_LINES = 2000;

    public enum Level {
        DEBUG("DEBUG", 0),
        INFO("INFO", 1),
        WARN("WARN", 2),
        ERROR("ERROR", 3);

        private final String label;
        private final int priority;

        Level(String label, int priority) {
            this.label = label;
            this.priority = priority;
        }

        public String getLabel() {
            return label;
        }

        public int getPriority() {
            return priority;
        }
    }

    public static class LogEntry {
        private final long timestampMillis;
        private final String timeFormatted;
        private final Level level;
        private final String tag;
        private final String message;
        private final String formattedLine;

        public LogEntry(long timestampMillis, String timeFormatted, Level level, String tag, String message) {
            this.timestampMillis = timestampMillis;
            this.timeFormatted = timeFormatted;
            this.level = level != null ? level : Level.INFO;
            this.tag = tag != null ? tag : "APP";
            this.message = message != null ? message : "";
            this.formattedLine = "[" + timeFormatted + "] [" + this.level.getLabel() + "] [" + this.tag + "] " + this.message;
        }

        public long getTimestampMillis() {
            return timestampMillis;
        }

        public String getTimeFormatted() {
            return timeFormatted;
        }

        public Level getLevel() {
            return level;
        }

        public String getTag() {
            return tag;
        }

        public String getMessage() {
            return message;
        }

        public String getFormattedLine() {
            return formattedLine;
        }

        @Override
        public String toString() {
            return formattedLine;
        }
    }

    private static final List<LogEntry> logEntries = new ArrayList<>();
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private static WeakReference<LogListener> listenerRef = null;

    public interface LogListener {
        void onNewLog(String logEntry);
        default void onNewEntry(LogEntry entry) {
            onNewLog(entry != null ? entry.getFormattedLine() : "");
        }
    }

    public static synchronized void setListener(LogListener l) {
        listenerRef = (l != null) ? new WeakReference<>(l) : null;
    }

    public static synchronized void log(String tag, String message) {
        log(Level.INFO, tag, message);
    }

    public static synchronized void debug(String tag, String message) {
        log(Level.DEBUG, tag, message);
    }

    public static synchronized void info(String tag, String message) {
        log(Level.INFO, tag, message);
    }

    public static synchronized void warn(String tag, String message) {
        log(Level.WARN, tag, message);
    }

    public static synchronized void error(String tag, String message) {
        log(Level.ERROR, tag, message);
    }

    public static synchronized void log(Level level, String tag, String message) {
        long now = System.currentTimeMillis();
        String timeStr = timeFormat.format(new Date(now));
        LogEntry entry = new LogEntry(now, timeStr, level, tag, message);

        if (logEntries.size() >= MAX_LOG_LINES) {
            logEntries.remove(0);
        }
        logEntries.add(entry);

        if (listenerRef != null) {
            LogListener l = listenerRef.get();
            if (l != null) {
                try {
                    l.onNewEntry(entry);
                } catch (Exception ignored) {}
            } else {
                listenerRef = null;
            }
        }
    }

    public static synchronized List<String> getLogs() {
        List<String> list = new ArrayList<>(logEntries.size());
        for (LogEntry e : logEntries) {
            list.add(e.getFormattedLine());
        }
        return list;
    }

    public static synchronized List<LogEntry> getEntries() {
        return new ArrayList<>(logEntries);
    }

    public static synchronized List<LogEntry> getFilteredEntries(Level minLevel, String keyword) {
        List<LogEntry> result = new ArrayList<>();
        int minPriority = (minLevel != null) ? minLevel.getPriority() : 0;
        String kw = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim().toLowerCase(Locale.ROOT) : null;

        for (LogEntry e : logEntries) {
            if (e.getLevel().getPriority() < minPriority) {
                continue;
            }
            if (kw != null) {
                boolean matchTag = e.getTag().toLowerCase(Locale.ROOT).contains(kw);
                boolean matchMsg = e.getMessage().toLowerCase(Locale.ROOT).contains(kw);
                if (!matchTag && !matchMsg) {
                    continue;
                }
            }
            result.add(e);
        }
        return result;
    }

    public static synchronized void clear() {
        logEntries.clear();
    }
}
