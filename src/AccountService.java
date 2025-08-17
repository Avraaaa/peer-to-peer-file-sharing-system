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

    private final Path userCsvPath;
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private static final int CAESAR_SHIFT_AMOUNT = 5;
    private final int MAX_RETRIES = 3;
    private final int RETRY_DELAY_MS = 100;

    public AccountService(String userCsvPath) {
        this.userCsvPath = Paths.get(userCsvPath);
        try {
            if (!Files.exists(this.userCsvPath)) {
                try (PrintWriter writer = new PrintWriter(new FileWriter(this.userCsvPath.toFile()))) {
                    writer.println("username,passwordHash,downloadStats,uploadStats");
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
            if (lines.size() <= 1) {
                users.putIfAbsent("admin", createAdminWithStats());
                return;
            }
        } catch (IOException e) {
            System.err.println("FATAL Error: Could not read user CSV file at " + userCsvPath + ": " + e.getMessage());
            users.putIfAbsent("admin", createAdminWithStats());
            return;
        }
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String[] parts = line.split(",");
            if (parts.length >= 2) {
                User user = new RegularUser(parts[0], parts[1]);
                if (parts.length >= 4) {
                    user.getDownloadStats().fromCsvString(parts[2] + "," + parts[3]);
                }
                if (parts.length >= 6) {
                    user.getUploadStats().fromCsvString(parts[4] + "," + parts[5]);
                }
                users.put(user.getUsername(), user);
            } else {
                System.err.println("Warning: Corrupt line in user CSV file, skipping: " + line);
            }
        }
        users.putIfAbsent("admin", createAdminWithStats());
    }

    private AdminUser createAdminWithStats() {
        DownloadStats downloadStats = new DownloadStats();
        UploadStats uploadStats = new UploadStats();

        Path parentDir = userCsvPath.getParent();
        Path adminStatsPath = (parentDir != null) ?
                parentDir.resolve("admin_stats.csv") :
                Paths.get("admin_stats.csv");

        if (Files.exists(adminStatsPath)) {
            try {
                List<String> lines = Files.readAllLines(adminStatsPath);
                if (lines.size() > 1) {
                    String[] parts = lines.get(1).split(",");
                    if (parts.length >= 4) {
                        downloadStats.fromCsvString(parts[0] + "," + parts[1]);
                        uploadStats.fromCsvString(parts[2] + "," + parts[3]);
                    }
                }
            } catch (IOException e) {
                System.err.println("Warning: Could not load admin stats: " + e.getMessage());
            }
        }

        return new AdminUser(hashPassword("admin"), downloadStats, uploadStats);
    }

    public synchronized User createUser(String username, String password) throws IOException {
        loadUsers();
        if (users.containsKey(username)) {
            throw new IOException("Username '" + username + "' already exists.");
        }
        if ("admin".equalsIgnoreCase(username)) {
            throw new IOException("The username 'admin' is reserved and cannot be used.");
        }
        String passwordHash = hashPassword(password);
        User newUser = new RegularUser(username, passwordHash);
        users.put(newUser.getUsername(), newUser);
        rewriteUserCsvFile();
        return newUser;
    }

    public synchronized boolean removeUser(String username) throws IOException {
        loadUsers();
        if ("admin".equalsIgnoreCase(username)) {
            return false;
        }
        if (users.remove(username) != null) {
            rewriteUserCsvFile();
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

    private void rewriteUserCsvFile() throws IOException {
        Path tempFilePath = getTempCsvPath();
        List<String> lines = new ArrayList<>();
        lines.add("username,passwordHash,downloadStats,uploadStats");
        for (User user : users.values()) {
            if (!user.isAdmin()) {
                lines.add(String.join(",",
                        user.getUsername(),
                        user.getPasswordHash(),
                        user.getDownloadStats().toCsvString(),
                        user.getUploadStats().toCsvString()
                ));
            }
        }
        Files.write(tempFilePath, lines);

        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Files.move(tempFilePath, userCsvPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (IOException e) {
                lastException = e;
                if (attempt == MAX_RETRIES) {
                    break;
                }
                System.err.printf("Warning: Failed to rewrite user CSV file, retrying (%d/%d)%n", attempt, MAX_RETRIES);
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("CSV file write was interrupted", ie);
                }
            }
        }
        throw lastException;
    }

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

    public synchronized void saveUserStats(User user) throws IOException {
        users.put(user.getUsername(), user);

        if (user.isAdmin()) {
            saveAdminStatsToFile(user);
        } else {
            rewriteUserCsvFile();
        }
        System.out.println("Saved stats for user: " + user.getUsername());
    }

    private void saveAdminStatsToFile(User adminUser) throws IOException {
        Path parentDir = userCsvPath.getParent();
        Path adminStatsPath = (parentDir != null) ?
                parentDir.resolve("admin_stats.csv") :
                Paths.get("admin_stats.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(adminStatsPath.toFile()))) {
            writer.println("downloadStats,uploadStats");
            writer.println(adminUser.getDownloadStats().toCsvString() + "," +
                    adminUser.getUploadStats().toCsvString());
        }
    }
}   