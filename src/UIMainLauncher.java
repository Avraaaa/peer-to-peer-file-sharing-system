import javax.swing.*;
import java.awt.*;

/**
 * Main entry point for the UI-enabled Peer to Peer file sharing system
 */
public class UIMainLauncher {
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
               // UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel());
                UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
            } catch (Exception e) {
                System.err.println("Could not set look and feel: " + e.getMessage());
            }

            WelcomeFrame welcomeFrame = new WelcomeFrame();
            welcomeFrame.setVisible(true);
        });
    }

    private static void createAndShowWelcomeFrame() {
        try {
            WelcomeFrame welcomeFrame = new WelcomeFrame();
            welcomeFrame.setVisible(true);

        } catch (Exception e) {
            showErrorAndExit("Failed to initialize application", e);
        }
    }

    private static void showErrorAndExit(String message, Exception e) {
        String fullMessage = message + "\n\nError: " + e.getMessage();

        JOptionPane.showMessageDialog(
                null,
                fullMessage,
                "Application Error",
                JOptionPane.ERROR_MESSAGE
        );

        e.printStackTrace();
        System.exit(1);
    }

    /**
     * Create main application after successful authentication
     */
    public static void launchMainApplication(User user, Transport serverTransport,
                                             String sharedDirectory) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Find available ports for peer client
                int[] ports = findAvailablePortPair();
                if (ports == null) {
                    throw new RuntimeException("Could not find available ports");
                }

                // Initialize peer client components
                FileHandler fileHandler = new LocalFileHandler(sharedDirectory);
                DownloadStrategy downloadStrategy = new ChunkedDownload(8192, fileHandler,
                        java.nio.file.Paths.get(sharedDirectory));

                PeerClient peerClient = new PeerClient("localhost", 9090, ports[0],
                        fileHandler, downloadStrategy);
                peerClient.setSessionContext(user, serverTransport);

                // Start peer client in background
                startPeerClientBackground(peerClient, sharedDirectory);

                // Create and show main application
                MainApplicationFrame mainFrame = new MainApplicationFrame(user, serverTransport, peerClient);
                mainFrame.setVisible(true);

                // Setup shutdown hook
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        serverTransport.sendLine("UNREGISTER");
                        serverTransport.close();
                    } catch (Exception ex) {
                        System.err.println("Error during shutdown: " + ex.getMessage());
                    }
                }));

            } catch (Exception e) {
                showErrorAndExit("Failed to launch main application", e);
            }
        });
    }

    private static void startPeerClientBackground(PeerClient peerClient, String sharedDirectory) {
        Thread peerThread = new Thread(() -> {
            try {
                peerClient.startWithoutConsoleInput(sharedDirectory);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null,
                            "Peer client error: " + e.getMessage(),
                            "Network Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        });

        peerThread.setDaemon(true);
        peerThread.start();
    }

    private static int[] findAvailablePortPair() {
        for (int port = 10000; port <= 11000; port++) {
            if (arePortsAvailable(port, port + 1)) {
                return new int[]{port, port + 1};
            }
        }
        return null;
    }

    private static boolean arePortsAvailable(int tcpPort, int udpPort) {
        try (java.net.ServerSocket tcpSocket = new java.net.ServerSocket(tcpPort);
             java.net.DatagramSocket udpSocket = new java.net.DatagramSocket(udpPort)) {
            tcpSocket.setReuseAddress(true);
            udpSocket.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
