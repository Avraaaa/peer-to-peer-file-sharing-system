import java.io.*;
import java.net.*;
import java.util.*;

public class Server {

    private static final int PORT = 9090;
    private static final List<FileEntry> fileRegistry = new ArrayList<>();
    private static final List<PeerInfo> activePeers = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        System.out.println("Simple Napster Server running on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                new Thread(new ClientHandler(serverSocket.accept())).start();
            }
        }
    }

    private static class PeerInfo {
        String address;

        PeerInfo(String address) {
            this.address = address;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PeerInfo)) return false;
            return Objects.equals(this.address, ((PeerInfo) obj).address);
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

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            System.out.println("Connected: " + socket.getRemoteSocketAddress());
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                String command;
                while ((command = in.readLine()) != null) {
                    String[] parts = command.split(" ", 2);
                    String action = parts[0].toUpperCase();

                    switch (action) {
                        case "REGISTER":
                            if (parts.length < 2) continue;
                            handleRegisterPeer(parts[1]);
                            break;
                        case "SHARE":
                            if (parts.length < 2 || peerInfo == null) continue;
                            handleShare(parts[1]);
                            break;
                        case "SEARCH":
                            if (parts.length < 2) continue;
                            handleSearch(parts[1], out);
                            break;
                        case "LIST_PEERS":
                            handleListPeers(out);
                            break;
                        case "UNREGISTER":
                            return;
                        default:
                            out.println("ERROR Unknown command");
                    }
                }
            } catch (IOException e) {
                System.err.println("Connection error: " + e.getMessage());
            } finally {
                unregisterPeer();
                try {
                    socket.close();
                } catch (IOException e) {}
                System.out.println("Disconnected: " + socket.getRemoteSocketAddress());
            }
        }

        private void handleRegisterPeer(String portStr) {
            String peerAddr = socket.getInetAddress().getHostAddress() + ":" + portStr;
            peerInfo = new PeerInfo(peerAddr);
            synchronized (activePeers) {
                if (!activePeers.contains(peerInfo)) {
                    activePeers.add(peerInfo);
                    System.out.println("Peer registered: " + peerAddr);
                }
            }
        }

        private void handleShare(String fileName) {
            synchronized (fileRegistry) {
                FileEntry entry = fileRegistry.stream()
                        .filter(e -> e.fileName.equals(fileName))
                        .findFirst()
                        .orElseGet(() -> {
                            FileEntry newEntry = new FileEntry(fileName);
                            fileRegistry.add(newEntry);
                            return newEntry;
                        });
                entry.addPeer(peerInfo);
            }
        }

        private void handleSearch(String fileName, PrintWriter out) {
            StringBuilder result = new StringBuilder();
            synchronized (fileRegistry) {
                fileRegistry.stream()
                        .filter(e -> e.fileName.equalsIgnoreCase(fileName))
                        .findFirst()
                        .ifPresent(entry -> {
                            for (PeerInfo p : entry.peers) {
                                if (result.length() > 0) result.append(",");
                                result.append(p.address);
                            }
                        });
            }
            out.println(result.toString());
        }

        private void handleListPeers(PrintWriter out) {
            StringJoiner joiner = new StringJoiner(",");
            synchronized (activePeers) {
                activePeers.forEach(p -> joiner.add(p.address));
            }
            out.println(joiner.toString());
        }

        private void unregisterPeer() {
            if (peerInfo != null) {
                synchronized (activePeers) {
                    activePeers.remove(peerInfo);
                }
                synchronized (fileRegistry) {
                    fileRegistry.forEach(entry -> entry.removePeer(peerInfo));
                }
            }
        }
    }
}
