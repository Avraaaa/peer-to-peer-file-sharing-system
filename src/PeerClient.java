import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PeerClient {
    private String serverHost;
    private int serverPort;
    private final int myListenPort;
    private final int myUdpPort;
    private FileHandler fileHandler;
    private DownloadStrategy downloadStrategy;
    private Transport serverTransport;
    private User loggedInUser;
    private final Set<String> knownSharedFiles = ConcurrentHashMap.newKeySet();
    private Thread directoryWatcherThread;


    public PeerClient(String serverHost, int serverPort, int myListenPort, FileHandler fileHandler, DownloadStrategy downloadStrategy) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.myListenPort = myListenPort;
        this.myUdpPort = myListenPort + 1;
        this.fileHandler = fileHandler;
        this.downloadStrategy = downloadStrategy;
    }

    public void setSessionContext(User user, Transport transport) {
        this.loggedInUser = user;
        this.serverTransport = transport;
    }

    public static void main(String[] args) throws IOException {
        String serverHost = "localhost";
        int serverPort = 9090;
        User loggedInUser = null;

        Transport serverTransport = null;
        try {
            serverTransport = new TCPTransport(serverHost, serverPort);
            System.out.println("Connected to server at " + serverHost + ":" + serverPort);
        } catch (IOException e) {
            System.err.println("FATAL: Could not connect to the server at " + serverHost + ":" + serverPort);
            return;
        }

        Scanner scanner = new Scanner(System.in);
        String localSharedDirectory = "";

        while (loggedInUser == null) {
            System.out.println("\n--- Welcome to Napster Clone ---");
            System.out.println("1. Login");
            System.out.println("2. Sign Up");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");
            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    System.out.print("Enter username: ");
                    String username = scanner.nextLine();
                    System.out.print("Enter password: ");
                    String password = scanner.nextLine();

                    serverTransport.sendLine("LOGIN " + username + " " + password);
                    String response = serverTransport.readLine();

                    if (response != null && response.startsWith("LOGIN_SUCCESS")) {
                        String[] payload = response.substring(14).split(";", 4);
                        String u = payload[0];
                        boolean isAdmin = Boolean.parseBoolean(payload[1]);
                        DownloadStats dStats = new DownloadStats();
                        dStats.fromCsvString(payload[2]);
                        UploadStats uStats = new UploadStats();
                        uStats.fromCsvString(payload[3]);

                        ClientConfigurationService configService = new ClientConfigurationService("client_config.csv");
                        localSharedDirectory = configService.getSharedDirectory(u);

                        if (localSharedDirectory == null) {
                            System.out.println("\n--- Welcome, " + u + "! First-time setup on this machine. ---");
                            System.out.print("Please specify a directory for your shared files (e.g., " + u + "_files): ");
                            String dirPathInput = scanner.nextLine();

                            Path userPath = Paths.get(dirPathInput);
                            if (!userPath.isAbsolute()) {
                                userPath = Paths.get(System.getProperty("user.dir"), dirPathInput).toAbsolutePath();
                            }

                            if (!Files.exists(userPath)) {
                                Files.createDirectories(userPath);
                            }
                            localSharedDirectory = userPath.toString();
                            configService.saveSharedDirectory(u, localSharedDirectory);
                            System.out.println("Shared directory for user '" + u + "' set to: " + localSharedDirectory);
                        } else {
                            System.out.println("Welcome back, " + u + "! Using configured directory: " + localSharedDirectory);
                        }

                        if (isAdmin) {
                            loggedInUser = new AdminUser("", dStats, uStats);
                        } else {
                            loggedInUser = new RegularUser(u, "", dStats, uStats);
                        }
                        System.out.println("Login successful!");
                    } else {
                        if (response != null) {
                            System.err.println("Login failed: " + response.replace("LOGIN_FAIL ", ""));
                        } else {
                            System.err.println("Login failed: No response from server.");
                        }
                    }
                    break;

                case "2":
                    System.out.print("Enter a new username: ");
                    String newUsername = scanner.nextLine();
                    System.out.print("Enter a password: ");
                    String newPassword = scanner.nextLine();

                    serverTransport.sendLine("SIGNUP " + newUsername + " " + newPassword);
                    String signupResponse = serverTransport.readLine();

                    if ("SIGNUP_SUCCESS".equals(signupResponse)) {
                        System.out.println("Account created successfully! Please log in.");
                    } else {
                        System.err.println("Could not create account: " + (signupResponse != null ? signupResponse.replace("SIGNUP_FAIL ", "") : "No response from server."));
                    }
                    break;
                case "3":
                    System.out.println("Exiting.");
                    serverTransport.close();
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }

        Path userSharedPath = Paths.get(localSharedDirectory);
        if (!Files.exists(userSharedPath)) {
            System.out.println("Shared directory not found for user, creating it at: " + userSharedPath.toAbsolutePath());
            Files.createDirectories(userSharedPath);
        }

        System.out.println("\nSearching for an available network port...");
        int myPort = findAvailablePortPair();
        if (myPort == -1) {
            System.err.println("FATAL: Could not find any available ports.");
            serverTransport.close();
            return;
        }
        System.out.println("--> Automatically assigned TCP Port " + myPort + " and UDP Port " + (myPort + 1));

        FileHandler fileHandler = new LocalFileHandler(localSharedDirectory);
        DownloadStrategy downloadStrategy = new ChunkedDownload(8192, fileHandler, userSharedPath);

        PeerClient client = new PeerClient(serverHost, serverPort, myPort, fileHandler, downloadStrategy);
        client.setSessionContext(loggedInUser, serverTransport);
        client.start(localSharedDirectory);
    }

    public void start(String sharedDirectoryPath) throws IOException {
        System.out.println("DEBUG: PeerClient.start() called with directory: " + sharedDirectoryPath);

        serverTransport.sendLine("REGISTER " + myListenPort);
        System.out.println("DEBUG: Sent REGISTER command with port: " + myListenPort);

        registerAndShareFiles();
        System.out.println("DEBUG: Completed registerAndShareFiles()");

        Path sharedPath = Paths.get(sharedDirectoryPath);
        directoryWatcherThread = new Thread(new DirectoryWatcher(sharedPath, serverTransport));
        directoryWatcherThread.setDaemon(true);
        directoryWatcherThread.start();
        System.out.println("DEBUG: Started directory watcher thread");

        // Rest of the method stays the same...
        class TcpServerTask implements Runnable {
            private int myListenPort;

            public TcpServerTask(int myListenPort) {
                this.myListenPort = myListenPort;
            }

            @Override
            public void run() {
                try (ServerSocket serverSocket = new ServerSocket(myListenPort)) {
                    System.out.println("TCP Download Server listening on port " + myListenPort);

                    while (!serverSocket.isClosed()) {
                        new Thread(new DownloadHandler(serverSocket.accept())).start();
                    }
                } catch (IOException e) {
                    if (!"Socket closed".equals(e.getMessage())) {
                        System.err.println("FATAL: Failed to listen for TCP connections: " + e.getMessage());
                    }
                }
            }
        }
        Thread tcpServerThread = new Thread(new TcpServerTask(myListenPort));
        tcpServerThread.start();

        new Thread(new UdpRequestHandler(myUdpPort)).start();
        handleUserInput();
    }
    private void registerAndShareFiles() throws IOException {
        List<String> sharedFiles = fileHandler.listSharedFiles();
        System.out.println("DEBUG: Found " + sharedFiles.size() + " files to share:");

        if (sharedFiles.isEmpty()) {
            System.out.println("DEBUG: WARNING - No files found in shared directory!");
            if (fileHandler instanceof LocalFileHandler) {
                LocalFileHandler lh = (LocalFileHandler) fileHandler;
                System.out.println("DEBUG: Shared directory path: " + lh.getSharedDirectory());
                System.out.println("DEBUG: Directory exists: " + java.nio.file.Files.exists(lh.getSharedDirectory()));
            }
            return;
        }

        for (String fileName : sharedFiles) {
            System.out.println("DEBUG: Sharing file: " + fileName);
            try {
                serverTransport.sendLine("SHARE " + fileName);
                knownSharedFiles.add(fileName);
                System.out.println("DEBUG: Successfully sent SHARE command for: " + fileName);
            } catch (IOException e) {
                System.err.println("DEBUG: Failed to share file " + fileName + ": " + e.getMessage());
                throw e;
            }
        }

        System.out.println("DEBUG: Completed sharing " + sharedFiles.size() + " files");
    }


    private void handleUserInput() throws IOException {
        if (loggedInUser.isAdmin()) {
            handleAdminInput();
        } else {
            handleRegularUserInput();
        }
    }

    private void handleRegularUserInput() throws IOException {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nOptions: [1] Search Files [2] List Peers [3] Browse Peer's Files [4] My Stats [5] Exit");
            System.out.print("Choose an option: ");
            String choice = scanner.nextLine();
            switch (choice) {
                case "1":
                    System.out.print("Enter filename to search: ");
                    searchAndDownload(scanner.nextLine());
                    break;
                case "2":
                    listPeers();
                    break;
                case "3":
                    browseAndDownload();
                    break;
                case "4":
                    displayMyStats();
                    break;
                case "5":
                    serverTransport.sendLine("UNREGISTER");
                    serverTransport.close();
                    System.exit(0);
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    private void handleAdminInput() throws IOException {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n--- ADMIN MENU ---");
            System.out.println("[1] Search Files  [2] List Peers  [3] Browse Peer's Files  [4] Remove User  [5] My Stats [6] Exit");
            System.out.print("Choose an option: ");
            String choice = scanner.nextLine();
            switch (choice) {
                case "1":
                    System.out.print("Enter filename to search: ");
                    searchAndDownload(scanner.nextLine());
                    break;
                case "2":
                    listPeers();
                    break;
                case "3":
                    browseAndDownload();
                    break;
                case "4":
                    removeUser();
                    break;
                case "5":
                    displayMyStats();
                    break;
                case "6":
                    serverTransport.sendLine("UNREGISTER");
                    serverTransport.close();
                    System.exit(0);
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    private void displayMyStats() {
        System.out.println("\n--- Your User Stats ---");
        System.out.println("Username: " + loggedInUser.getUsername());
        System.out.println("\n--- Download Stats ---");
        System.out.println("Files Downloaded: " + loggedInUser.getDownloadStats().getFileCount());
        System.out.println("Total Bytes Downloaded: " + formatFileSize(loggedInUser.getDownloadStats().getTotalBytes()));
        System.out.println("\n--- Upload Stats ---");
        System.out.println("Files Uploaded: " + loggedInUser.getUploadStats().getFileCount());
        System.out.println("Total Bytes Uploaded: " + formatFileSize(loggedInUser.getUploadStats().getTotalBytes()));
        System.out.println("-----------------------");
    }

    private void searchAndDownload(String fileName) throws IOException {
        serverTransport.sendLine("SEARCH " + fileName);
        String response = serverTransport.readLine();

        // Parse the enhanced search response format
        Map<String, Map<String, String>> searchResults = parseEnhancedSearchResponse(response);

        if (searchResults.isEmpty()) {
            System.out.println("No peers found with this file.");
            return;
        }

        // Flatten the results for display and validation
        Map<String, String> validPeers = new LinkedHashMap<>();

        System.out.println("\nVerifying which peers have the file...");
        for (Map.Entry<String, Map<String, String>> fileEntry : searchResults.entrySet()) {
            String foundFileName = fileEntry.getKey();
            Map<String, String> peersWithFile = fileEntry.getValue();

            for (Map.Entry<String, String> peerEntry : peersWithFile.entrySet()) {
                String peerUsername = peerEntry.getKey();
                String peerAddress = peerEntry.getValue();

                // Verify peer actually has the file
                if (getPeerFileSize(peerAddress, foundFileName) > -1) {
                    validPeers.put(peerUsername + " (" + foundFileName + ")", peerAddress + "|" + foundFileName);
                }
            }
        }

        if (validPeers.isEmpty()) {
            System.out.println("No available peer has files matching: " + fileName);
            return;
        }

        System.out.println("\nFiles found matching your search:");
        System.out.printf("%-40s %s\n", "Peer (File)", "Address");
        System.out.println("-".repeat(70));

        int index = 1;
        Map<Integer, String> indexMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : validPeers.entrySet()) {
            String display = entry.getKey();
            String value = entry.getValue();
            System.out.printf("%d. %-38s %s\n", index, display, value.split("\\|")[0]);
            indexMap.put(index, value);
            index++;
        }

        System.out.println("-".repeat(70));
        System.out.print("\nEnter number to download: ");

        try {
            int choice = Integer.parseInt(new Scanner(System.in).nextLine());
            if (indexMap.containsKey(choice)) {
                String[] parts = indexMap.get(choice).split("\\|");
                String peerAddress = parts[0];
                String actualFileName = parts[1];

                String peerUsername = validPeers.entrySet().stream()
                        .filter(e -> e.getValue().equals(indexMap.get(choice)))
                        .map(e -> e.getKey().split(" \\(")[0])
                        .findFirst().orElse("Unknown");

                System.out.println("Starting download of '" + actualFileName + "' from " + peerUsername + " at " + peerAddress);
                long fileSize = getPeerFileSize(peerAddress, actualFileName);
                downloadStrategy.download(peerAddress, actualFileName);

                if (fileSize > 0) {
                    loggedInUser.getDownloadStats().addFile();
                    loggedInUser.getDownloadStats().addBytes(fileSize);
                    updateRemoteStats();
                    System.out.println("Your download stats have been updated with the server.");
                }

                serverTransport.sendLine("SHARE " + actualFileName);
            } else {
                System.out.println("Invalid selection.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a number.");
        }
    }

    // Add this new method to parse the enhanced search response
    private Map<String, Map<String, String>> parseEnhancedSearchResponse(String response) {
        Map<String, Map<String, String>> results = new LinkedHashMap<>();

        if (response == null || response.trim().isEmpty()) {
            return results;
        }

        // Server sends format: filename1=peer1:address1,peer2:address2;filename2=peer3:address3
        String[] fileEntries = response.split(";");

        for (String fileEntry : fileEntries) {
            if (fileEntry.trim().isEmpty()) continue;

            String[] fileParts = fileEntry.split("=", 2);
            if (fileParts.length == 2) {
                String fileName = fileParts[0];
                String peerList = fileParts[1];

                Map<String, String> peersForFile = new LinkedHashMap<>();

                String[] peers = peerList.split(",");
                for (String peer : peers) {
                    String[] peerParts = peer.split(":", 2);
                    if (peerParts.length == 2) {
                        String username = peerParts[0];
                        String address = peerParts[1];
                        peersForFile.put(username, address);
                    }
                }

                results.put(fileName, peersForFile);
            }
        }

        return results;
    }
    private void browseAndDownload() throws IOException {
        System.out.println("\n--- Browse Peer's Files ---");
        Map<String, String> onlinePeers = listPeers();
        if (onlinePeers.isEmpty()) {
            return;
        }
        System.out.print("Choose a peer to browse by entering their USERNAME: ");
        Scanner scanner = new Scanner(System.in);
        String chosenUsername = scanner.nextLine();
        if (!onlinePeers.containsKey(chosenUsername)) {
            System.out.println("Invalid or offline username selected.");
            return;
        }
        String peerAddress = onlinePeers.get(chosenUsername);
        List<String> fileNames = getFileListFromPeer(peerAddress);
        if (fileNames == null || fileNames.isEmpty()) {
            System.out.println("Peer " + chosenUsername + " has no shared files or did not respond.");
            return;
        }
        List<SharedFile> sharedFiles = new ArrayList<>();
        System.out.println("\nFetching file details from " + chosenUsername + "...");
        for (String fileName : fileNames) {
            long fileSize = getPeerFileSize(peerAddress, fileName);
            sharedFiles.add(SharedFileFactory.createSharedFile(fileName, fileSize));
        }
        System.out.println("\n--- Files available from " + chosenUsername + " ---");
        for (int i = 0; i < sharedFiles.size(); i++) {
            SharedFile file = sharedFiles.get(i);
            System.out.printf("%d. %s (%s, Type: %s)\n", i + 1, file.getName(), formatFileSize(file.getSize()), file.getClass().getSimpleName());
        }
        System.out.println("---------------------------------------");
        System.out.print("Enter file number to download, or type 'info [number]' for details: ");
        String input = scanner.nextLine();
        if (input.toLowerCase().startsWith("info")) {
            try {
                int fileIndex = Integer.parseInt(input.split(" ")[1]) - 1;
                if (fileIndex >= 0 && fileIndex < sharedFiles.size()) {
                    SharedFile selectedFile = sharedFiles.get(fileIndex);
                    if (selectedFile instanceof ArchivedFile) {
                        System.out.println("Querying peer for archive details...");
                        int realFileCount = getRemoteZipFileCount(peerAddress, selectedFile.getName());
                        selectedFile = new ArchivedFile(selectedFile.getName(), selectedFile.getSize(), realFileCount);
                    }
                    System.out.println("\n--- File Details ---\n" + selectedFile.getFileInfo() + "\n--------------------");
                } else {
                    System.err.println("Invalid file number.");
                }
            } catch (Exception e) {
                System.err.println("Invalid command format. Use 'info [number]'.");
            }
        } else {
            try {
                int fileIndex = Integer.parseInt(input) - 1;
                if (fileIndex >= 0 && fileIndex < sharedFiles.size()) {
                    SharedFile selectedFile = sharedFiles.get(fileIndex);
                    System.out.println("Downloading '" + selectedFile.getName() + "' from " + chosenUsername + " via TCP...");
                    downloadStrategy.download(peerAddress, selectedFile.getName());
                    if (selectedFile.getSize() > 0) {
                        loggedInUser.getDownloadStats().addFile();
                        loggedInUser.getDownloadStats().addBytes(selectedFile.getSize());
                        updateRemoteStats();
                        System.out.println("Your download stats have been updated with the server.");
                    }
                    serverTransport.sendLine("SHARE " + selectedFile.getName());
                } else {
                    System.err.println("Invalid file number selected.");
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid input. Please enter a file number or 'info' command.");
            }
        }
    }

    private void removeUser() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n--- Remove a User ---");
        Map<String, String> onlinePeers = listPeers();
        System.out.print("Enter the USERNAME of the user to remove: ");
        String usernameToRemove = scanner.nextLine();
        if (loggedInUser.getUsername().equalsIgnoreCase(usernameToRemove)) {
            System.err.println("You cannot remove yourself.");
            return;
        }
        System.out.print("Are you sure you want to PERMANENTLY remove user '" + usernameToRemove + "'? (y/n): ");
        if (!scanner.nextLine().equalsIgnoreCase("y")) {
            System.out.println("Removal cancelled.");
            return;
        }
        serverTransport.sendLine("REMOVE_USER " + usernameToRemove);
        String response = serverTransport.readLine();
        if ("REMOVE_SUCCESS".equals(response)) {
            System.out.println("Server has removed user '" + usernameToRemove + "'.");
            if (onlinePeers.containsKey(usernameToRemove)) {
                String peerAddress = onlinePeers.get(usernameToRemove);
                System.out.println("Sending shutdown signal to " + peerAddress + "...");
                try (Transport peerTransport = new TCPTransport(peerAddress.split(":")[0], Integer.parseInt(peerAddress.split(":")[1]))) {
                    peerTransport.sendLine("_KICK_ " + usernameToRemove);
                } catch (IOException e) {
                    System.err.println("Could not send kick signal. The peer may have already disconnected. " + e.getMessage());
                }
            }
        } else {
            System.err.println("Could not remove user. Server response: " + (response != null ? response.replace("REMOVE_FAIL ", "") : "No response."));
        }
    }

    private Map<String, String> listPeers() throws IOException {
        serverTransport.sendLine("LIST_PEERS");
        String response = serverTransport.readLine();
        Map<String, String> onlinePeers = parsePeerInfoResponse(response);
        onlinePeers.remove(loggedInUser.getUsername());
        if (onlinePeers.isEmpty()) {
            System.out.println("\nYou are the only peer online.");
            return onlinePeers;
        }
        System.out.println("\n--- Online Peers ---");
        System.out.printf("%-20s %s\n", "Username", "Peer Address");
        System.out.println("-".repeat(42));
        for (Map.Entry<String, String> entry : onlinePeers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            System.out.printf("%-20s %s\n", key, value);
        }
        System.out.println("--------------------");
        return onlinePeers;
    }

    private Map<String, String> parsePeerInfoResponse(String response) {
        Map<String, String> peerInfoMap = new LinkedHashMap<>();
        if (response == null || response.isEmpty()) return peerInfoMap;
        String[] pairs = response.split(",");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                peerInfoMap.put(parts[0], parts[1]);
            }
        }
        return peerInfoMap;
    }

    private List<String> getFileListFromPeer(String peerAddress) {
        try {
            String[] parts = peerAddress.split(":");
            String host = parts[0];
            int udpPort = Integer.parseInt(parts[1]) + 1;
            try (UDPTransport peerTransport = new UDPTransport(host, udpPort)) {
                peerTransport.setSoTimeout(3000);
                peerTransport.sendLine("LIST_FILES");
                String line = peerTransport.readLine();
                if (line == null || line.trim().isEmpty()) return Collections.emptyList();
                return Arrays.asList(line.split(","));
            }
        } catch (SocketTimeoutException e) {
            System.err.println("Could not get file list from " + peerAddress + ": Peer timed out.");
            return null;
        } catch (IOException e) {
            System.err.println("Could not get file list from " + peerAddress + ": " + e.getMessage());
            return null;
        }
    }

    private long getPeerFileSize(String peerAddress, String fileName) {
        try {
            String[] parts = peerAddress.split(":");
            String host = parts[0];
            int udpPort = Integer.parseInt(parts[1]) + 1;
            try (UDPTransport transport = new UDPTransport(host, udpPort)) {
                transport.setSoTimeout(3000);
                transport.sendLine("FILESIZE " + fileName);
                String response = transport.readLine();
                if (response != null && !response.trim().isEmpty()) {
                    return Long.parseLong(response.trim());
                }
                return -1;
            }
        } catch (NumberFormatException | SocketTimeoutException e) {
            return -1;
        } catch (IOException e) {
            return -1;
        }
    }

    private int getRemoteZipFileCount(String peerAddress, String fileName) {
        try {
            String[] parts = peerAddress.split(":");
            String host = parts[0];
            int udpPort = Integer.parseInt(parts[1]) + 1;
            try (UDPTransport transport = new UDPTransport(host, udpPort)) {
                transport.setSoTimeout(3000);
                transport.sendLine("GET_ZIP_COUNT " + fileName);
                String response = transport.readLine();
                if (response != null && !response.trim().isEmpty()) return Integer.parseInt(response.trim());
                return 0;
            }
        } catch (NumberFormatException | SocketTimeoutException e) {
            System.err.println("Peer did not respond with zip count.");
            return 0;
        } catch (IOException e) {
            System.err.println("Error querying peer for zip count: " + e.getMessage());
            return 0;
        }
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double displaySize = size;
        while (displaySize >= 1024 && unitIndex < units.length - 1) {
            displaySize /= 1024;
            unitIndex++;
        }
        return String.format(Locale.US, "%.1f %s", displaySize, units[unitIndex]);
    }

    private static int findAvailablePortPair() {
        for (int port = 10000; port <= 11000; port++) if (arePortsAvailable(port, port + 1)) return port;
        return -1;
    }

    private static boolean arePortsAvailable(int tcpPort, int udpPort) {
        try (ServerSocket tcpSocket = new ServerSocket(tcpPort); DatagramSocket udpSocket = new DatagramSocket(udpPort)) {
            tcpSocket.setReuseAddress(true);
            udpSocket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void updateRemoteStats() {
        try {
            String downloadStatsCsv = loggedInUser.getDownloadStats().toCsvString();
            String uploadStatsCsv = loggedInUser.getUploadStats().toCsvString();
            serverTransport.sendLine("UPDATE_STATS " + downloadStatsCsv + " " + uploadStatsCsv);
        } catch (IOException e) {
            System.err.println("Warning: Could not update stats with server: " + e.getMessage());
        }
    }

    private class DirectoryWatcher implements Runnable {
        private final Path path;
        private final Transport serverTransport;
        private static final int POLLING_INTERVAL_MS = 3000;

        DirectoryWatcher(Path path, Transport serverTransport) {
            this.path = path;
            this.serverTransport = serverTransport;
        }

        @Override
        public void run() {
            System.out.println("Directory watcher started for: " + path + " (polling every 3 seconds)");
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        for (String fileName : fileHandler.listSharedFiles()) {
                            if (knownSharedFiles.add(fileName)) {
                                System.out.println("\n[Auto-Detector] New file found: " + fileName + ". Sharing with network...");
                                System.out.print("Choose an option: ");
                                try {
                                    synchronized (serverTransport) {
                                        serverTransport.sendLine("SHARE " + fileName);
                                    }
                                } catch (IOException e) {
                                    System.err.println("\nFailed to auto-share file " + fileName + ". The connection may be down.");
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("\nError reading shared directory in watcher: " + e.getMessage());
                    }
                    Thread.sleep(POLLING_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                System.out.println("Directory watcher stopped.");
            }
        }
    }

    private class UdpRequestHandler implements Runnable {
        private final int port;

        public UdpRequestHandler(int port) {
            this.port = port;
        }

        @Override
        public void run() {
            try (DatagramSocket socket = new DatagramSocket(port)) {
                System.out.println("UDP Discovery Server listening on port " + port);
                while (true) {
                    try {
                        byte[] receiveBuffer = new byte[1024];
                        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                        socket.receive(receivePacket);

                        //Extract sender info
                        InetAddress senderAddress = receivePacket.getAddress();
                        int senderPort = receivePacket.getPort();
                        String request = new String(receivePacket.getData(), 0, receivePacket.getLength(), "UTF-8");
                        System.out.println("UDP Server received request: " + request);

                        //creating response string here based on the request
                        String responseString = "";
                        if (request.equals("LIST_FILES")) {
                            responseString = String.join(",", fileHandler.listSharedFiles());
                        } else if (request.startsWith("FILESIZE ")) {
                            String fileName = request.substring(9);
                            Path filePath = ((LocalFileHandler) fileHandler).getSharedDirectory().resolve(fileName);
                            responseString = String.valueOf(Files.exists(filePath) ? Files.size(filePath) : -1);
                        } else if (request.startsWith("GET_ZIP_COUNT ")) {
                            String requestedFileName = request.substring(14);
                            Path sharedDir = ((LocalFileHandler) fileHandler).getSharedDirectory();
                            Path requestedFilePath = sharedDir.resolve(requestedFileName);
                            long zipFileCount = SharedFileFactory.getZipFileCount(requestedFilePath);
                            responseString = String.valueOf(zipFileCount);
                        }

                        //instead of creating new socket reuse the existing one to send response
                        if (!responseString.isEmpty()) {
                            byte[] sendBuffer = responseString.getBytes("UTF-8");
                            DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, senderAddress, senderPort);
                            socket.send(sendPacket);
                        }

                    } catch (IOException e) {
                        // print eror if socket wasnt closed intentionally
                        if (socket.isClosed()) {
                            System.out.println("UDP listener shutting down.");
                            break;
                        }
                        System.err.println("Error processing UDP request: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("FATAL: Failed to listen for UDP datagrams on port " + port + ": " + e.getMessage());
            }
        }
    }


    private class DownloadHandler implements Runnable {
        private final Socket socket;

        public DownloadHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream())); OutputStream os = socket.getOutputStream();
                 PrintWriter out = new PrintWriter(os, true)) {
                String request = in.readLine();
                if (request == null) return;
                System.out.println("TCP Server received request: " + request + " from " + socket.getRemoteSocketAddress());
                if (request.startsWith("DOWNLOAD ")) {
                    String fileName = request.substring(9);
                    long totalBytesSent = 0;
                    try (InputStream fis = fileHandler.getInputStream(fileName)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                            totalBytesSent += bytesRead;
                        }
                    } catch (IOException e) {
                        System.err.println("Error sending file " + fileName + ": " + e.getMessage());
                    }
                    if (totalBytesSent > 0) {
                        loggedInUser.getUploadStats().addFile();
                        loggedInUser.getUploadStats().addBytes(totalBytesSent);
                        updateRemoteStats();
                    }
                } else if (request.startsWith("_KICK_ ")) {
                    String kickedUser = request.substring(7);
                    if (loggedInUser.getUsername().equals(kickedUser)) {
                        out.println("OK_KICKED");
                        System.out.println("\n!!! Received remote shutdown command from an administrator. Quitting now. !!!");
                        System.exit(0);
                    } else {
                        out.println("ERROR_WRONG_USER");
                    }
                } else {
                    out.println("ERROR Unknown command on TCP socket: " + request);
                }
            } catch (IOException e) {
                System.err.println("Error in DownloadHandler: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
