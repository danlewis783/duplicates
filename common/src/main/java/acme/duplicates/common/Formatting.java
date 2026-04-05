package acme.duplicates.common;

public final class Formatting {
    private Formatting() {}

    public static String formatBytes(long b) {
        if (b < 1_048_576)     return String.format("%,.1f KB", b / 1_024.0);
        if (b < 1_073_741_824) return String.format("%,.1f MB", b / 1_048_576.0);
        return                 String.format("%,.2f GB", b / 1_073_741_824.0);
    }

    public static String formatDuration(long ms) {
        if (ms < 60_000) return (ms / 1000) + "s";
        return String.format("%dm%ds", ms / 60_000, (ms % 60_000) / 1000);
    }
}
