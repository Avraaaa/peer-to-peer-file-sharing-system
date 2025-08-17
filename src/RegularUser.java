public class RegularUser extends User {

    public RegularUser(String username, String passwordHash) {
        super(username, passwordHash, new DownloadStats(), new UploadStats());
    }

    public RegularUser(String username, String passwordHash, DownloadStats downloadStats, UploadStats uploadStats) {
        super(username, passwordHash, downloadStats, uploadStats);
    }

    @Override
    public boolean isAdmin() {
        return false;
    }
}