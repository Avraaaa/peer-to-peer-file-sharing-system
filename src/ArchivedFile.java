public class ArchivedFile extends SharedFile {
    private int fileCount;

    public ArchivedFile(String name, long size, int fileCount) {
        super(name, size);
        this.fileCount = fileCount;
    }

    @Override
    public String getFileInfo() {
        return "Archive File: " + name + "\n"
                + "  Size: " + formatFileSize(size) + "\n"
                + "  Contains: " + fileCount + " files";
    }
}
