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
import java.util.stream.Collectors;

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
            AdminUser adminUser = createAdminWithStats();
            users.put("admin", adminUser);  
            return;
        }

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String[] parts = line.split(",");

            if (parts.length >= 6) {
                String username = parts[0];
                String passwordHash = parts[1];
                // Parts[2] not required for non admins

                User user = new RegularUser(username, passwordHash);

                // Download stats parts[3] and parts[4]
                if (parts.length >= 5) {
                    try {
                        user.getDownloadStats().fromCsvString(parts[3] + "," + parts[4]);
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Invalid download stats for user " + username + ", using defaults");
                    }
                }

                // upload stats (parts[5] and parts[6])
                if (parts.length >= 7) {
                    try {
                        user.getUploadStats().fromCsvString(parts[5] + "," + parts[6]);
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Invalid upload stats for user " + username + ", using defaults");
                    }
                }

                users.put(user.getUsername(), user);

            } else if (parts.length >= 2) {
                User user = new RegularUser(parts[0], parts[1]);
                if (parts.length >= 4) {
                    try {
                        user.getDownloadStats().fromCsvString(parts[2] + "," + parts[3]);
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Invalid download stats in old format for user " + parts[0] + ", using defaults");
                    }
                }
                if (parts.length >= 6) {
                    try {
                        user.getUploadStats().fromCsvString(parts[4] + "," + parts[5]);
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Invalid upload stats in old format for user " + parts[0] + ", using defaults");
                    }
                }
                users.put(user.getUsername(), user);
            } else {
                System.err.println("Warning: Corrupt line in user CSV file, skipping: " + line);
            }
        }
        AdminUser adminUser = createAdminWithStats();
        users.put("admin", adminUser);
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

            try {
                Path clientConfigPath = Paths.get("client_config.csv");
                if (Files.exists(clientConfigPath)) {
                    // Read the entire file and put everything other than the removedUser  on the arraylist and then overwrite
                    List<String> updatedLines = new ArrayList<>();


                    List<String> allLines = Files.readAllLines(clientConfigPath);
                    for (String line : allLines) {
                        if (!line.trim().startsWith(username + ",")) {
                            updatedLines.add(line);
                        }
                    }

                    Files.write(clientConfigPath, updatedLines);
                    System.out.println("Removed directory configuration for deleted user: " + username);
                }
            } catch (IOException e) {
                System.err.println("Warning: Could not remove directory config for user '" + username + "': " + e.getMessage());
            }

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
                // Atomically move the temporary file to replace the original
                Files.move(tempFilePath, userCsvPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (IOException e) {
                lastException = e;


                if (attempt < MAX_RETRIES) {
                    System.err.printf("Warning: Failed to rewrite user CSV file, retrying (%d/%d)%n", attempt, MAX_RETRIES);
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("CSV file write was interrupted", ie);
                    }
                }

            }
        }
        // If all retries fail, throw the last exception caught

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

    boolean verifyPassword(String password, String storedHash) {
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
        Path adminStatsPath;
        if (parentDir != null) {
            adminStatsPath = parentDir.resolve("admin_stats.csv");
        } else {
            adminStatsPath = Paths.get("admin_stats.csv");
        }


        try (PrintWriter writer = new PrintWriter(new FileWriter(adminStatsPath.toFile()))) {
            writer.println("downloadStats,uploadStats");
            writer.println(adminUser.getDownloadStats().toCsvString() + "," +
                    adminUser.getUploadStats().toCsvString());
        }
    }

    public boolean isOnlyAdmin(String username) {
        User user = users.get(username);
        if (user == null || !user.isAdmin()) {
            return false;
        }

        long adminCount = users.values().stream()
                .filter(User::isAdmin)
                .count();

        return adminCount == 1;
    }
    public boolean changePassword(String username, String newPassword) throws IOException {
        User user = users.get(username);
        if (user == null) {
            return false;
        }

        String newHashedPassword = hashPassword(newPassword);

        User updatedUser;
        if (user.isAdmin()) {
            updatedUser = new AdminUser( newHashedPassword, user.getDownloadStats(), user.getUploadStats());
        } else {
            updatedUser = new RegularUser(username, newHashedPassword, user.getDownloadStats(), user.getUploadStats());
        }

        users.put(username, updatedUser);

        rewriteUserCsvFile();
        return true;
    }

}
