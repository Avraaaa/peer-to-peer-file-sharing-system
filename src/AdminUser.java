public class AdminUser extends User {

    public AdminUser(String passwordHash, DownloadStats downloadStats, UploadStats uploadStats) {
        super("admin", passwordHash, downloadStats, uploadStats);
    }

    @Override
    public boolean isAdmin() {
        return true;
    }
}