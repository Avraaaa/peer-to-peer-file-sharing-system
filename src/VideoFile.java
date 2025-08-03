public class VideoFile extends SharedFile {
    private final String resolution;
    private final String codec;
    private final int durationSeconds;

    public VideoFile(String name, long size, String resolution, String codec, int durationSeconds) {
        super(name, size);

        // default values if resolution or codec is missing
        this.resolution = (resolution != null && !resolution.isEmpty()) ? resolution : "N/A";
        this.codec = (codec != null && !codec.isEmpty()) ? codec : "Unknown";
        this.durationSeconds = durationSeconds;
    }

    public String getResolution() {
        return resolution;
    }

    public String getCodec() {
        return codec;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    @Override
    public String getFileInfo() {
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;

        return "Video File: " + name + "\n" +
                "  Size: " + formatFileSize(size) + "\n" +
                "  Resolution: " + resolution + "\n" +
                "  Codec: " + codec + "\n" +
                "  Duration: " + minutes + ":" + String.format("%02d", seconds);
    }
}
