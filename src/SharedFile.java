import java.text.DecimalFormat;

public abstract class SharedFile {
    protected String name;
    protected long size; // Size in bytes

    public SharedFile(String name, long size) {
        this.name = name;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public abstract String getFileInfo();
    
    protected String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";

        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int index = (int) (Math.log10(bytes) / Math.log10(1024));
        double sizeInUnit = bytes / Math.pow(1024, index);

        DecimalFormat formatter = new DecimalFormat("#,##0.#");
        return formatter.format(sizeInUnit) + " " + units[index];
    }
}
