public class AudioFile extends SharedFile {
    private String artist;
    private String album;
    private int durationSeconds;

    public AudioFile(String name, long size, String artist, String album, int durationSeconds) {
        super(name, size);

        // Use default values if artist or album is null/empty
        this.artist = (artist != null && !artist.isEmpty()) ? artist : "Unknown Artist";
        this.album = (album != null && !album.isEmpty()) ? album : "Unknown Album";

        // Set duration in seconds (assumes valid input)
        this.durationSeconds = durationSeconds;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    @Override
    public String getFileInfo() {
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;

        return "Audio File: " + name + "\n" +
                "  Size: " + formatFileSize(size) + "\n" +
                "  Artist: " + artist + "\n" +
                "  Album: " + album + "\n" +
                "  Duration: " + minutes + ":" + String.format("%02d", seconds);
    }
}
