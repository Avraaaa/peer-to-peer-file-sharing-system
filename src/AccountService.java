import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AccountService {

    // **MODIFIED**: Renamed variable for clarity
    private final Path userCsvPath;
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private static final int CAESAR_SHIFT_AMOUNT = 5;

    // **MODIFIED**: Renamed parameter for clarity
    public AccountService(String userCsvPath) {
        this.userCsvPath = Paths.get(userCsvPath);
        try {
            if (!Files.exists(this.userCsvPath)) {
                // The header for the CSV file
                try (PrintWriter writer = new PrintWriter(new FileWriter(this.userCsvPath.toFile()))) {
                    writer.println("username,passwordHash,sharedDirectory");
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create user CSV file at " + this.userCsvPath + ": " + e.getMessage(), e);
                }
            }
            loadUsers();
        } catch (RuntimeException e) {
            throw new RuntimeException("Could not initialize AccountService: " + e.getMessage(), e);
        }

    }

    private void loadUsers() {
        users.clear();
        List<String> lines;
        try {
            lines = Files.readAllLines(userCsvPath);
            // Skip header or if file is empty
            if (lines.isEmpty() || lines.size() <= 1) {
                users.putIfAbsent("admin", new AdminUser(hashPassword("admin")));
                return;
            }
        } catch (IOException e) {
            System.err.println("FATAL Error: Could not read user CSV file at " + userCsvPath + ": " + e.getMessage());
            // Create a default admin if the file is unreadable
            users.putIfAbsent("admin", new AdminUser(hashPassword("admin")));
            return;
        }
        // Start from 1 to skip the header line
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String[] parts = line.split(",", 3);
            if (parts.length == 3) {
                User user = new RegularUser(parts[0], parts[1], parts[2]);
                users.put(user.getUsername(), user);
            } else {
                System.err.println("Warning: Corrupt line in user CSV file, skipping: " + line);
            }
        }
        // Ensure the admin user always exists
        users.putIfAbsent("admin", new AdminUser(hashPassword("admin")));
    }

    public synchronized User createUser(String username, String password, String sharedDirectory) throws IOException {
        loadUsers();
        if (users.containsKey(username)) {
            throw new IOException("Username '" + username + "' already exists.");
        }
        if ("admin".equalsIgnoreCase(username)) {
            throw new IOException("The username 'admin' is reserved and cannot be used.");
        }
        String passwordHash = hashPassword(password);
        User newUser = new RegularUser(username, passwordHash, sharedDirectory);
        users.put(newUser.getUsername(), newUser);
        rewriteUserCsvFile(); // Call the renamed method
        return newUser;
    }

    public synchronized boolean removeUser(String username) throws IOException {
        loadUsers();
        if ("admin".equalsIgnoreCase(username)) {
            return false; // Cannot remove the admin
        }
        if (users.remove(username) != null) {
            rewriteUserCsvFile(); // Call the renamed method
            return true;
        }
        return false;
    }

    public synchronized User login(String username, String password) {
        loadUsers();
        User user = users.get(username);
        if (user != null && verifyPassword(password, user.getPasswordHash())) {
            return user;
        }
        return null;
    }

    // **MODIFIED**: Renamed method for clarity
    private void rewriteUserCsvFile() throws IOException {
        Path tempFilePath = getTempCsvPath(); // Call the renamed method
        List<String> lines = new ArrayList<>();
        lines.add("username,passwordHash,sharedDirectory"); // CSV header
        for (User user : users.values()) {
            // Don't write the default admin user to the file, it's managed in memory
            if (!user.isAdmin()) {
                lines.add(String.join(",", user.getUsername(), user.getPasswordHash(), user.getSharedDirectory()));
            }
        }
        Files.write(tempFilePath, lines);

        // Atomic move to prevent data corruption if the program crashes mid-write
        final int MAX_RETRIES = 3;
        final int RETRY_DELAY_MS = 100;
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Files.move(tempFilePath, userCsvPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return; // Success
            } catch (IOException e) {
                if (i == MAX_RETRIES - 1) throw e; // Give up after last retry
                System.err.println("Warning: Could not rewrite user CSV file, retrying...(" + (i + 1) + "/" + MAX_RETRIES + ")");
                try {
                    Thread.sleep(RETRY_DELAY_MS * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("CSV file write was interrupted", ie);
                }
            }
        }
    }

    // **MODIFIED**: Renamed method for clarity
    private Path getTempCsvPath() {
        Path parentDir = userCsvPath.getParent();
        String tempFileName = userCsvPath.getFileName().toString() + ".tmp";
        if (parentDir != null) {
            return parentDir.resolve(tempFileName);
        } else {
            return Paths.get(tempFileName);
        }
    }

    private String hashPassword(String password) {
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

    private boolean verifyPassword(String password, String storedHash) {
        return hashPassword(password).equals(storedHash);
    }
}