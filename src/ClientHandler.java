import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final List<FileEntry> fileRegistry;
    private final List<PeerInfo> activePeers;
    private final AccountService accountService;
    private final Map<String, ClientHandler> activeHandlers;

    private PeerInfo peerInfo;
    private User loggedInUser;


    public ClientHandler(Socket socket, List<FileEntry> fileRegistry, List<PeerInfo> activePeers, AccountService accountService, Map<String, ClientHandler> activeHandlers) {
        this.socket = socket;
        this.fileRegistry = fileRegistry;
        this.activePeers = activePeers;
        this.accountService = accountService;
        this.activeHandlers = activeHandlers;
    }


    public void forceDisconnect() {
        try {
            // Before disconnecting inform the client
            if (!socket.isClosed() && socket.isConnected()) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println("FORCE_DISCONNECT You have been disconnected by an administrator.");
            }
        } catch (IOException e) {
            System.err.println("Error sending force disconnect message (client may already be gone): " + e.getMessage());
        } finally {
            try {

                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                // Ignore errors on close.
            }
        }
    }

    @Override
    public void run() {
        String clientIdentifier = socket.getRemoteSocketAddress().toString();
        System.out.println("Connected: " + clientIdentifier);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String command;
            while ((command = in.readLine()) != null) {
                String userIdentifier = (loggedInUser != null) ? loggedInUser.getUsername() : "unauthenticated " + clientIdentifier;
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
                        if (loggedInUser == null) { out.println("ERROR Not logged in"); continue; }
                        if (parts.length < 2) continue;
                        handleRegisterPeer(parts[1]);
                        break;
                    case "SHARE":
                        if (loggedInUser == null || peerInfo == null) { out.println("ERROR Not registered"); continue; }
                        if (parts.length < 2) continue;
                        handleShare(parts[1]);
                        break;
                    case "SEARCH":
                        if (loggedInUser == null) { out.println("ERROR Not logged in"); continue; }
                        if (parts.length < 2) continue;
                        handleSearch(parts[1], out);
                        break;
                    case "LIST_PEERS":
                        if (loggedInUser == null) { out.println("ERROR Not logged in"); continue; }
                        handleListPeers(out);
                        break;
                    case "UPDATE_STATS":
                        if (loggedInUser == null) { out.println("ERROR Not logged in"); continue; }
                        if (parts.length < 3) continue;
                        handleUpdateStats(parts[1], parts[2]);
                        break;
                    case "REMOVE_USER":
                        if (loggedInUser == null || !loggedInUser.isAdmin()) { out.println("ERROR Not authorized"); continue; }
                        if (parts.length < 2) continue;
                        handleRemoveUser(parts[1], out);
                        break;
                    case "CHANGE_PASSWORD":
                        if (loggedInUser == null) { out.println("ERROR Not logged in"); continue; }
                        if (parts.length < 3) continue;
                        handleChangePassword(parts[1], parts[2], out);
                        break;
                    case "DELETE_ACCOUNT":
                        if (loggedInUser == null) { out.println("ERROR Not logged in"); continue; }
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
            // Throw this exception on forced disconnect or abrupt disconnect by client
            System.err.println("Connection error or forced disconnect for " + clientIdentifier + ": " + e.getMessage());
        } finally {
            //Centralized cleanup logic
            if (loggedInUser != null) {
                activeHandlers.remove(loggedInUser.getUsername());
                System.out.println("Handler for '" + loggedInUser.getUsername() + "' removed from active map.");
            }

            unregisterPeer();

            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) { /* Ignore */ }

            String username = (peerInfo != null) ? peerInfo.username : clientIdentifier;
            System.out.println("Closed connection and cleaned up all resources for: " + username);
        }
    }

    private void handleLogin(String username, String password, PrintWriter out) {
        // A user cna only login from one client at a time
        if (activeHandlers.containsKey(username)) {
            out.println("LOGIN_FAIL User is already logged in elsewhere.");
            System.out.println("Failed login for '" + username + "': Already active.");
            return;
        }

        User user = accountService.login(username, password);
        if (user != null) {
            this.loggedInUser = user;

            activeHandlers.put(username, this);

            String payload = String.join(";",
                    user.getUsername(),
                    String.valueOf(user.isAdmin()),
                    user.getDownloadStats().toCsvString(),
                    user.getUploadStats().toCsvString()
            );
            out.println("LOGIN_SUCCESS " + payload);
            System.out.println("User '" + username + "' logged in. Handler is now registered.");
        } else {
            out.println("LOGIN_FAIL Invalid username or password.");
            System.out.println("Failed login attempt for user '" + username + "'.");
        }
    }

    private void handleRemoveUser(String username, PrintWriter out) {
        try {
            if (accountService.removeUser(username)) {
                System.out.println("Admin '" + loggedInUser.getUsername() + "' removed user '" + username + "' from account service.");

                ClientHandler handlerToDisconnect = activeHandlers.get(username);

                if (handlerToDisconnect != null) {
                    System.out.println("Found active session for '" + username + "'. Forcing disconnect.");
                    handlerToDisconnect.forceDisconnect();
                } else {
                    System.out.println("User '" + username + "' was not logged in. No active session to disconnect.");
                }

                out.println("REMOVE_SUCCESS");

            } else {
                out.println("REMOVE_FAIL Could not remove user. They may not exist or are an admin.");
            }
        } catch (IOException e) {
            out.println("REMOVE_FAIL " + e.getMessage());
            System.err.println("Error during user removal: " + e.getMessage());
        }
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
                fileRegistry.removeIf(entry -> entry.peers.isEmpty());
            }
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
        if (loggedInUser == null) return;
        try {
            loggedInUser.getDownloadStats().fromCsvString(downloadStatsCsv);
            loggedInUser.getUploadStats().fromCsvString(uploadStatsCsv);
            accountService.saveUserStats(loggedInUser);
        } catch (IOException e) {
            System.err.println("Could not update stats for user " + loggedInUser.getUsername() + ": " + e.getMessage());
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
        if (peerInfo == null) return;

        synchronized (fileRegistry) {
            FileEntry entry = fileRegistry.stream()
                    .filter(fe -> fe.fileName.equals(fileName))
                    .findFirst()
                    .orElseGet(() -> {
                        FileEntry newEntry = new FileEntry(fileName);
                        fileRegistry.add(newEntry);
                        return newEntry;
                    });

            entry.addPeer(peerInfo);
            System.out.println("Peer " + peerInfo.username + " now sharing '" + fileName + "'. Total peers for file: " + entry.peers.size());
        }
    }

    private void handleSearch(String searchTerm, PrintWriter out) {
        StringBuilder response = new StringBuilder();
        Map<String, Set<PeerInfo>> matchingFiles = new LinkedHashMap<>();
        String lowerSearchTerm = searchTerm.toLowerCase().trim();

        synchronized (fileRegistry) {
            for (FileEntry entry : fileRegistry) {
                if (entry.fileName.toLowerCase().contains(lowerSearchTerm) && !entry.peers.isEmpty()) {
                    matchingFiles.computeIfAbsent(entry.fileName, k -> new LinkedHashSet<>()).addAll(entry.peers);
                }
            }
        }

        boolean firstFile = true;
        for (Map.Entry<String, Set<PeerInfo>> entry : matchingFiles.entrySet()) {
            if (!firstFile) response.append(";");
            firstFile = false;
            response.append(entry.getKey()).append("=");
            boolean firstPeer = true;
            for (PeerInfo peer : entry.getValue()) {
                if (!firstPeer) response.append(",");
                firstPeer = false;
                response.append(peer.username).append(":").append(peer.address);
            }
        }
        out.println(response.toString());
        System.out.println("Search by '" + loggedInUser.getUsername() + "' for '" + searchTerm + "' found " + matchingFiles.size() + " files.");
    }

    private void handleChangePassword(String currentPassword, String newPassword, PrintWriter out) {
        try {
            if (!accountService.verifyPassword(currentPassword, loggedInUser.getPasswordHash())) {
                out.println("CHANGE_PASSWORD_FAIL Current password is incorrect");
                return;
            }
            if (accountService.changePassword(loggedInUser.getUsername(), newPassword)) {
                out.println("CHANGE_PASSWORD_SUCCESS");
                System.out.println("User '" + loggedInUser.getUsername() + "' changed their password.");
            } else {
                out.println("CHANGE_PASSWORD_FAIL Could not update password.");
            }
        } catch (IOException e) {
            out.println("CHANGE_PASSWORD_FAIL " + e.getMessage());
        }
    }

    private void handleDeleteAccount(String username, String password, PrintWriter out) {
        try {
            if (!loggedInUser.getUsername().equals(username)) {
                out.println("DELETE_ACCOUNT_FAIL Username mismatch");
                return;
            }
            if (!accountService.verifyPassword(password, loggedInUser.getPasswordHash())) {
                out.println("DELETE_ACCOUNT_FAIL Incorrect password");
                return;
            }
            if (loggedInUser.isAdmin() && accountService.isOnlyAdmin(username)) {
                out.println("DELETE_ACCOUNT_FAIL Cannot delete the only admin account");
                return;
            }
            if (accountService.removeUser(username)) {
                out.println("DELETE_ACCOUNT_SUCCESS");
                System.out.println("User '" + username + "' deleted their own account.");
                //Close the connection by returning
                return;
            } else {
                out.println("DELETE_ACCOUNT_FAIL Could not delete account");
            }
        } catch (IOException e) {
            out.println("DELETE_ACCOUNT_FAIL " + e.getMessage());
        }
    }

    private void handleListPeers(PrintWriter out) {
        StringBuilder response = new StringBuilder();
        synchronized (activePeers) {
            for (PeerInfo pi : activePeers) {
                if (response.length() > 0) response.append(",");
                response.append(pi.username).append("=").append(pi.address);
            }
        }
        out.println(response.toString());
    }
}