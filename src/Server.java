import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final int PORT = 9090;
    private static final String OUTPUT_DIRECTORY = "out";
    private static final List<FileEntry> fileRegistry = new ArrayList<>();
    private static final List<PeerInfo> activePeers = new ArrayList<>();
    private static final AccountService accountService = new AccountService(OUTPUT_DIRECTORY + "/users.csv");

    private static final Map<String, ClientHandler> activeClients = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        Files.createDirectories(Paths.get(OUTPUT_DIRECTORY));
        System.out.println("Simple Napster Server running on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                new Thread(new ClientHandler(serverSocket.accept())).start();
            }
        }
    }

    private static class PeerInfo {
        String username;
        String address;

        PeerInfo(String username, String address) {
            this.username = username;
            this.address = address;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PeerInfo)) return false;
            PeerInfo other = (PeerInfo) obj;
            return Objects.equals(this.address, other.address);
        }

        @Override
        public int hashCode() {
            return Objects.hash(address);
        }
    }

    private static class FileEntry {
        String fileName;
        List<PeerInfo> peers = new ArrayList<>();

        FileEntry(String fileName) {
            this.fileName = fileName;
        }

        void addPeer(PeerInfo peer) {
            if (!peers.contains(peer)) {
                peers.add(peer);
            }
        }

        void removePeer(PeerInfo peer) {
            peers.remove(peer);
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private PeerInfo peerInfo;
        private User loggedInUser;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        // Forcibly close client's socket
        public void stopClient() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing socket for " + (loggedInUser != null ? loggedInUser.getUsername() : "a client") + ": " + e.getMessage());
            }
        }


        @Override
        public void run() {
            String clientIdentifier = socket.getRemoteSocketAddress().toString();
            System.out.println("Connected: " + clientIdentifier);

            try (Transport transport = new TCPTransport(socket)) {
                String command;
                while ((command = transport.readLine()) != null) {
                    if (loggedInUser != null) {
                        clientIdentifier = loggedInUser.getUsername();
                    }

                    String[] parts = command.split(" ", 2);
                    String action = parts[0].toUpperCase();

                    switch (action) {
                        case "LOGIN":
                            String[] loginParts = command.split(" ", 3);
                            if (loginParts.length < 3) continue;
                            handleLogin(loginParts[1], loginParts[2], transport);
                            break;

                        case "SIGNUP":
                            String[] signupParts = command.split(" ", 3);
                            if (signupParts.length < 3) continue;
                            handleSignUp(signupParts[1], signupParts[2], transport);
                            break;

                        case "REGISTER":
                            if (loggedInUser == null) {
                                transport.sendLine("ERROR Not logged in");
                                continue;
                            }
                            if (parts.length < 2) continue;
                            handleRegisterPeer(parts[1]);
                            break;
                        case "SHARE":
                            if (loggedInUser == null || peerInfo == null) {
                                transport.sendLine("ERROR Not logged in or peer not registered");
                                continue;
                            }
                            if (parts.length < 2) continue;
                            handleShare(parts[1]);
                            break;
                        case "SEARCH":
                            if (loggedInUser == null) {
                                transport.sendLine("ERROR Not logged in");
                                continue;
                            }
                            if (parts.length < 2) continue;
                            handleSearch(parts[1], transport);
                            break;
                        case "LIST_PEERS":
                            if (loggedInUser == null) {
                                transport.sendLine("ERROR Not logged in");
                                continue;
                            }
                            handleListPeers(transport);
                            break;
                        case "REMOVE_USER":
                            if (loggedInUser == null) {
                                transport.sendLine("ERROR Not logged in");
                                continue;
                            }
                            if (!loggedInUser.isAdmin()) {
                                transport.sendLine("ERROR Not authorized");
                                continue;
                            }
                            if (parts.length < 2) continue;
                            handleRemoveUser(parts[1], transport);
                            break;
                        case "UNREGISTER":
                            if (loggedInUser != null) {
                                try {
                                    accountService.saveUserStats(loggedInUser);
                                } catch (IOException e) {
                                    System.err.println("Failed to save stats for user " + loggedInUser.getUsername() + ": " + e.getMessage());
                                }
                            }
                            return;
                        case "UPDATE_STATS":
                            if (loggedInUser == null) {
                                continue;
                            }
                            if (parts.length < 2) continue;
                            handleUpdateStats(parts[1]);
                            break;
                        default:
                            transport.sendLine("ERROR Unknown command");
                    }
                }
            } catch (IOException e) {
                // Ignore "Socket closed" errors which are expected when kicking a user
                if (!"Socket closed".equals(e.getMessage())) {
                    System.err.println("Connection error with " + clientIdentifier + ": " + e.getMessage());
                }
            } finally {
                unregisterPeer();
                try {
                    if (!socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    // Ignore
                }
                System.out.println("Disconnected: " + clientIdentifier);
            }
        }

        private void handleLogin(String username, String password, Transport transport) throws IOException {
            User user = accountService.login(username, password);
            if (user != null) {
                this.loggedInUser = user;
                String payload = String.join(";",
                        user.getUsername(),
                        String.valueOf(user.isAdmin()),
                        user.getDownloadStats().toCsvString(),
                        user.getUploadStats().toCsvString()
                );
                transport.sendLine("LOGIN_SUCCESS " + payload);
                System.out.println("User '" + username + "' logged in successfully.");

                activeClients.put(username, this);

            } else {
                transport.sendLine("LOGIN_FAIL Invalid username or password.");
                System.out.println("Failed login attempt for user '" + username + "'.");
            }
        }

        private void handleSignUp(String username, String password, Transport transport) throws IOException {
            try {
                User newUser = accountService.createUser(username, password);
                if (newUser != null) {
                    transport.sendLine("SIGNUP_SUCCESS User created. Please login.");
                    System.out.println("New user signed up: " + username);
                }
            } catch (IOException e) {
                transport.sendLine("SIGNUP_FAIL " + e.getMessage());
                System.err.println("Failed to create user '" + username + "': " + e.getMessage());
            }
        }

        private void handleRegisterPeer(String portStr) {
            String peerAddr = socket.getInetAddress().getHostAddress() + ":" + portStr;
            this.peerInfo = new PeerInfo(loggedInUser.getUsername(), peerAddr);
            synchronized (activePeers) {
                if (!activePeers.contains(peerInfo)) {
                    activePeers.add(peerInfo);
                    System.out.println("Peer registered: " + peerAddr + " for user: " + loggedInUser.getUsername());
                }
            }
        }

        private void handleShare(String fileName) {
            synchronized (fileRegistry) {
                FileEntry foundEntry = null;

                for (FileEntry currentEntry : fileRegistry) {
                    if (currentEntry.fileName.equals(fileName)) {
                        foundEntry = currentEntry;
                        break;
                    }
                }

                if (foundEntry == null) {
                    FileEntry newEntry = new FileEntry(fileName);
                    fileRegistry.add(newEntry);
                    foundEntry = newEntry;
                }

                foundEntry.addPeer(peerInfo);
                System.out.println("User '" + loggedInUser.getUsername() + "' is sharing: " + fileName);
            }
        }


        private void handleSearch(String fileName, Transport transport) throws IOException {
            StringBuilder sb = new StringBuilder();
            synchronized (fileRegistry) {
                for (FileEntry entry : fileRegistry) {
                    if (entry.fileName.equalsIgnoreCase(fileName)) {
                        for (PeerInfo peer : entry.peers) {
                            if (!sb.isEmpty()) {
                                sb.append(",");
                            }
                            sb.append(peer.address);
                        }
                    }
                }
            }
            transport.sendLine(sb.toString());
        }

        private void handleListPeers(Transport transport) throws IOException {
            StringBuilder sb = new StringBuilder();
            synchronized (activePeers) {
                for (int i = 0; i < activePeers.size(); i++) {
                    sb.append(activePeers.get(i).address);
                    if (i < activePeers.size() - 1) {
                        sb.append(",");
                    }
                }
            }
            transport.sendLine(sb.toString());
        }

        private void handleRemoveUser(String username, Transport transport) throws IOException {
            // Forcibly disconnect the user if they are currently online
            ClientHandler handlerToKick = activeClients.get(username);
            if (handlerToKick != null) {
                System.out.println("Kicking user '" + username + "'...");
                handlerToKick.stopClient();
                activeClients.remove(username); // Also remove from tracking map
            }

            try {
                if (accountService.removeUser(username)) {
                    transport.sendLine("REMOVE_SUCCESS " + username + " removed.");
                    System.out.println("Admin '" + loggedInUser.getUsername() + "' removed user '" + username + "'");
                } else {
                    transport.sendLine("REMOVE_FAIL User not found or cannot be removed.");
                }
            } catch (IOException e) {
                transport.sendLine("REMOVE_FAIL Error removing user: " + e.getMessage());
                System.err.println("Error removing user '" + username + "': " + e.getMessage());
            }
        }

        private void unregisterPeer() {
            if (peerInfo != null) {
                synchronized (activePeers) {
                    activePeers.remove(peerInfo);
                }
                synchronized (fileRegistry) {
                    for (FileEntry entry : fileRegistry) {
                        entry.removePeer(peerInfo);
                    }
                }
                System.out.println("Peer unregistered: " + peerInfo.address);
            }

            if (loggedInUser != null) {
                activeClients.remove(loggedInUser.getUsername());
            }
        }

        private void handleUpdateStats(String statsData) {
            try {
                String[] statsParts = statsData.split(";");
                if (statsParts.length == 2) {
                    loggedInUser.getDownloadStats().fromCsvString(statsParts[0]);
                    loggedInUser.getUploadStats().fromCsvString(statsParts[1]);
                    accountService.saveUserStats(loggedInUser);
                    System.out.println("Stats updated for user '" + loggedInUser.getUsername() + "': " + statsData);
                } else {
                    System.err.println("Invalid stats format from user '" + loggedInUser.getUsername() + "'");
                }
            } catch (Exception e) {
                System.err.println("Error updating stats for user '" + loggedInUser.getUsername() + "': " + e.getMessage());
            }
        }
    }
}