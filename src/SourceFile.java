public class SourceFile extends SharedFile {
    private final String language;
    private final int lineCount;

    public SourceFile(String name, long size, String language, int lineCount) {
        super(name, size);

        //  fallback values if not provided
        this.language = (language != null && !language.isEmpty()) ? language : "Unknown";
        this.lineCount = (lineCount > 0) ? lineCount : 1; // default to 1 line minimum
    }

    public String getLanguage() {
        return language;
    }

    public int getLineCount() {
        return lineCount;
    }

    @Override
    public String getFileInfo() {
        return "Source Code File: " + name + "\n" +
                "  Size: " + formatFileSize(size) + "\n" +
                "  Language: " + language + "\n" +
                "  Lines: " + lineCount;
    }
}
