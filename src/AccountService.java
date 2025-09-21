import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AccountService {
    private final Path userCsvPath;
    private final PasswordHasher passwordHasher;
    private final UserRepository userRepository;

    public AccountService(String userCsvPath) {
        this(userCsvPath, new CaesarPasswordHasher());
    }
    //Dependency Inversion for solid
    public AccountService(String userCsvPath, PasswordHasher passwordHasher) {
        this.userCsvPath = Paths.get(userCsvPath);
        this.passwordHasher = passwordHasher;
        this.userRepository = new CsvUserRepository(this.userCsvPath, passwordHasher);
        initialize();
    }



    private void initialize() {
        try {
            if (!Files.exists(userCsvPath)) {
                try (PrintWriter writer = new PrintWriter(new FileWriter(userCsvPath.toFile()))) {
                    writer.println("username,passwordHash,downloadStats,uploadStats");
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create user CSV file at " + userCsvPath + ": " + e.getMessage(), e);
                }
            }
            userRepository.loadUsers();
        } catch (RuntimeException e) {
            throw new RuntimeException("Could not initialize AccountService: " + e.getMessage(), e);
        }
    }

    public synchronized User createUser(String username, String password) throws IOException {
        userRepository.loadUsers();
        Map<String, User> users = userRepository.getUsers();

        if (users.containsKey(username)) {
            throw new IOException("Username '" + username + "' already exists.");
        }
        if ("admin".equalsIgnoreCase(username)) {
            throw new IOException("The username 'admin' is reserved and cannot be used.");
        }

        String passwordHash = passwordHasher.hashPassword(password);
        User newUser = new RegularUser(username, passwordHash);
        users.put(newUser.getUsername(), newUser);
        userRepository.saveUsers();
        return newUser;
    }

    public synchronized boolean removeUser(String username) throws IOException {
        userRepository.loadUsers();
        Map<String, User> users = userRepository.getUsers();

        if ("admin".equalsIgnoreCase(username)) {
            return false;
        }

        if (users.remove(username) != null) {
            userRepository.saveUsers();

            try {
                Path clientConfigPath = Paths.get("client_config.csv");
                if (Files.exists(clientConfigPath)) {
                    List<String> updatedLines = new ArrayList<>();
                    List<String> allLines = Files.readAllLines(clientConfigPath);
                    for (String line : allLines) {
                        // After reading the entire file filter out the removed user's name and overwrite the file with only those who should be there
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
        userRepository.loadUsers();
        Map<String, User> users = userRepository.getUsers();
        User user = users.get(username);
        if (user != null && passwordHasher.verifyPassword(password, user.getPasswordHash())) {
            return user;
        }
        return null;
    }

    public synchronized void saveUserStats(User user) throws IOException {
        userRepository.saveUserStats(user);
    }

    public boolean isOnlyAdmin(String username) {
        Map<String, User> users = userRepository.getUsers();
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
        Map<String, User> users = userRepository.getUsers();
        User user = users.get(username);
        if (user == null) {
            return false;
        }

        String newHashedPassword = passwordHasher.hashPassword(newPassword);
        User updatedUser;
        if (user.isAdmin()) {
            updatedUser = new AdminUser(newHashedPassword, user.getDownloadStats(), user.getUploadStats());
        } else {
            updatedUser = new RegularUser(username, newHashedPassword, user.getDownloadStats(), user.getUploadStats());
        }

        users.put(username, updatedUser);
        userRepository.saveUsers();
        return true;
    }

    boolean verifyPassword(String password, String storedHash) {
        return passwordHasher.verifyPassword(password, storedHash);
    }
}