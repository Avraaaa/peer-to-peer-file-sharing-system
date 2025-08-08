public abstract class User {

    private final String username;
    private final String passwordHash;
    private final String sharedDirectory;
    private final DownloadStats downloadStats;
    private final UploadStats uploadStats;

    public User(String username, String passwordHash, String sharedDirectory, DownloadStats downloadStats, UploadStats uploadStats) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.sharedDirectory = sharedDirectory;
        this.downloadStats = downloadStats;
        this.uploadStats = uploadStats;
    }

    public String getUsername(){
        return username;
    }
    public String getPasswordHash(){
        return passwordHash;
    }

    public String getSharedDirectory(){
        return sharedDirectory;
    }

    public abstract boolean isAdmin();

    public DownloadStats getDownloadStats() {
        return downloadStats;
    }

    public UploadStats getUploadStats() {
        return uploadStats;
    }

}
