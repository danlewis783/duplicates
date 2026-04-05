package acme.duplicates.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileSizes {
    private FileSizes() {
    }

    public static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1_024) return bytes + " B";
        if (bytes < 1_048_576) return String.format("%.1f KB", bytes / 1_024.0);
        if (bytes < 1_073_741_824) return String.format("%.1f MB", bytes / 1_048_576.0);
        return String.format("%.2f GB", bytes / 1_073_741_824.0);
    }
}