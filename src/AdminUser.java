public class AdminUser extends User {

    private static final String ADMIN_SHARED_DIR = "admin_shared_files";

    public AdminUser(String passwordHash) {
        super("admin", passwordHash, ADMIN_SHARED_DIR, new DownloadStats(), new UploadStats());
    }

    @Override
    public boolean isAdmin() {
        return true;
    }

}
