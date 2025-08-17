import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ClientConfigurationService {

    private final Path configFile;
    private final Map<String, String> configurations = new ConcurrentHashMap<>();

    public ClientConfigurationService(String configFileName) {
        this.configFile = Paths.get(configFileName);
        try {
            if (Files.exists(configFile)) {
                loadConfigurations();
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load client configurations: " + e.getMessage());
        }
    }

    private void loadConfigurations() throws IOException {
        List<String> lines = Files.readAllLines(configFile);
        for (String line : lines) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split(",", 2);
            if (parts.length == 2) {
                configurations.put(parts[0], parts[1]);
            }
        }
    }

    public String getSharedDirectory(String username) {
        return configurations.get(username);
    }

    public void saveSharedDirectory(String username, String sharedDirectory) throws IOException {
        configurations.put(username, sharedDirectory);
        rewriteConfigFile();
    }

    private synchronized void rewriteConfigFile() throws IOException {
        List<String> lines = new ArrayList<>();
        for (String key : configurations.keySet()) {
            String value = configurations.get(key);
            lines.add(key + "," + value);
        }

        Files.write(configFile, lines);
    }
}