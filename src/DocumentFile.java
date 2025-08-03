public class DocumentFile extends SharedFile {
    private final int pageCount;

    public DocumentFile(String name, long size, int pageCount) {
        super(name, size);
        // ensuring at least one page
        if (pageCount > 0) {
            this.pageCount = pageCount;
        } else {
            this.pageCount = 1;
        }

    }

    public int getPageCount() {
        return pageCount;
    }

    @Override
    public String getFileInfo() {
        return "Document File: " + name + "\n" +
                "  Size: " + formatFileSize(size) + "\n" +
                "  Pages: " + pageCount;
    }
}