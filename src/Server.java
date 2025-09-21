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
            if (peerInfo == null) return;

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
                }

                entry.addPeer(peerInfo);
            }
        }


        private void handleSearch(String searchFile, PrintWriter out) {
            StringBuilder response = new StringBuilder();

            synchronized (fileRegistry) {
                FileEntry foundEntry = null;

                for (FileEntry entry : fileRegistry) {
                    if (entry.fileName.equalsIgnoreCase(searchFile)) {
                        foundEntry = entry;
                        break;
                    }
                }

                if (foundEntry != null) {
                    for (PeerInfo pi : foundEntry.peers) {
                        if (response.length() > 0) {
                            response.append(",");
                        }
                        response.append(pi.username).append("=").append(pi.address);
                    }
                }
            }
            out.println(response);
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