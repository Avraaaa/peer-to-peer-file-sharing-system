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

class CsvUserRepository implements UserRepository {
    private final Path userCsvPath;
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final PasswordHasher passwordHasher;
    private final int MAX_RETRIES = 3;
    private final int RETRY_DELAY_MS = 100;

    public CsvUserRepository(Path userCsvPath, PasswordHasher passwordHasher) {
        this.userCsvPath = userCsvPath;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public Map<String, User> getUsers() {
        return users;
    }

    @Override
    public void loadUsers() {
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

        parseUserLines(lines);
        AdminUser adminUser = createAdminWithStats();
        users.put("admin", adminUser);
    }

    private void parseUserLines(List<String> lines) {
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String[] parts = line.split(",");

            if (parts.length >= 6) {
                parseNewFormatUser(parts);
            } else if (parts.length >= 2) {
                parseOldFormatUser(parts);
            } else {
                System.err.println("Warning: Corrupt line in user CSV file, skipping: " + line);
            }
        }
    }

    private void parseNewFormatUser(String[] parts) {
        String username = parts[0];
        String passwordHash = parts[1];
        User user = new RegularUser(username, passwordHash);
        // Parts[2] not required for non admins

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
    }

    private void parseOldFormatUser(String[] parts) {
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

        return new AdminUser(passwordHasher.hashPassword("admin"), downloadStats, uploadStats);
    }

    @Override
    public void saveUsers() throws IOException {
        rewriteUserCsvFile();
    }

    @Override
    public synchronized void saveUserStats(User user) throws IOException {
        if (user != null) {
            users.put(user.getUsername(), user);
        }

        if (user != null && user.isAdmin()) {
            saveAdminStatsToFile(user);
        } else {
            rewriteUserCsvFile();
        }

        if (user != null) {
            System.out.println("Saved stats for user: " + user.getUsername());
        }
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
}