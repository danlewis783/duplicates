package acme.duplicates.common;

import java.nio.file.Path;
import java.util.regex.Pattern;

public final class PathUtils {
    private PathUtils() {}

    /** Translates basic file wildcards (*, ?) into regex patterns */
    public static Pattern compileWildcard(String wildcard) {
        String regex = "(?i)" + wildcard.replace("\\", "\\\\") // Escape Windows path slashes
                                        .replace(".", "\\.")   // Escape literal dots
                                        .replace("*", ".*")    // * matches any characters
                                        .replace("?", ".");    // ? matches a single character
        return Pattern.compile(regex);
    }

    /**
     * Scores a file path to determine how "good" of a keeper it is.
     * Higher score = better candidate to KEEP.
     */
    public static int scoreFile(Path p) {
        int score = 0;
        String fileName = p.getFileName().toString();
        String nameLower = fileName.toLowerCase();

        // 1. Penalize generic auto-generated camera/system names (-1000 pts)
        if (nameLower.matches("^(img|dsc|vid|wp|screenshot|untitled|mvimg|pxl)[_\\-]?\\d+.*")) {
            score -= 1000;
        }

        // 2. Penalize duplicate indicators like " (1)" or " - copy" (-500 pts)
        if (nameLower.matches(".*\\(\\d+\\)\\.[a-z0-9]+$") || nameLower.contains(" - copy")) {
            score -= 500;
        }

        // 3. Reward name length (longer names are usually hand-typed and descriptive)
        // Cap the reward at 50 chars so ridiculously long names don't break the logic
        String nameWithoutExt = fileName.replaceFirst("[.][^.]+$", "");
        score += Math.min(nameWithoutExt.length(), 50) * 5;

        // 4. Penalize deeply nested directories (-20 pts per folder depth)
        score -= p.getNameCount() * 20;

        return score;
    }
}
