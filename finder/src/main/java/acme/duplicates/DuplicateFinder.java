package acme.duplicates;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static acme.duplicates.common.FileHashing.hash;
import static acme.duplicates.common.FileSizes.fileSize;
import static acme.duplicates.common.FileSizes.formatSize;
import static acme.duplicates.common.Formatting.formatBytes;
import static acme.duplicates.common.Formatting.formatDuration;
import static acme.duplicates.common.PathUtils.compileWildcard;
import static acme.duplicates.common.PlanIO.writePlan;

/**
 * Finds duplicate files in a directory tree using a three-phase pipeline:
 * <p>
 * <ol>
 * <li>Phase 1 - Group by file size (metadata only, free)</li>
 * <li>Phase 2 - Group by first-4KB hash (cheap read, eliminates most non-dupes)</li>
 * <li>Phase 3 - Group by full SHA-256 (parallelised; collision probability is effectively zero so binary compare is unnecessary)</li>
 * </ol>
 * <p>
 * Writes a hand-editable .plan file consumed by DuplicateDeleter.
 * <p>
 * Usage:
 * java DuplicateFinder <directory> [threads] [-o <planfile>] [-skip <dir_pattern>] [-skipfile <file_pattern>] ...
 * <p>
 * Examples:
 * <pre>
 * {@code java acme.duplicates.DuplicateFinder C:\Foo}
 * {@code java acme.duplicates.DuplicateFinder C:\Bar 12 -o bar.plan -skip "important*" -skipfile "*.log"}
 * </pre>
 * <p>
 * JVM flags (must precede the source file name):
 * <pre>
 * {@code java -Xms64m -Xmx512m acme.duplicates.DuplicateFinder <directory> ...}
 * </pre>
 */
public class DuplicateFinder {

    private static final int BUFFER_SIZE   = 512 * 1024; // 512 KB
    private static final int QUICK_SIZE    =   4 * 1024; //   4 KB for phase-2 pre-filter
    private static final int PROGRESS_COLS = 50;

    /** Directory names that are always pruned during the file walk. */
    private static final Set<String> SKIP_DIRS = Set.of(
        // Windows user profile noise
        "AppData",
        "Local",
        "LocalLow",
        "Roaming",

        // Windows system
        "Windows",
        "System32",
        "SysWOW64",
        "WinSxS",
        "MUICache",

        // Development / tooling
        ".git",
        ".svn",
        ".hg",
        "node_modules",
        ".gradle",
        ".m2",
        "target",
        "build",
        "dist",
        ".idea",
        ".vs",
        "__pycache__",
        ".mypy_cache",
        "venv",
        ".venv",

        // Temp / cache / system
        "Temp",
        "tmp",
        "$Recycle.Bin",
        "System Volume Information",

        // Additional
        "ArcGIS",
        "Saved Games"
    );

    /** Default directory wildcard patterns to skip */
    private static final String[] DEFAULT_DIR_PATTERNS = {
        "*Facebook_files*",
        "*GIS*",
        "RNL ESRI*",
        "*Class SeptOct 2019",
        "*ProfCertCourses*"
    };

    /** Default file wildcard patterns to skip (e.g., "*.log") */
    private static final String[] DEFAULT_FILE_PATTERNS = {
        "*.class",
        "*.log",
        "thumbs.db",
        ".DS_Store",
        "NTUSER*",
        "autorun.inf",
        "*.ico",
        "OpalViewerLite.exe"
    };

    // Internal compiled pattern lists (Command line args are added to these)
    private static final List<Pattern> SKIP_PATTERNS = new ArrayList<>();
    private static final List<Pattern> SKIP_FILE_PATTERNS = new ArrayList<>();

    // Automatically compile the default string patterns on startup
    static {
        for (String p : DEFAULT_DIR_PATTERNS) {
            SKIP_PATTERNS.add(compileWildcard(p));
        }
        for (String p : DEFAULT_FILE_PATTERNS) {
            SKIP_FILE_PATTERNS.add(compileWildcard(p));
        }
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws InterruptedException, IOException {
        if (args.length < 1) {
            System.err.println("Usage: java acme.duplicates.DuplicateFinder <directory> [threads]"
                    + " [-o <planfile>] [-skip <dir_pattern>] [-skipfile <file_pattern>] ...");
            System.exit(1);
        }

        Path root     = Paths.get(args[0]);
        int  threads  = Runtime.getRuntime().availableProcessors();
        Path planFile = Paths.get("duplicates.plan");

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-o":
                    if (i + 1 < args.length) planFile = Paths.get(args[++i]);
                    break;
                case "-skip":
                    if (i + 1 < args.length) {
                        SKIP_PATTERNS.add(compileWildcard(args[++i]));
                    }
                    break;
                case "-skipfile":
                    if (i + 1 < args.length) {
                        SKIP_FILE_PATTERNS.add(compileWildcard(args[++i]));
                    }
                    break;
                default:
                    try { threads = Integer.parseInt(args[i]); }
                    catch (NumberFormatException e) {
                        System.err.println("Unrecognised argument: " + args[i]);
                        System.exit(1);
                    }
            }
        }

        if (!Files.isDirectory(root)) {
            System.err.println("Not a directory: " + root);
            System.exit(1);
        }

        System.out.println("Scanning:  " + root.toAbsolutePath());
        System.out.println("Threads:   " + threads);
        System.out.println("Plan file: " + planFile.toAbsolutePath());
        System.out.println("Skipping:  " + SKIP_DIRS.stream().sorted()
                .collect(Collectors.joining(", ")));
        if (!SKIP_PATTERNS.isEmpty()) {
            System.out.println("Dir pattern skips applied:  " + SKIP_PATTERNS.size());
        }
        if (!SKIP_FILE_PATTERNS.isEmpty()) {
            System.out.println("File pattern skips applied: " + SKIP_FILE_PATTERNS.size());
        }
        System.out.println();

        // --- Phase 1: group by file size -------------------------------------
        List<Path> allFiles = collectFiles(root);
        System.out.printf("Files found: %,d%n", allFiles.size());

        Map<Long, List<Path>> bySize = groupBySize(allFiles);
        allFiles = null; // release -- no longer needed

        List<List<Path>> sizeCandidates = filterSingletons(bySize);
        bySize = null;

        System.out.printf("Phase 1 (size)    : %,d groups, %,d files remain%n%n",
                sizeCandidates.size(), countFiles(sizeCandidates));

        // --- Phase 2: group by first-4KB hash --------------------------------
        List<List<Path>> quickCandidates = groupByQuickHash(sizeCandidates, threads);
        sizeCandidates = null;

        System.out.printf("Phase 2 (4KB hash): %,d groups, %,d files remain%n%n",
                quickCandidates.size(), countFiles(quickCandidates));

        // --- Phase 3: group by full SHA-256 ----------------------------------
        // SHA-256 collision probability is ~1/2^256. Binary verification is
        // unnecessary; these groups are definitive duplicates.
        List<List<Path>> duplicateGroups = groupByFullHash(quickCandidates, threads);
        quickCandidates = null;

        System.out.printf("Phase 3 (SHA-256) : %,d duplicate groups confirmed%n%n",
                duplicateGroups.size());

        // --- Write plan ------------------------------------------------------
        writePlan(duplicateGroups, planFile);
    }

    // -------------------------------------------------------------------------
    // Phase 1 -- file collection
    // -------------------------------------------------------------------------

    private static List<Path> collectFiles(Path root) {
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(
                        Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null
                            ? "" : dir.getFileName().toString();
                    String absPath = dir.toAbsolutePath().toString();

                    if (SKIP_DIRS.contains(name)) {
                        System.out.println("  Skipping: " + dir.toAbsolutePath());
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    for (Pattern p : SKIP_PATTERNS) {
                        if (p.matcher(name).matches() || p.matcher(absPath).matches()) {
                            System.out.println("  Skipping (pattern): " + absPath);
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(
                        Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        String name = file.getFileName() == null ? "" : file.getFileName().toString();
                        String absPath = file.toAbsolutePath().toString();

                        for (Pattern p : SKIP_FILE_PATTERNS) {
                            if (p.matcher(name).matches() || p.matcher(absPath).matches()) {
                                System.out.println("  Skipping file (pattern): " + absPath);
                                return FileVisitResult.CONTINUE;
                            }
                        }
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    System.err.println("Cannot access: " + file
                            + " -- " + e.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
        }
        return files;
    }

    private static Map<Long, List<Path>> groupBySize(List<Path> files) {
        Map<Long, List<Path>> map = new LinkedHashMap<>();
        for (Path p : files) {
            try {
                map.computeIfAbsent(Files.size(p), k -> new ArrayList<>()).add(p);
            } catch (IOException e) {
                System.err.println("Cannot stat: " + p + " -- " + e.getMessage());
            }
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // Phase 2 -- first-4KB hash pre-filter
    // -------------------------------------------------------------------------

    private static List<List<Path>> groupByQuickHash(
            List<List<Path>> sizeGroups, int threads) throws InterruptedException {
        List<Path> toHash = flatten(sizeGroups);
        String[]   results = hashFiles(toHash, QUICK_SIZE,
                "Quick-hashing (4 KB)", threads);
        return rebuildGroups(sizeGroups, toHash, results);
    }

    // -------------------------------------------------------------------------
    // Phase 3 -- full SHA-256
    // -------------------------------------------------------------------------

    private static List<List<Path>> groupByFullHash(
            List<List<Path>> quickGroups, int threads) throws InterruptedException {
        List<Path> toHash = flatten(quickGroups);
        String[]   results = hashFiles(toHash, Integer.MAX_VALUE,
                "Full SHA-256        ", threads);
        return rebuildGroups(quickGroups, toHash, results);
    }

    // -------------------------------------------------------------------------
    // Shared parallelised hashing engine
    //
    // maxBytes == Integer.MAX_VALUE  -> read entire file
    // maxBytes == QUICK_SIZE         -> read first 4 KB only
    // -------------------------------------------------------------------------

    private static String[] hashFiles(List<Path> files, int maxBytes,
            String label, int threads) throws InterruptedException {

        String[]      results   = new String[files.size()];
        AtomicInteger done      = new AtomicInteger(0);
        AtomicInteger errors    = new AtomicInteger(0);
        AtomicLong    bytesDone = new AtomicLong(0);
        long          startMs   = System.currentTimeMillis();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Thread progress = progressThread(label, done, files.size(), bytesDone, startMs);
        progress.start();

        for (int i = 0; i < files.size(); i++) {
            final int idx = i;
            pool.submit(() -> {
                Path p = files.get(idx);
                try {
                    long sz      = Files.size(p);
                    results[idx] = hash(p, maxBytes);
                    bytesDone.addAndGet(maxBytes == Integer.MAX_VALUE
                            ? sz : Math.min(sz, maxBytes));
                } catch (IOException | NoSuchAlgorithmException e) {
                    errors.incrementAndGet();
                } finally {
                    done.incrementAndGet();
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        progress.interrupt();
        progress.join();
        printProgress(label, files.size(), files.size(),
                bytesDone.get(), startMs, true);
        System.out.println();

        if (errors.get() > 0)
            System.out.printf("  (%,d file(s) skipped due to read errors)%n",
                    errors.get());

        return results;
    }

    private static List<List<Path>> rebuildGroups(
            List<List<Path>> sourceGroups, List<Path> flat, String[] results) {

        Map<Path, String> hashMap = new HashMap<>(flat.size() * 2);
        for (int i = 0; i < flat.size(); i++)
            if (results[i] != null) hashMap.put(flat.get(i), results[i]);

        List<List<Path>> out = new ArrayList<>();
        for (List<Path> group : sourceGroups) {
            Map<String, List<Path>> byHash = new LinkedHashMap<>();
            for (Path p : group) {
                String h = hashMap.get(p);
                if (h != null)
                    byHash.computeIfAbsent(h, k -> new ArrayList<>()).add(p);
            }
            byHash.values().stream()
                    .filter(g -> g.size() > 1)
                    .forEach(out::add);
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Plan writer
    //
    // Format (hand-editable plain text):
    //
    //   GROUP 1  size=1048576  hash=a3f4c2...  copies=3  wasted=2097152
    //   KEEP  C:\path\to\original\file.jpg
    //   DEL   C:\path\to\duplicate\file.jpg
    //   DEL   C:\path\to\duplicate2\file.jpg
    //
    // Rules:
    //   - Lines starting with '#' are comments; ignored by DuplicateDeleter.
    //   - Path on a KEEP/DEL line begins at column 6.
    //   - GROUP lines are informational only; the deleter ignores them.
    //   - size= and wasted= are raw bytes; hash= is full SHA-256 hex.
    //   - The deleter re-verifies the hash of every DEL file before deleting.
    //   - You may swap KEEP <-> DEL lines before running the deleter.
    //   - Delete or comment out an entire GROUP block to skip it.
    // -------------------------------------------------------------------------

    private static void writePlanFile(List<List<Path>> groups, Path planFile)
            throws IOException {

        // Sort: largest wasted space first
        groups.sort((a, b) -> {
            long wa = fileSize(a.get(0)) * (a.size() - 1);
            long wb = fileSize(b.get(0)) * (b.size() - 1);
            return Long.compare(wb, wa);
        });

        long totalWaste = groups.stream()
                .mapToLong(g -> fileSize(g.get(0)) * (g.size() - 1))
                .sum();

        writePlan(groups, planFile);

        System.out.printf("Plan written to  : %s%n", planFile.toAbsolutePath());
        System.out.printf("Groups           : %,d%n", groups.size());
        System.out.printf("Reclaimable      : %s%n%n", formatSize(totalWaste));
        System.out.println("Review the plan, then run:");
        System.out.println("  java DuplicateDeleter.java " + planFile.toAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // Progress
    // -------------------------------------------------------------------------

    private static Thread progressThread(String label, AtomicInteger done,
            int total, AtomicLong bytes, long startMs) {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                printProgress(label, done.get(), total,
                        bytes.get(), startMs, false);
                if (done.get() >= total) break;
                try { Thread.sleep(250); }
                catch (InterruptedException e) { break; }
            }
        });
        t.setDaemon(true);
        return t;
    }

    private static void printProgress(String label, int done, int total,
            long bytes, long startMs, boolean finalLine) {
        double pct     = total == 0 ? 1.0 : (double) done / total;
        int    filled  = (int) (PROGRESS_COLS * pct);
        long   elapsed = System.currentTimeMillis() - startMs;
        String bar     = "["
                + "#".repeat(filled)
                + " ".repeat(PROGRESS_COLS - filled)
                + "]";
        String eta;
        if (done <= 0)          eta = "--";
        else if (done >= total) eta = formatDuration(elapsed);
        else {
            long etaMs = (long) ((elapsed / (double) done) * (total - done));
            eta = formatDuration(etaMs);
        }
        String line = String.format("%s %s %,d/%,d  %s  ETA %-8s",
                label, bar, done, total, formatBytes(bytes), eta);
        System.out.printf("\r%-120s", line);
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------


    private static List<Path> flatten(List<List<Path>> groups) {
        return groups.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private static <K> List<List<Path>> filterSingletons(Map<K, List<Path>> map) {
        return map.values().stream()
                .filter(g -> g.size() > 1)
                .collect(Collectors.toList());
    }

    private static long countFiles(List<List<Path>> groups) {
        return groups.stream().mapToLong(List::size).sum();
    }

}