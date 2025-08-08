public interface Stats {

    void addFile();
    void addBytes(long bytes);
    long getFileCount();
    long getTotalBytes();
    String toCsvString();
    void fromCsvString(String csv);

}
