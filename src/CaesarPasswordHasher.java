class CaesarPasswordHasher implements PasswordHasher {
    private static final int CAESAR_SHIFT_AMOUNT = 5;

    @Override
    public String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "";
        }
        String reversed = new StringBuilder(password).reverse().toString();

        char[] chars = reversed.toCharArray();
        for (int i = 0; i < chars.length - 1; i += 2) {
            char temp = chars[i];
            chars[i] = chars[i + 1];
            chars[i + 1] = temp;
        }
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (chars[i] + CAESAR_SHIFT_AMOUNT);
        }
        return new String(chars);
    }

    @Override
    public boolean verifyPassword(String password, String storedHash) {
        return hashPassword(password).equals(storedHash);
    }
}