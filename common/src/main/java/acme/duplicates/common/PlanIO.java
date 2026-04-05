package acme.duplicates.common;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static acme.duplicates.common.FileHashing.hash;
import static acme.duplicates.common.FileSizes.fileSize;
import static acme.duplicates.common.FileSizes.formatSize;

public final class PlanIO {
    private PlanIO() {}

    public static List<DuplicateGroup> readPlan(Path planFile) throws IOException {
        List<DuplicateGroup> groups = new ArrayList<>();
        DuplicateGroup current = null;

        try (BufferedReader br = new BufferedReader(new FileReader(planFile.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;

                if (line.startsWith("GROUP ")) {
                    current = parseGroupLine(line);
                    if (current != null) groups.add(current);
                } else if (line.startsWith("KEEP  ") && current != null) {
                    current.setKeep(Paths.get(line.substring(6)));
                } else if (line.startsWith("DEL   ") && current != null) {
                    current.getDel().add(Paths.get(line.substring(6)));
                }
            }
        }

        // Validate: drop groups that have no KEEP or no DEL entries
        groups.removeIf(g -> g.getKeep() == null || g.getDel().isEmpty());

        return groups;
    }

    private static DuplicateGroup parseGroupLine(String line) {
        DuplicateGroup g = new DuplicateGroup();
        try {
            String[] tokens = line.split("\\s+");
            g.setNumber(Integer.parseInt(tokens[1]));
            for (String token : tokens) {
                if (token.startsWith("size=")) g.setSize(Long.parseLong(token.substring(5)));
                if (token.startsWith("hash=")) g.setHash(token.substring(5));
            }
        } catch (Exception e) {
            return null;
        }
        return g;
    }

    public static void writePlan(List<List<Path>> groups, Path planFile) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(planFile.toFile())))) {
            pw.println("# Duplicate Search Plan");
            pw.println("# Generated on: " + new java.util.Date());
            pw.println("# -------------------------------------------------------------------------");

            int groupNum = 0;
            long totalWaste = 0;

            for (List<Path> group : groups) {
                groupNum++;
                long size = fileSize(group.get(0));
                long waste = size * (group.size() - 1);
                totalWaste += waste;

                String hash = "unknown";
                try {
                    hash = hash(group.get(0), Integer.MAX_VALUE);
                } catch (Exception ignored) {
                }

                pw.printf("%nGROUP %d  size=%d  hash=%s  copies=%d  wasted=%d%n",
                        groupNum, size, hash, group.size(), waste);

                // Sort using the same logic as before (caller should have sorted, but we ensure it here if needed)
                // Actually, DuplicateFinder does the sorting before calling writePlan usually.
                // To keep it identical, we'll assume the list is already sorted or sort it here.
                // DuplicateFinder sorts it in place.

                pw.println("KEEP  " + group.get(0).toAbsolutePath());
                for (int i = 1; i < group.size(); i++) {
                    pw.println("DEL   " + group.get(i).toAbsolutePath());
                }
            }

            pw.println();
            pw.printf("# Total groups    : %,d%n", groupNum);
            pw.printf("# Total reclaimable: %s (%,d bytes)%n",
                    formatSize(totalWaste), totalWaste);
        }
    }
}
