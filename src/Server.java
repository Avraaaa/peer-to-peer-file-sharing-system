import java.io.*;
import java.net.*;
import java.util.*;

public class Server {

    private static final int PORT = 9090;
    private static final List<FileEntry> fileRegistry = new ArrayList<>();
    private static final List<PeerInfo> activePeers = new ArrayList<>();
    private static final AccountService accountService = new AccountService("users.csv");

    public static void main(String[] args) throws IOException {
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

        @Override
        public void run() {
            String clientIdentifier = socket.getRemoteSocketAddress().toString();
            System.out.println("Connected: " + clientIdentifier);

            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                String command;
                while ((command = in.readLine()) != null) {
                    if (loggedInUser != null) {
                        clientIdentifier = loggedInUser.getUsername();
                    }

                    String[] parts = command.split(" ", 2);
                    String action = parts[0].toUpperCase();

                    switch (action) {
                        case "LOGIN":
                            String[] loginParts = command.split(" ", 3);
                            if (loginParts.length < 3) continue;
                            handleLogin(loginParts[1], loginParts[2], out);
                            break;

                        case "SIGNUP":
                            String[] signupParts = command.split(" ", 4);
                            if (signupParts.length < 4) continue;
                            handleSignUp(signupParts[1], signupParts[2], signupParts[3], out);
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
                                out.println("ERROR Not logged in or peer not registered");
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
                        case "REMOVE_USER":
                            if (loggedInUser == null) {
                                out.println("ERROR Not logged in");
                                continue;
                            }
                            if (!loggedInUser.isAdmin()) {
                                out.println("ERROR Not authorized");
                                continue;
                            }
                            if (parts.length < 2) continue;
                            handleRemoveUser(parts[1], out);
                            break;
                        case "UNREGISTER":
                            return;
                        default:
                            out.println("ERROR Unknown command");
                    }
                }
            } catch (IOException e) {
                System.err.println("Connection error with " + clientIdentifier + ": " + e.getMessage());
            } finally {
                unregisterPeer();
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
                System.out.println("Disconnected: " + clientIdentifier);
            }
        }

        private void handleLogin(String username, String password, PrintWriter out) {
            User user = accountService.login(username, password);
            if (user != null) {
                this.loggedInUser = user;
                String payload = String.join(";",
                        user.getUsername(),
                        String.valueOf(user.isAdmin()),
                        user.getSharedDirectory()
                );
                out.println("LOGIN_SUCCESS " + payload);
                System.out.println("User '" + username + "' logged in successfully.");
            } else {
                out.println("LOGIN_FAIL Invalid username or password.");
                System.out.println("Failed login attempt for user '" + username + "'.");
            }
        }

        private void handleSignUp(String username, String password, String sharedDirectory, PrintWriter out) {
            try {
                User newUser = accountService.createUser(username, password, sharedDirectory);
                if (newUser != null) {
                    out.println("SIGNUP_SUCCESS User created. Please login.");
                    System.out.println("New user signed up: " + username + " with shared dir: " + sharedDirectory);
                }
            } catch (IOException e) {
                out.println("SIGNUP_FAIL " + e.getMessage());
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


        private void handleSearch(String fileName, PrintWriter out) {
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
            out.println(sb);
        }

        private void handleListPeers(PrintWriter out) {
            StringBuilder sb = new StringBuilder();
            synchronized (activePeers) {
                for (int i = 0; i < activePeers.size(); i++) {
                    sb.append(activePeers.get(i).address);
                    if (i < activePeers.size() - 1) {
                        sb.append(",");
                    }
                }
            }
            out.println(sb);
        }

        private void handleRemoveUser(String username, PrintWriter out) {
            try {
                if (accountService.removeUser(username)) {
                    out.println("REMOVE_SUCCESS " + username + " removed.");
                    System.out.println("Admin '" + loggedInUser.getUsername() + "' removed user '" + username + "'");
                } else {
                    out.println("REMOVE_FAIL User not found or cannot be removed.");
                }
            } catch (IOException e) {
                out.println("REMOVE_FAIL Error removing user: " + e.getMessage());
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
        }
    }
}