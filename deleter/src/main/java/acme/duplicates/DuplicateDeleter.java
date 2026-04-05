package acme.duplicates;

import acme.duplicates.common.DuplicateGroup;
import acme.duplicates.common.PlanIO;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static acme.duplicates.common.FileHashing.sha256;
import static acme.duplicates.common.FileSizes.fileSize;
import static acme.duplicates.common.FileSizes.formatSize;

/**
 * Reads a .plan file produced by DuplicateFinder and deletes the DEL files.
 * <p>
 * Safety rules (all enforced unconditionally):
 * <ol>
 *   <li>1. The KEEP file must exist before any DEL in its group is touched.</li>
 *   <li>2. Every DEL file is re-hashed and must match the hash in the GROUP line.</li>
 *   <li>3. A DEL file that fails verification is skipped, not deleted.</li>
 *   <li>4. A full log of every action is written alongside the plan file.</li>
 * </ol>
 * <p>
 * Usage:
 * <pre>
 * {@code java acme.duplicates.DuplicateDeleter <planfile> [--dry-run]}
 * where
 * {@code --dry-run} prints every action without deleting anything.
 * </pre>
 */
public class DuplicateDeleter {

    private static final int BUFFER_SIZE = 512 * 1024;


    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java acme.duplicates.DuplicateDeleter <planfile> [--dry-run]");
            System.exit(1);
        }

        Path planFile = Paths.get(args[0]);
        boolean dryRun = args.length >= 2 && "--dry-run".equals(args[1]);

        if (!Files.exists(planFile)) {
            System.err.println("Plan file not found: " + planFile);
            System.exit(1);
        }

        Path logFile = planFile.resolveSibling(
                planFile.getFileName().toString().replaceFirst("\\.plan$", "")
                        + ".delete.log");

        System.out.println("Plan file: " + planFile.toAbsolutePath());
        System.out.println("Log file:  " + logFile.toAbsolutePath());
        System.out.println("Dry run:   " + dryRun);
        System.out.println();

        List<DuplicateGroup> groups = PlanIO.readPlan(planFile);
        System.out.printf("Groups loaded: %,d%n%n", groups.size());

        process(groups, logFile, dryRun);
    }


    // -------------------------------------------------------------------------
    // Processing
    // -------------------------------------------------------------------------

    private static void process(List<DuplicateGroup> groups, Path logFile, boolean dryRun)
            throws IOException {

        long deletedBytes  = 0;
        int  deletedFiles  = 0;
        int  skippedFiles  = 0;

        try (PrintWriter log = new PrintWriter(
                new BufferedWriter(new FileWriter(logFile.toFile())))) {

            log.println("# DuplicateDeleter log  " + new java.util.Date());
            log.println("# dry-run=" + dryRun);
            log.println();

            for (DuplicateGroup g : groups) {
                log.printf("GROUP %d  size=%d  hash=%s%n", g.getNumber(), g.getSize(), g.getHash());

                // Rule 1: KEEP file must exist
                if (!Files.exists(g.getKeep())) {
                    String msg = "SKIP-NO-KEEP  " + g.getKeep();
                    System.out.println(msg);
                    log.println(msg);
                    for (Path d : g.getDel()) {
                        String m2 = "SKIP-NO-KEEP  " + d;
                        System.out.println(m2);
                        log.println(m2);
                        skippedFiles++;
                    }
                    continue;
                }

                log.println("KEEP  " + g.getKeep().toAbsolutePath());

                for (Path del : g.getDel()) {
                    // Rule 2: re-verify hash
                    if (!Files.exists(del)) {
                        String msg = "SKIP-MISSING  " + del.toAbsolutePath();
                        System.out.println(msg);
                        log.println(msg);
                        skippedFiles++;
                        continue;
                    }

                    String actualHash;
                    try {
                        actualHash = sha256(del);
                    } catch (IOException | NoSuchAlgorithmException e) {
                        String msg = "SKIP-HASH-ERR  " + del.toAbsolutePath()
                                + "  (" + e.getMessage() + ")";
                        System.out.println(msg);
                        log.println(msg);
                        skippedFiles++;
                        continue;
                    }

                    // Rule 3: hash mismatch -> skip
                    if (!actualHash.equals(g.getHash())) {
                        String msg = "SKIP-HASH-MISMATCH  " + del.toAbsolutePath();
                        System.out.println(msg);
                        log.println(msg);
                        skippedFiles++;
                        continue;
                    }

                    long sz = fileSize(del);

                    if (dryRun) {
                        String msg = "DRY-RUN-DEL  " + del.toAbsolutePath();
                        System.out.println(msg);
                        log.println(msg);
                        deletedFiles++;
                        deletedBytes += sz;
                    } else {
                        try {
                            Files.delete(del);
                            String msg = "DELETED  " + del.toAbsolutePath();
                            System.out.println(msg);
                            log.println(msg);
                            deletedFiles++;
                            deletedBytes += sz;
                        } catch (IOException e) {
                            String msg = "SKIP-DELETE-ERR  " + del.toAbsolutePath()
                                    + "  (" + e.getMessage() + ")";
                            System.out.println(msg);
                            log.println(msg);
                            skippedFiles++;
                        }
                    }
                }

                log.println();
            }

            // Summary
            String action = dryRun ? "Would delete" : "Deleted";
            System.out.println();
            System.out.printf("%s %,d file(s), %s%n",
                    action, deletedFiles, formatSize(deletedBytes));
            System.out.printf("Skipped: %,d file(s)%n", skippedFiles);
            System.out.println("Log: " + logFile.toAbsolutePath());

            log.printf("%n# %s %,d file(s), %s%n", action, deletedFiles, formatSize(deletedBytes));
            log.printf("# Skipped: %,d%n", skippedFiles);
        }
    }
}
