interface PasswordHasher {
    String hashPassword(String password);
    boolean verifyPassword(String password, String storedHash);
}