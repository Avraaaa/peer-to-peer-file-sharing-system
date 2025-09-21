import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server{

    private static final int PORT = 9090;
    private static final List<FileEntry> fileRegistry = new ArrayList<>();
    private static final List<PeerInfo> activePeers = new ArrayList<>();
    private static final AccountService accountService = new AccountService("users.csv");

    public static void main(String[] args) throws IOException {
        System.out.println("Napster-style Server is running on port " + PORT);
        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket listener = new ServerSocket(PORT)) {
            while (true) {
                pool.execute(new ClientHandler(listener.accept()));
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

        @Override
        public void run() {
            System.out.println("Connected: " + socket.getRemoteSocketAddress());
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String command;
                while ((command = in.readLine()) != null) {
                    String userIdentifier;

                    if (loggedInUser != null) {
                        userIdentifier = loggedInUser.getUsername();
                    } else {
                        userIdentifier = "unauthenticated session " + socket.getRemoteSocketAddress();
                    }
                    System.out.println("Received from " + userIdentifier + ": " + command);

                    String[] parts = command.split(" ", 3);
                    String action = parts[0].toUpperCase();

                    switch (action) {
                        case "LOGIN":
                            if (parts.length < 3) continue;
                            handleLogin(parts[1], parts[2], out);
                            break;
                        case "SIGNUP":
                            if (parts.length < 3) continue;
                            handleSignup(parts[1], parts[2], out);
                            break;
                        case "REGISTER":
                            if (loggedInUser == null) {
                                out.println("ERROR Not logged in");
                                continue;
                            }
                            if (parts.length < 2) continue;
                            handleRegisterPeer(parts[1]);
                            break;
                        case "SHARE":
                            if (loggedInUser == null || peerInfo == null) {
                                out.println("ERROR Not registered");
                                continue;
                            }
                            if (parts.length < 2) continue;
                            handleShare(parts[1]);
                            break;
                        case "SEARCH":
                            if (loggedInUser == null) {
                                out.println("ERROR Not logged in");
                                continue;
                            }
                            if (parts.length < 2) continue;
                            handleSearch(parts[1], out);
                            break;
                        case "LIST_PEERS":
                            if (loggedInUser == null) {
                                out.println("ERROR Not logged in");
                                continue;
                            }
                            handleListPeers(out);
                            break;
                        case "UPDATE_STATS":
                            if (loggedInUser == null) {
                                out.println("ERROR Not logged in");
                                continue;
                            }
                            if (parts.length < 3) continue;
                            handleUpdateStats(parts[1], parts[2]);
                            break;
                        case "REMOVE_USER":
                            if (loggedInUser == null || !loggedInUser.isAdmin()) {
                                out.println("ERROR Not authorized");
                                continue;
                            }
                            if (parts.length < 2) continue;
                            handleRemoveUser(parts[1], out);
                            break;
                        case "CHANGE_PASSWORD":
                            if (loggedInUser == null) {
                                out.println("ERROR Not logged in");
                                continue;
                            }
                            if (parts.length < 3) continue;
                            handleChangePassword(parts[1], parts[2], out);
                            break;

                        case "DELETE_ACCOUNT":
                            if (loggedInUser == null) {
                                out.println("ERROR Not logged in");
                                continue;
                            }
                            if (parts.length < 3) continue;
                            handleDeleteAccount(parts[1], parts[2], out);
                            break;
                        case "UNREGISTER":
                            return;
                        default:
                            out.println("ERROR Unknown command");
                    }
                }
            } catch (IOException e) {
                System.err.println("Connection error with " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
            } finally {
                unregisterPeer();
                try {
                    socket.close();
                } catch (IOException e) { /* Ignore */ }
                String username = (peerInfo != null) ? peerInfo.username : socket.getRemoteSocketAddress().toString();
                System.out.println("Closed connection: " + username);
            }
        }

        private void handleLogin(String username, String password, PrintWriter out) {
            User user = accountService.login(username, password);
            if (user != null) {
                this.loggedInUser = user;
                String payload = String.join(";",
                        user.getUsername(),
                        String.valueOf(user.isAdmin()),
                        user.getDownloadStats().toCsvString(),
                        user.getUploadStats().toCsvString()
                );
                out.println("LOGIN_SUCCESS " + payload);
                System.out.println("User '" + username + "' logged in successfully.");
            } else {
                out.println("LOGIN_FAIL Invalid username or password.");
                System.out.println("Failed login attempt for user '" + username + "'.");
            }
        }

        private void handleSignup(String username, String password, PrintWriter out) {
            try {
                accountService.createUser(username, password);
                out.println("SIGNUP_SUCCESS");
                System.out.println("New user '" + username + "' created.");
            } catch (IOException e) {
                out.println("SIGNUP_FAIL " + e.getMessage());
                System.err.println("Failed signup for user '" + username + "': " + e.getMessage());
            }
        }

        private void handleUpdateStats(String downloadStatsCsv, String uploadStatsCsv) {
            try {
                loggedInUser.getDownloadStats().fromCsvString(downloadStatsCsv);
                loggedInUser.getUploadStats().fromCsvString(uploadStatsCsv);
                accountService.saveUserStats(loggedInUser);
            } catch (IOException e) {
                System.err.println("Could not update stats for user " + loggedInUser.getUsername() + ": " + e.getMessage());
            }
        }

        private void handleRemoveUser(String username, PrintWriter out) {
            try {
                if (accountService.removeUser(username)) {
                    out.println("REMOVE_SUCCESS");
                    System.out.println("Admin '" + loggedInUser.getUsername() + "' removed user '" + username + "'.");
                } else {
                    out.println("REMOVE_FAIL Could not remove user. They may not exist or are an admin.");
                }
            } catch (IOException e) {
                out.println("REMOVE_FAIL " + e.getMessage());
            }
        }

        private void handleRegisterPeer(String peerListenPort) {
            int port = Integer.parseInt(peerListenPort);
            String peerAddress = socket.getInetAddress().getHostAddress() + ":" + port;
            this.peerInfo = new PeerInfo(loggedInUser.getUsername(), peerAddress);
            synchronized (activePeers) {
                if (!activePeers.contains(this.peerInfo)) {
                    activePeers.add(this.peerInfo);
                    System.out.println("Peer registered: " + peerAddress + " as user '" + loggedInUser.getUsername() + "'");
                }
            }
        }

        private void handleShare(String fileName) {
            if (peerInfo == null) {
                System.out.println("DEBUG: handleShare called but peerInfo is null for file: " + fileName);
                return;
            }

            System.out.println("DEBUG: Sharing file '" + fileName + "' from peer " + peerInfo.username + " at " + peerInfo.address);

            synchronized (fileRegistry) {
                FileEntry entry = null;

                for (FileEntry fe : fileRegistry) {
                    if (fe.fileName.equals(fileName)) {
                        entry = fe;
                        break;
                    }
                }

                if (entry == null) {
                    entry = new FileEntry(fileName);
                    fileRegistry.add(entry);
                    System.out.println("DEBUG: Created new FileEntry for: " + fileName);
                } else {
                    System.out.println("DEBUG: Found existing FileEntry for: " + fileName);
                }

                entry.addPeer(peerInfo);
                System.out.println("DEBUG: Added peer " + peerInfo.username + " to file " + fileName);
                System.out.println("DEBUG: File " + fileName + " now has " + entry.peers.size() + " peer(s)");
                System.out.println("DEBUG: Total files in registry: " + fileRegistry.size());
            }
        }


        private void handleSearch(String searchTerm, PrintWriter out) {
            StringBuilder response = new StringBuilder();
            Map<String, Set<PeerInfo>> matchingFiles = new LinkedHashMap<>();

            // Convert search term to lowercase for case-insensitive matching
            String lowerSearchTerm = searchTerm.toLowerCase().trim();

            System.out.println("DEBUG: Searching for: '" + searchTerm + "' (normalized: '" + lowerSearchTerm + "')");

            synchronized (fileRegistry) {
                System.out.println("DEBUG: Total files in registry: " + fileRegistry.size());

                // Find all files that match the search criteria
                for (FileEntry entry : fileRegistry) {
                    String fileName = entry.fileName;
                    String lowerFileName = fileName.toLowerCase();

                    System.out.println("DEBUG: Checking file: '" + fileName + "' against '" + lowerSearchTerm + "'");

                    boolean matches = false;

                    // Method 1: Direct substring match (for partial names)
                    if (lowerFileName.contains(lowerSearchTerm)) {
                        matches = true;
                        System.out.println("DEBUG: Matched by substring");
                    }

                    // Method 2: File extension match (if search starts with .)
                    else if (lowerSearchTerm.startsWith(".")) {
                        if (lowerFileName.endsWith(lowerSearchTerm)) {
                            matches = true;
                            System.out.println("DEBUG: Matched by extension");
                        }
                    }

                    // Method 3: Extension match without dot (e.g., "mp3" matches ".mp3" files)
                    else {
                        String searchAsExtension = "." + lowerSearchTerm;
                        if (lowerFileName.endsWith(searchAsExtension)) {
                            matches = true;
                            System.out.println("DEBUG: Matched by extension (without dot)");
                        }
                    }

                    // Method 4: Word boundary matching (for better partial matches)
                    if (!matches) {
                        String[] searchWords = lowerSearchTerm.split("\\s+");
                        boolean allWordsMatch = true;
                        for (String word : searchWords) {
                            if (!lowerFileName.contains(word)) {
                                allWordsMatch = false;
                                break;
                            }
                        }
                        if (allWordsMatch && searchWords.length > 0) {
                            matches = true;
                            System.out.println("DEBUG: Matched by word boundary");
                        }
                    }

                    if (matches && !entry.peers.isEmpty()) {
                        // Only include files that have active peers
                        matchingFiles.computeIfAbsent(fileName, k -> new LinkedHashSet<>())
                                .addAll(entry.peers);
                        System.out.println("DEBUG: Added file '" + fileName + "' with " + entry.peers.size() + " peers");
                    }
                }
            }

            // Build response in format: filename1=peer1:address1,peer2:address2;filename2=peer3:address3
            boolean first = true;
            for (Map.Entry<String, Set<PeerInfo>> entry : matchingFiles.entrySet()) {
                if (!first) {
                    response.append(";");
                }
                first = false;

                response.append(entry.getKey()).append("=");

                boolean firstPeer = true;
                for (PeerInfo peer : entry.getValue()) {
                    if (!firstPeer) {
                        response.append(",");
                    }
                    firstPeer = false;
                    response.append(peer.username).append(":").append(peer.address);
                }
            }

            String finalResponse = response.toString();

            // Debug output
            System.out.println("DEBUG: Search for '" + searchTerm + "' found " + matchingFiles.size() + " files");
            System.out.println("DEBUG: Server sending response: '" + finalResponse + "'");

            out.println(finalResponse);

            // Log the search
            System.out.println("User '" + loggedInUser.getUsername() + "' searched for '" +
                    searchTerm + "' - found " + matchingFiles.size() + " matching files");
        }

        private void handleChangePassword(String currentPassword, String newPassword, PrintWriter out) {
            try {
                // Get the user's stored password hash and verify current password
                String storedHash = loggedInUser.getPasswordHash();
                if (!accountService.verifyPassword(currentPassword, storedHash)) {
                    out.println("CHANGE_PASSWORD_FAIL Current password is incorrect");
                    System.out.println("Password change failed for user '" + loggedInUser.getUsername() + "': incorrect current password");
                    return;
                }

                // Change password
                if (accountService.changePassword(loggedInUser.getUsername(), newPassword)) {
                    out.println("CHANGE_PASSWORD_SUCCESS");
                    System.out.println("User '" + loggedInUser.getUsername() + "' changed their password successfully");
                } else {
                    out.println("CHANGE_PASSWORD_FAIL Could not update password");
                }
            } catch (IOException e) {
                out.println("CHANGE_PASSWORD_FAIL " + e.getMessage());
                System.err.println("Error changing password for user " + loggedInUser.getUsername() + ": " + e.getMessage());
            }
        }

        private void handleDeleteAccount(String username, String password, PrintWriter out) {
            try {
                // Verify it's the same user
                if (!loggedInUser.getUsername().equals(username)) {
                    out.println("DELETE_ACCOUNT_FAIL Username mismatch");
                    return;
                }

                // Get the user's stored password hash and verify password
                String storedHash = loggedInUser.getPasswordHash();
                if (!accountService.verifyPassword(password, storedHash)) {
                    out.println("DELETE_ACCOUNT_FAIL Incorrect password");
                    System.out.println("Account deletion failed for user '" + username + "': incorrect password");
                    return;
                }

                // Don't allow admin to delete themselves if they're the only admin
                if (loggedInUser.isAdmin() && accountService.isOnlyAdmin(username)) {
                    out.println("DELETE_ACCOUNT_FAIL Cannot delete the only admin account");
                    return;
                }

                // Delete account
                if (accountService.removeUser(username)) {
                    out.println("DELETE_ACCOUNT_SUCCESS");
                    System.out.println("User '" + username + "' deleted their own account");

                    // Force disconnect after successful deletion
                    unregisterPeer();
                    return; // This will close the connection
                } else {
                    out.println("DELETE_ACCOUNT_FAIL Could not delete account");
                }
            } catch (IOException e) {
                out.println("DELETE_ACCOUNT_FAIL " + e.getMessage());
                System.err.println("Error deleting account for user " + username + ": " + e.getMessage());
            }
        }
        private void handleListPeers(PrintWriter out) {
            StringBuilder response = new StringBuilder();

            synchronized (activePeers) {
                for (PeerInfo pi : activePeers) {
                    if (response.length() > 0) {
                        response.append(",");
                    }
                    response.append(pi.username).append("=").append(pi.address);
                }
            }

            out.println(response);
        }

        private void unregisterPeer() {
            if (peerInfo != null) {
                System.out.println("Unregistering peer: " + peerInfo.address + " ('" + peerInfo.username + "')");

                synchronized (activePeers) {
                    activePeers.remove(peerInfo);
                }

                synchronized (fileRegistry) {
                    for (FileEntry entry : fileRegistry) {
                        entry.removePeer(peerInfo);
                    }
                }
            }
        }
     }
}
