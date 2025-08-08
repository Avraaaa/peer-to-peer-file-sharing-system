public class RegularUser extends User {

    public RegularUser(String username, String passwordHash, String sharedDirectory) {
        super(username, passwordHash, sharedDirectory, new DownloadStats(), new UploadStats());
    }

    public RegularUser(String username, String passwordHash, String sharedDirectory, DownloadStats downloadStats, UploadStats uploadStats) {
        super(username, passwordHash, sharedDirectory, downloadStats, uploadStats);
    }

    @Override
    public boolean isAdmin() {
        return false;
    }

}
