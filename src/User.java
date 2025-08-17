public abstract class User {

    private final String username;
    private final String passwordHash;
    private final DownloadStats downloadStats;
    private final UploadStats uploadStats;

    public User(String username, String passwordHash, DownloadStats downloadStats, UploadStats uploadStats) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.downloadStats = downloadStats;
        this.uploadStats = uploadStats;
    }

    public String getUsername(){
        return username;
    }

    public String getPasswordHash(){
        return passwordHash;
    }

    public abstract String getSharedDirectory();

    public abstract boolean isAdmin();

    public DownloadStats getDownloadStats() {
        return downloadStats;
    }

    public UploadStats getUploadStats() {
        return uploadStats;
    }
}