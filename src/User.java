public abstract class User {

    private final String username;
    private final String passwordHash;
    private final String sharedDirectory;

    public User(String username, String passwordHash, String sharedDirectory) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.sharedDirectory = sharedDirectory;
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

}
