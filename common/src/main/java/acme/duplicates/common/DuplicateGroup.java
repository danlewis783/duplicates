package acme.duplicates.common;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DuplicateGroup {
    private int number;
    private long size;
    private String hash;
    private Path keep;
    private List<Path> del = new ArrayList<>();

    public DuplicateGroup() {}

    public DuplicateGroup(int number, long size, String hash) {
        this.number = number;
        this.size = size;
        this.hash = hash;
    }

    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }

    public Path getKeep() { return keep; }
    public void setKeep(Path keep) { this.keep = keep; }

    public List<Path> getDel() { return del; }
    public void setDel(List<Path> del) { this.del = del; }
}
