public class ImageFile extends SharedFile {
    private final int  width;
    private final int height;

    public ImageFile(String name, long size, int width, int height) {
        super(name, size);

        // default dimensions if invalid values are provided
        this.width = (width > 0) ? width : 100;
        this.height = (height > 0) ? height : 100;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public String getFileInfo() {
        return "Image File: " + name + "\n" +
                "  Size: " + formatFileSize(size) + "\n" +
                "  Dimensions: " + width + " x " + height + " pixels";
    }
}
