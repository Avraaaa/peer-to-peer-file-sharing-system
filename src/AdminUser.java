public class AdminUser extends User {

    private static final String ADMIN_SHARED_DIR = "admin_shared_files";

    public AdminUser(String passwordHash) {
        super("admin", passwordHash, new DownloadStats(), new UploadStats());
    }

    public AdminUser(String passwordHash, DownloadStats downloadStats, UploadStats uploadStats) {
        super("admin", passwordHash, downloadStats, uploadStats);
    }

    @Override
    public boolean isAdmin() {
        return true;
    }

    @Override
    public String getSharedDirectory() {
        return ADMIN_SHARED_DIR;
    }
}