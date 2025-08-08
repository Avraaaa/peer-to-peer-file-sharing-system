import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PeerClient {

    private final String serverHost;
    private final int serverPort;

    private User loggedInUser;
    private int myListenPort;
    private FileHandler fileHandler;
    private DownloadStrategy downloadStrategy;
    private final Set<String> knownSharedFiles = ConcurrentHashMap.newKeySet();

    private Thread downloadListenerThread;
    private ServerSocket downloadListenerSocket;
    private Thread directoryWatcherThread;

    public PeerClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java PeerClient <serverHost> <serverPort>");
            return;
        }
        String serverHost = args[0];
        int serverPort = Integer.parseInt(args[1]);
        new PeerClient(serverHost, serverPort).start();
    }

    public void start() {
        try (
                Transport serverTransport = new TCPTransport(serverHost, serverPort);
                Scanner console = new Scanner(System.in)
        ) {
            System.out.println("Connected to the Napster server.");

            while (true) {
                if (loggedInUser == null) {
                    showPreLoginMenu();
                    String choice = console.nextLine();
                    switch (choice) {
                        case "1": handleLogin(serverTransport, console); break;
                        case "2": handleSignUp(serverTransport, console); break;
                        case "3": System.out.println("Exiting client."); return;
                        default: System.out.println("Invalid option. Please try again.");
                    }
                } else {
                    showPostLoginMenu();
                    String choice = console.nextLine();
                    switch (choice) {
                        case "1": handleSearchAndDownload(serverTransport, console); break;
                        case "2": handleListPeers(serverTransport); break;
                        case "3": handleViewStatistics(); break;
                        case "4":
                            if (loggedInUser.isAdmin()) handleRemoveUser(serverTransport, console);
                            else handleLogout(serverTransport);
                            break;
                        case "5":
                            if (loggedInUser.isAdmin()) handleLogout(serverTransport);
                            else System.out.println("Invalid option.");
                            break;
                        default: System.out.println("Invalid option. Please try again.");
                    }
                }
            }
        } catch (ConnectException e) {
            System.err.println("FATAL: Could not connect to the server at " + serverHost + ":" + serverPort);
        } catch (IOException e) {
            System.err.println("An error occurred: " + e.getMessage());
        } finally {
            shutdownLocalServices();
        }
    }

    private void showPreLoginMenu() {
        System.out.println("\n--- Welcome ---");
        System.out.println("[1] Login");
        System.out.println("[2] Sign Up");
        System.out.println("[3] Exit");
        System.out.print("> ");
    }

    private void showPostLoginMenu() {
        System.out.printf("\n--- Logged in as %s ---\n", loggedInUser.getUsername());
        System.out.println("[1] Search for a file");
        System.out.println("[2] List online peers");
        System.out.println("[3] View my statistics");
        if (loggedInUser.isAdmin()) {
            System.out.println("[4] Remove a user (Admin)");
            System.out.println("[5] Logout");
        } else {
            System.out.println("[4] Logout");
        }
        System.out.print("> ");
    }

    private void handleLogin(Transport serverTransport, Scanner console) throws IOException {
        System.out.print("Enter username: ");
        String username = console.nextLine();
        System.out.print("Enter password: ");
        String password = console.nextLine();
        System.out.print("Enter a port for listening to downloads: ");
        this.myListenPort = Integer.parseInt(console.nextLine());

        serverTransport.sendLine("LOGIN " + username + " " + password);
        String response = serverTransport.readLine();

        if (response != null && response.startsWith("LOGIN_SUCCESS")) {
            String[] parts = response.split(" ", 2);
            String[] payload = parts[1].split(";", 3);

            // Create appropriate User instance based on admin status
            String receivedUsername = payload[0];
            boolean isAdmin = Boolean.parseBoolean(payload[1]);
            String sharedDirFromServer = payload[2];

            if (isAdmin) {
                this.loggedInUser = new AdminUser(""); //PasswordHash not needed on client side
            } else {
                this.loggedInUser = new RegularUser(receivedUsername, "", sharedDirFromServer);
            }

            System.out.println("Login successful. Welcome, " + loggedInUser.getUsername() + "!");
            System.out.println("Your registered shared directory is: " + sharedDirFromServer);

            initializeLocalServices(sharedDirFromServer, serverTransport);
        } else {
            System.out.println("Login failed: " + (response != null ? response : "No response from server."));
        }
    }

    private void handleSignUp(Transport serverTransport, Scanner console) throws IOException {
        System.out.print("Choose a username: ");
        String username = console.nextLine();
        System.out.print("Choose a password: ");
        String password = console.nextLine();
        System.out.print("Enter path to your shared folder (e.g., 'my_files' or 'C:\\Users\\YourUser\\p2p_share'): ");
        String sharedDirectoryPath = console.nextLine();

        serverTransport.sendLine("SIGNUP " + username + " " + password + " " + sharedDirectoryPath);
        String response = serverTransport.readLine();
        if (response != null && response.startsWith("SIGNUP_SUCCESS")) {
            System.out.println("Signup successful! You can now log in.");
        } else {
            System.out.println("Signup failed: " + (response != null ? response : "No response from server."));
        }
    }

    private void handleLogout(Transport serverTransport) throws IOException {
        System.out.println("Logging out...");
        serverTransport.sendLine("UNREGISTER");
        shutdownLocalServices();
        this.loggedInUser = null;
        this.knownSharedFiles.clear();
    }

    private void initializeLocalServices(String sharedDirectoryPath, Transport serverTransport) throws IOException {
        this.fileHandler = new LocalFileHandler(sharedDirectoryPath);
        Path sharedDir = ((LocalFileHandler) fileHandler).getSharedDirectory().toAbsolutePath();
        this.downloadStrategy = new ChunkedDownload(8192, fileHandler, sharedDir);
        System.out.println("File sharing is active for directory: " + sharedDir);

        this.downloadListenerSocket = new ServerSocket(myListenPort);
        this.downloadListenerThread = new Thread(new DownloadListener(downloadListenerSocket, fileHandler, this));
        this.downloadListenerThread.start();

        this.directoryWatcherThread = new Thread(new DirectoryWatcher(sharedDir, serverTransport));
        this.directoryWatcherThread.start();

        serverTransport.sendLine("REGISTER " + myListenPort);
        shareInitialFiles(serverTransport);
    }

    private void shareInitialFiles(Transport serverTransport) {
        System.out.println("Sharing initial files...");
        List<String> files = fileHandler.listSharedFiles();
        if (files.isEmpty()) {
            System.out.println("... No files found to share.");
        } else {
            for (String fileName : files) {
                if (knownSharedFiles.add(fileName)) {
                    System.out.println("... sharing " + fileName);
                    try {
                        serverTransport.sendLine("SHARE " + fileName);
                    } catch (IOException e) {
                        System.err.println("Failed to share file " + fileName + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    private void shutdownLocalServices() {
        if (directoryWatcherThread != null && directoryWatcherThread.isAlive()) {
            directoryWatcherThread.interrupt();
        }
        if (downloadListenerSocket != null && !downloadListenerSocket.isClosed()) {
            try { downloadListenerSocket.close(); } catch (IOException e) { /* ignore */ }
        }
        if (downloadListenerThread != null && downloadListenerThread.isAlive()) {
            downloadListenerThread.interrupt();
        }
        System.out.println("Local services shut down.");
    }

    private void handleListPeers(Transport serverTransport) throws IOException {
        serverTransport.sendLine("LIST_PEERS");
        String response = serverTransport.readLine();
        System.out.println("\n--- Online Peers ---");
        if (response == null || response.isEmpty()) {
            System.out.println("No other peers are online.");
        } else {
            Arrays.stream(response.split(",")).forEach(System.out::println);
        }
        System.out.println("--------------------");
    }

    private void handleSearchAndDownload(Transport serverTransport, Scanner console) throws IOException {
        System.out.print("Enter filename to search for: ");
        String fileName = console.nextLine();
        serverTransport.sendLine("SEARCH " + fileName);
        String response = serverTransport.readLine();

        if (response == null || response.isEmpty()) {
            System.out.println("File '" + fileName + "' not found on any peer.");
            return;
        }

        List<String> peersWithFile = Arrays.asList(response.split(","));
        System.out.println("\n--- File available on ---");
        for (int i = 0; i < peersWithFile.size(); i++) {
            System.out.printf("[%d] %s\n", i + 1, peersWithFile.get(i));
        }
        System.out.println("-------------------------");
        System.out.print("Choose a peer to download from (enter number, or 0 to cancel): ");

        try {
            int choice = Integer.parseInt(console.nextLine());
            if (choice > 0 && choice <= peersWithFile.size()) {
                String peerAddress = peersWithFile.get(choice - 1);
                downloadFile(peerAddress, fileName);
                serverTransport.sendLine("SHARE " + fileName);
            } else {
                System.out.println("Download cancelled.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a number.");
        }
    }

    private void downloadFile(String peerAddress, String fileName) {
        System.out.println("Attempting to download '" + fileName + "' from " + peerAddress + "...");
        try {
            long fileSizeBefore = getFileSize(fileName);
            downloadStrategy.download(peerAddress, fileName);
            long fileSizeAfter = getFileSize(fileName);

            // Update download stats
            if (fileSizeAfter > fileSizeBefore) {
                loggedInUser.getDownloadStats().addFile();
                loggedInUser.getDownloadStats().addBytes(fileSizeAfter - fileSizeBefore);
                System.out.println("Download complete: " + fileName + " (" + (fileSizeAfter - fileSizeBefore) + " bytes)");
            } else {
                System.out.println("Download complete: " + fileName);
            }
        } catch (IOException e) {
            System.err.println("Download failed: " + e.getMessage());
        }
    }

    private long getFileSize(String fileName) {
        try {
            if (fileHandler.fileExists(fileName)) {
                Path filePath = ((LocalFileHandler) fileHandler).getSharedDirectory().resolve(fileName);
                return Files.size(filePath);
            }
        } catch (IOException e) {
            // Ignore
        }
        return 0;
    }


    private void handleRemoveUser(Transport serverTransport, Scanner console) throws IOException {
        System.out.print("Enter username to remove: ");
        String userToRemove = console.nextLine();
        serverTransport.sendLine("REMOVE_USER " + userToRemove);
        String response = serverTransport.readLine();
        System.out.println("Server response: " + response);
    }

    private void handleViewStatistics() {
        System.out.println("\n--- Your Statistics ---");
        System.out.println("Downloaded files: " + loggedInUser.getDownloadStats().getFileCount());
        System.out.println("Downloaded bytes: " + loggedInUser.getDownloadStats().getTotalBytes());
        System.out.println("Uploaded files: " + loggedInUser.getUploadStats().getFileCount());
        System.out.println("Uploaded bytes: " + loggedInUser.getUploadStats().getTotalBytes());
        System.out.println("-------------------------");
    }

    private static class DownloadListener implements Runnable {
        private final ServerSocket listenerSocket;
        private final FileHandler fileHandler;
        private final PeerClient peerClient;

        public DownloadListener(ServerSocket socket, FileHandler fileHandler, PeerClient peerClient) {
            this.listenerSocket = socket;
            this.fileHandler = fileHandler;
            this.peerClient = peerClient;
        }

        @Override
        public void run() {
            System.out.println("Download listener started on port " + listenerSocket.getLocalPort());
            while (!Thread.currentThread().isInterrupted() && !listenerSocket.isClosed()) {
                try {
                    Socket peerConnection = listenerSocket.accept();
                    new Thread(() -> handleDownloadRequest(peerConnection)).start();
                } catch (IOException e) {
                    if (listenerSocket.isClosed()) {
                        System.out.println("Download listener shutting down.");
                        break;
                    }
                    System.err.println("Error in download listener: " + e.getMessage());
                }
            }
        }

        private void handleDownloadRequest(Socket peerConnection) {
            try (Transport transport = new TCPTransport(peerConnection)) {
                String request = transport.readLine();
                if (request != null && request.startsWith("DOWNLOAD ")) {
                    String fileName = request.substring(9);
                    if (fileHandler.fileExists(fileName)) {
                        long fileSize = 0;
                        try (InputStream fileIn = fileHandler.getInputStream(fileName)) {
                            //Sending the stuff in 8KB chunks
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = fileIn.read(buffer)) != -1) {
                                transport.sendBytes(buffer, bytesRead);
                                fileSize += bytesRead;
                            }
                            if (peerClient.loggedInUser != null && fileSize > 0) {
                                peerClient.loggedInUser.getUploadStats().addFile();
                                peerClient.loggedInUser.getUploadStats().addBytes(fileSize);
                                System.out.println("\n[Upload] Sent " + fileName + " (" + fileSize + " bytes)");
                                System.out.print("> ");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
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
                    List<String> currentFilesOnDisk = fileHandler.listSharedFiles();

                    for (String fileName : currentFilesOnDisk) {
                        if (knownSharedFiles.add(fileName)) {
                            System.out.println("\n[Auto-Detector] New file found: " + fileName + ". Sharing with network...");
                            try {
                                serverTransport.sendLine("SHARE " + fileName);
                            } catch (IOException e) {
                                System.err.println("Failed to auto-share file " + fileName + ": " + e.getMessage());
                            }
                            System.out.print("> ");
                        }
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
}
