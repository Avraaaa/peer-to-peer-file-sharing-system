import javax.swing.SwingUtilities;
import java.io.IOException;

/**
 * Adapter class to integrate the existing PeerClient with the new UI system
 * This class bridges the console-based PeerClient with the GUI components
 */
public class PeerClientUIAdaptor {

    private PeerClient peerClient;
    private User currentUser;
    private Transport serverTransport;
    private String sharedDirectory;

    public PeerClientUIAdaptor(String serverHost, int serverPort, int listenPort,
                               String sharedDirectory, User user, Transport transport) {
        this.currentUser = user;
        this.serverTransport = transport;
        this.sharedDirectory = sharedDirectory;

        try {
            // Initialize file handler and download strategy
            LocalFileHandler fileHandler = new LocalFileHandler(sharedDirectory);
            ChunkedDownload downloadStrategy = new ChunkedDownload(8192, fileHandler,
                    java.nio.file.Paths.get(sharedDirectory));

            // Create peer client instance
            this.peerClient = new PeerClient(serverHost, serverPort, listenPort,
                    fileHandler, downloadStrategy);

            // Set session context
            this.peerClient.setSessionContext(user, transport);

        } catch (IOException e) {
            UIIntegrationHelper.showError("Failed to initialize peer client: " + e.getMessage());
        }
    }

    /**
     * Start the peer client with UI integration
     */
    public void startWithUI() {
        try {
            // Start peer client in background thread
            Thread peerClientThread = new Thread(() -> {
                try {
                    // Override the console input handling
                    peerClient.startWithoutConsoleInput(sharedDirectory);
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> {
                        UIIntegrationHelper.showError("Peer client error: " + e.getMessage());
                    });
                }
            });

            peerClientThread.setDaemon(true);
            peerClientThread.start();

            // Initialize UI integration
            UIIntegrationHelper.initializeIntegration(currentUser, serverTransport,
                    sharedDirectory, peerClient);

            // Show main application window
            UIIntegrationHelper.showMainApplication();

        } catch (Exception e) {
            UIIntegrationHelper.showError("Failed to start application: " + e.getMessage());
        }
    }

    /**
     * Perform search operation from UI
     */
    public void searchFiles(String fileName, SearchResultCallback callback) {
        Thread searchThread = new Thread(() -> {
            try {
                serverTransport.sendLine("SEARCH " + fileName);
                String response = serverTransport.readLine();

                SwingUtilities.invokeLater(() -> {
                    callback.onSearchComplete(response);
                });

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    callback.onSearchError("Search failed: " + e.getMessage());
                });
            }
        });

        searchThread.start();
    }

    /**
     * Download file from peer
     */
    public void downloadFile(String peerAddress, String fileName,
                             DownloadProgressCallback callback) {
        Thread downloadThread = new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() -> {
                    callback.onDownloadStarted(fileName);
                });

                // Use the existing download strategy
                peerClient.getDownloadStrategy().download(peerAddress, fileName);

                // Update statistics
                UIIntegrationHelper.recordDownload(1024); // Placeholder size

                SwingUtilities.invokeLater(() -> {
                    callback.onDownloadComplete(fileName);
                    UIIntegrationHelper.updateFileList();
                });

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    callback.onDownloadError(fileName, e.getMessage());
                });
            }
        });

        downloadThread.start();
    }

    /**
     * Get list of online peers
     */
    public void listPeers(PeerListCallback callback) {
        Thread listThread = new Thread(() -> {
            try {
                serverTransport.sendLine("LIST_PEERS");
                String response = serverTransport.readLine();

                SwingUtilities.invokeLater(() -> {
                    callback.onPeerListReceived(response);
                });

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    callback.onPeerListError("Failed to get peer list: " + e.getMessage());
                });
            }
        });

        listThread.start();
    }

    /**
     * Share a file with the network
     */
    public void shareFile(String fileName, OperationCallback callback) {
        Thread shareThread = new Thread(() -> {
            try {
                serverTransport.sendLine("SHARE " + fileName);

                SwingUtilities.invokeLater(() -> {
                    callback.onOperationComplete("File '" + fileName + "' shared successfully");
                    UIIntegrationHelper.updateFileList();
                });

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    callback.onOperationError("Failed to share file: " + e.getMessage());
                });
            }
        });

        shareThread.start();
    }

    /**
     * Update user statistics on server
     */
    public void updateStats() {
        Thread statsThread = new Thread(() -> {
            try {
                String downloadStats = currentUser.getDownloadStats().toCsvString();
                String uploadStats = currentUser.getUploadStats().toCsvString();

                serverTransport.sendLine("UPDATE_STATS " + downloadStats + " " + uploadStats);

                SwingUtilities.invokeLater(() -> {
                    UIIntegrationHelper.showInfo("Statistics updated successfully");
                });

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    UIIntegrationHelper.showError("Failed to update statistics: " + e.getMessage());
                });
            }
        });

        statsThread.start();
    }

    /**
     * Admin operation: Remove user
     */
    public void removeUser(String username, OperationCallback callback) {
        if (!currentUser.isAdmin()) {
            callback.onOperationError("Insufficient privileges");
            return;
        }

        Thread removeThread = new Thread(() -> {
            try {
                serverTransport.sendLine("REMOVE_USER " + username);
                String response = serverTransport.readLine();

                SwingUtilities.invokeLater(() -> {
                    if ("REMOVE_SUCCESS".equals(response)) {
                        callback.onOperationComplete("User '" + username + "' removed successfully");
                    } else {
                        callback.onOperationError("Failed to remove user: " + response);
                    }
                });

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    callback.onOperationError("Communication error: " + e.getMessage());
                });
            }
        });

        removeThread.start();
    }

    /**
     * Get the underlying peer client instance
     */
    public PeerClient getPeerClient() {
        return peerClient;
    }

    /**
     * Clean shutdown
     */
    public void shutdown() {
        try {
            if (serverTransport != null) {
                serverTransport.sendLine("UNREGISTER");
                serverTransport.close();
            }
        } catch (IOException e) {
            System.err.println("Error during shutdown: " + e.getMessage());
        }
    }

    // Callback interfaces for async operations

    public interface SearchResultCallback {
        void onSearchComplete(String results);
        void onSearchError(String error);
    }

    public interface DownloadProgressCallback {
        void onDownloadStarted(String fileName);
        void onDownloadProgress(String fileName, int progress);
        void onDownloadComplete(String fileName);
        void onDownloadError(String fileName, String error);
    }

    public interface PeerListCallback {
        void onPeerListReceived(String peerList);
        void onPeerListError(String error);
    }

    public interface OperationCallback {
        void onOperationComplete(String message);
        void onOperationError(String error);
    }
}

/**
 * Modified PeerClient helper methods for UI integration
 */
class PeerClientHelper {

    /**
     * Create a UI-friendly version of the PeerClient main method
     */
    public static PeerClientUIAdaptor createUIEnabledPeerClient(String[] args) {
        String serverHost = "localhost";
        int serverPort = 9090;

        try {
            // Establish server connection
            Transport serverTransport = new TCPTransport(serverHost, serverPort);

            // Show welcome dialog for authentication
            WelcomeFrame welcomeFrame = new WelcomeFrame();
            welcomeFrame.setVisible(true);

            return null; // This would return the adapter after successful authentication

        } catch (IOException e) {
            UIIntegrationHelper.showError("Could not connect to server: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse peer information response into a map
     */
    public static java.util.Map<String, String> parsePeerResponse(String response) {
        java.util.Map<String, String> peers = new java.util.LinkedHashMap<>();

        if (response == null || response.isEmpty()) {
            return peers;
        }

        String[] pairs = response.split(",");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                peers.put(parts[0], parts[1]);
            }
        }

        return peers;
    }

    /**
     * Format peer information for display
     */
    public static String formatPeerInfo(String username, String address) {
        return String.format("%-20s %s", username, address);
    }
}

/**
 * Integration point for the existing PeerClient to work with UI
 */

