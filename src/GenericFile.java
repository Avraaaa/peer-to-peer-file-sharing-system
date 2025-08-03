public class GenericFile extends SharedFile {

    public GenericFile(String name, long size) {
        super(name, size);
    }

    @Override
    public String getFileInfo() {
        return "Generic File: " + name + "\n" +
                "  Size: " + formatFileSize(size);
    }
}

