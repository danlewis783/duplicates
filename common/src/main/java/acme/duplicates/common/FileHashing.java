package acme.duplicates.common;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class FileHashing {
    private static final int BUFFER_SIZE = 512 * 1024;

    private FileHashing() {
    }

    public static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        return hash(path, Integer.MAX_VALUE);
    }

    public static String hash(Path path, int maxBytes)
            throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[BUFFER_SIZE];
        long remaining = maxBytes == Integer.MAX_VALUE ? Long.MAX_VALUE : maxBytes;

        try (InputStream in = new BufferedInputStream(Files.newInputStream(path), BUFFER_SIZE)) {
            int n;
            while (remaining > 0
                    && (n = in.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                digest.update(buf, 0, n);
                remaining -= n;
            }
        }

        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}