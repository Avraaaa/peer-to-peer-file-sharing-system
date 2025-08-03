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
                Socket serverSocket = new Socket(serverHost, serverPort);
                PrintWriter out = new PrintWriter(serverSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
                Scanner console = new Scanner(System.in)
        ) {
            System.out.println("Connected to the Napster server.");

            while (true) {
                if (loggedInUser == null) {
                    showPreLoginMenu();
                    String choice = console.nextLine();
                    switch (choice) {
                        case "1": handleLogin(out, in, console); break;
                        case "2": handleSignUp(out, in, console); break;
                        case "3": System.out.println("Exiting client."); return;
                        default: System.out.println("Invalid option. Please try again.");
                    }
                } else {
                    showPostLoginMenu();
                    String choice = console.nextLine();
                    switch (choice) {
                        case "1": handleSearchAndDownload(out, in, console); break;
                        case "2": handleListPeers(out, in); break;
                        case "3":
                            if (loggedInUser.isAdmin) handleRemoveUser(out, in, console);
                            else handleLogout(out);
                            break;
                        case "4":
                            if (loggedInUser.isAdmin) handleLogout(out);
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
        System.out.printf("\n--- Logged in as %s ---\n", loggedInUser.username);
        System.out.println("[1] Search for a file");
        System.out.println("[2] List online peers");
        if (loggedInUser.isAdmin) {
            System.out.println("[3] Remove a user (Admin)");
            System.out.println("[4] Logout");
        } else {
            System.out.println("[3] Logout");
        }
        System.out.print("> ");
    }

    private void handleLogin(PrintWriter out, BufferedReader in, Scanner console) throws IOException {
        System.out.print("Enter username: ");
        String username = console.nextLine();
        System.out.print("Enter password: ");
        String password = console.nextLine();
        System.out.print("Enter a port for listening to downloads: ");
        this.myListenPort = Integer.parseInt(console.nextLine());

        out.println("LOGIN " + username + " " + password);
        String response = in.readLine();

        if (response != null && response.startsWith("LOGIN_SUCCESS")) {
            String[] parts = response.split(" ", 2);
            String[] payload = parts[1].split(";", 3);

            this.loggedInUser = new User(payload[0], Boolean.parseBoolean(payload[1]));
            String sharedDirFromServer = payload[2];

            System.out.println("Login successful. Welcome, " + loggedInUser.username + "!");
            System.out.println("Your registered shared directory is: " + sharedDirFromServer);

            initializeLocalServices(sharedDirFromServer, out);
        } else {
            System.out.println("Login failed: " + (response != null ? response : "No response from server."));
        }
    }

    private void handleSignUp(PrintWriter out, BufferedReader in, Scanner console) throws IOException {
        System.out.print("Choose a username: ");
        String username = console.nextLine();
        System.out.print("Choose a password: ");
        String password = console.nextLine();
        System.out.print("Enter path to your shared folder (e.g., 'my_files' or 'C:\\Users\\YourUser\\p2p_share'): ");
        String sharedDirectoryPath = console.nextLine();

        out.println("SIGNUP " + username + " " + password + " " + sharedDirectoryPath);
        String response = in.readLine();
        if (response != null && response.startsWith("SIGNUP_SUCCESS")) {
            System.out.println("Signup successful! You can now log in.");
        } else {
            System.out.println("Signup failed: " + (response != null ? response : "No response from server."));
        }
    }

    private void handleLogout(PrintWriter out) {
        System.out.println("Logging out...");
        out.println("UNREGISTER");
        shutdownLocalServices();
        this.loggedInUser = null;
        this.knownSharedFiles.clear();
    }

    private void initializeLocalServices(String sharedDirectoryPath, PrintWriter serverOut) throws IOException {
        this.fileHandler = new LocalFileHandler(sharedDirectoryPath);
        Path sharedDir = ((LocalFileHandler) fileHandler).getSharedDirectory().toAbsolutePath();
        System.out.println("File sharing is active for directory: " + sharedDir);

        this.downloadListenerSocket = new ServerSocket(myListenPort);
        this.downloadListenerThread = new Thread(new DownloadListener(downloadListenerSocket, fileHandler));
        this.downloadListenerThread.start();

        this.directoryWatcherThread = new Thread(new DirectoryWatcher(sharedDir, serverOut));
        this.directoryWatcherThread.start();

        serverOut.println("REGISTER " + myListenPort);
        shareInitialFiles(serverOut);
    }

    private void shareInitialFiles(PrintWriter serverOut) {
        System.out.println("Sharing initial files...");
        List<String> files = fileHandler.listSharedFiles();
        if (files.isEmpty()) {
            System.out.println("... No files found to share.");
        } else {
            for (String fileName : files) {
                if (knownSharedFiles.add(fileName)) {
                    System.out.println("... sharing " + fileName);
                    serverOut.println("SHARE " + fileName);
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

    private void handleListPeers(PrintWriter out, BufferedReader in) throws IOException {
        out.println("LIST_PEERS");
        String response = in.readLine();
        System.out.println("\n--- Online Peers ---");
        if (response == null || response.isEmpty()) {
            System.out.println("No other peers are online.");
        } else {
            Arrays.stream(response.split(",")).forEach(System.out::println);
        }
        System.out.println("--------------------");
    }

    private void handleSearchAndDownload(PrintWriter out, BufferedReader in, Scanner console) throws IOException {
        System.out.print("Enter filename to search for: ");
        String fileName = console.nextLine();
        out.println("SEARCH " + fileName);
        String response = in.readLine();

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
                out.println("SHARE " + fileName);
            } else {
                System.out.println("Download cancelled.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a number.");
        }
    }

    private void downloadFile(String peerAddress, String fileName) {
        System.out.println("Attempting to download '" + fileName + "' from " + peerAddress + "...");
        String[] parts = peerAddress.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        try (
                Socket peerSocket = new Socket(host, port);
                PrintWriter peerOut = new PrintWriter(peerSocket.getOutputStream(), true);
                InputStream peerIn = peerSocket.getInputStream();
                OutputStream fileOut = fileHandler.getOutputStream(fileName)
        ) {
            peerOut.println("DOWNLOAD " + fileName);
            peerIn.transferTo(fileOut);
            System.out.println("Download complete: " + fileName);
        } catch (IOException e) {
            System.err.println("Download failed: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleRemoveUser(PrintWriter out, BufferedReader in, Scanner console) throws IOException {
        System.out.print("Enter username to remove: ");
        String userToRemove = console.nextLine();
        out.println("REMOVE_USER " + userToRemove);
        String response = in.readLine();
        System.out.println("Server response: " + response);
    }

    private static class User {
        final String username;
        final boolean isAdmin;

        User(String username, boolean isAdmin) {
            this.username = username;
            this.isAdmin = isAdmin;
        }
    }


    private static class DownloadListener implements Runnable {
        private final ServerSocket listenerSocket;
        private final FileHandler fileHandler;

        public DownloadListener(ServerSocket socket, FileHandler fileHandler) {
            this.listenerSocket = socket;
            this.fileHandler = fileHandler;
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
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(peerConnection.getInputStream()));
                    OutputStream out = peerConnection.getOutputStream()
            ) {
                String request = in.readLine();
                if (request != null && request.startsWith("DOWNLOAD ")) {
                    String fileName = request.substring(9);
                    if (fileHandler.fileExists(fileName)) {
                        try (InputStream fileIn = fileHandler.getInputStream(fileName)) {
                            fileIn.transferTo(out);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();

            } finally {
                try { peerConnection.close(); } catch (IOException e) { /* Ignore */ }
            }
        }
    }


    private class DirectoryWatcher implements Runnable {
        private final Path path;
        private final PrintWriter serverOut;
        private static final int POLLING_INTERVAL_MS = 3000;

        DirectoryWatcher(Path path, PrintWriter serverOut) {
            this.path = path;
            this.serverOut = serverOut;
        }

        @Override
        public void run() {
            System.out.println("Directory watcher started for: " + path + " (polling every 5 seconds)");

            try {
                while (!Thread.currentThread().isInterrupted()) {

                    List<String> currentFilesOnDisk = fileHandler.listSharedFiles();

                    for (String fileName : currentFilesOnDisk) {


                        if (knownSharedFiles.add(fileName)) {

                            System.out.println("\n[Auto-Detector] New file found: " + fileName + ". Sharing with network...");
                            serverOut.println("SHARE " + fileName);
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