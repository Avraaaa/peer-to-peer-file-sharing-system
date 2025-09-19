import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import javax.swing.border.TitledBorder;

/**
 * Helper class to integrate UI components with the existing backend system
 */
public class UIIntegrationHelper {
    private static User currentUser;
    private static Transport serverTransport;
    private static LocalFileHandler fileHandler;
    private static String sharedDirectory;
    private static PeerClient peerClient;

    // UI Component references
    private static MainApplicationFrame mainFrame;
    private static FileManagerPanel fileManagerPanel;
    private static StatisticsPanel statisticsPanel;
    private static SettingsPanel settingsPanel;
    private static AdminPanel adminPanel;

    /**
     * Initialize the UI integration with backend components
     */
    public static void initializeIntegration(User user, Transport transport,
                                             String sharedDir, PeerClient client) {
        currentUser = user;
        serverTransport = transport;
        sharedDirectory = sharedDir;
        peerClient = client;

        try {
            fileHandler = new LocalFileHandler(sharedDirectory);
        } catch (IOException e) {
            showError("Failed to initialize file handler: " + e.getMessage());
        }

        setupShutdownHook();
    }

    /**
     * Create and show the main application window
     */
    public static void showMainApplication() {
        SwingUtilities.invokeLater(() -> {
            try {
                // Set system look and feel
                //UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel());
                UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");

                // Create main frame
                mainFrame = new MainApplicationFrame(currentUser, serverTransport,peerClient);
                mainFrame.setUser(currentUser);

                // Initialize panels
                initializePanels();

                // Add panels to main frame
                addPanelsToMainFrame();

                // Setup window close behavior
                setupMainFrameCloseBehavior();

                // Show the application
                mainFrame.setVisible(true);

            } catch (Exception e) {
                showError("Failed to initialize UI: " + e.getMessage());
                System.exit(1);
            }
        });
    }

    /**
     * Initialize all UI panels
     */
    private static void initializePanels() {
        fileManagerPanel = new FileManagerPanel(sharedDirectory);
        statisticsPanel = new StatisticsPanel(currentUser);
        settingsPanel = new SettingsPanel(currentUser);

        if (currentUser.isAdmin()) {
            adminPanel = new AdminPanel((AdminUser) currentUser, serverTransport);
        }
    }

    /**
     * Add panels to the main application frame
     */
    private static void addPanelsToMainFrame() {
        // Add panels to main frame's content system
        mainFrame.addPanel("Files", fileManagerPanel);
        mainFrame.addPanel("Statistics", statisticsPanel);
        mainFrame.addPanel("Settings", settingsPanel);

        if (adminPanel != null) {
            mainFrame.addPanel("Admin", adminPanel);
        }

        // Set default panel
        mainFrame.showPanel("Files");
    }

    /**
     * Setup main frame close behavior
     */
    private static void setupMainFrameCloseBehavior() {
        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleApplicationClose();
            }
        });
    }

    /**
     * Handle application close
     */

    public static PeerClient getPeerClient() {
        return peerClient;
    }
    public static void updateSharedDirectory(String newDirectory) {
        sharedDirectory = newDirectory;

        try {
            fileHandler = new LocalFileHandler(newDirectory);

            // Update file manager panel
            if (fileManagerPanel != null) {
                fileManagerPanel.updateSharedDirectory(newDirectory);
            }

            // Restart directory watcher if peer client is available
            if (peerClient != null) {
            }

        } catch (IOException e) {
            showError("Failed to update shared directory: " + e.getMessage());
        }
    }
    public static void refreshPeerList() {
        SwingUtilities.invokeLater(() -> {
            // Refresh peer browser panel
            if (mainFrame != null) {
                JPanel peerPanel = mainFrame.panelMap.get("Browse Peers");
                if (peerPanel instanceof PeerBrowserPanel) {
                    ((PeerBrowserPanel) peerPanel).refreshPeerList();
                }
            }
        });
    }

    private static void handleApplicationClose() {
        int choice = JOptionPane.showConfirmDialog(
                mainFrame,
                "Are you sure you want to exit?",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION
        );

        if (choice == JOptionPane.YES_OPTION) {
            cleanup();
            System.exit(0);
        }
    }

    /**
     * Setup shutdown hook for cleanup
     */
    private static void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cleanup();
        }));
    }

    /**
     * Cleanup resources
     */
    private static void cleanup() {
        try {
            if (statisticsPanel != null) {
                statisticsPanel.cleanup();
            }

            if (adminPanel != null) {
                adminPanel.cleanup();
            }

            if (serverTransport != null) {
                serverTransport.sendLine("UNREGISTER");
                serverTransport.close();
            }
        } catch (Exception e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Update UI components when user data changes
     */
    public static void updateUserData(User updatedUser) {
        currentUser = updatedUser;

        SwingUtilities.invokeLater(() -> {
            if (statisticsPanel != null) {
                statisticsPanel.updateUser(updatedUser);
            }

            if (settingsPanel != null) {
                settingsPanel.updateUser(updatedUser);
            }

            if (mainFrame != null) {
                mainFrame.setUser(updatedUser);
            }
        });
    }

    /**
     * Update file list when files change
     */
    public static void updateFileList() {
        SwingUtilities.invokeLater(() -> {
            if (fileManagerPanel != null) {
                fileManagerPanel.updateFileList();
            }
        });
    }

    /**
     * Record download activity for statistics
     */
    public static void recordDownload(long bytes) {
        SwingUtilities.invokeLater(() -> {
            if (statisticsPanel != null) {
                statisticsPanel.recordDownload(bytes);
            }
        });
    }

    /**
     * Record upload activity for statistics
     */
    public static void recordUpload(long bytes) {
        SwingUtilities.invokeLater(() -> {
            if (statisticsPanel != null) {
                statisticsPanel.recordUpload(bytes);
            }
        });
    }

    /**
     * Update peer count
     */
    public static void updatePeerCount(int count) {
        SwingUtilities.invokeLater(() -> {
            if (statisticsPanel != null) {
                statisticsPanel.updatePeerCount(count);
            }

            if (adminPanel != null && currentUser.isAdmin()) {
                // Extract active users count (would need actual implementation)
                adminPanel.updateUserCount(count, count);
            }
        });
    }

    /**
     * Add log entry for admin panel
     */
    public static void addLogEntry(String level, String source, String message) {
        SwingUtilities.invokeLater(() -> {
            if (adminPanel != null && currentUser.isAdmin()) {
                adminPanel.addLogEntry(level, source, message);
            }
        });
    }

    /**
     * Show error message dialog
     */
    public static void showError(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                    mainFrame,
                    message,
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        });
    }

    /**
     * Show information message dialog
     */
    public static void showInfo(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                    mainFrame,
                    message,
                    "Information",
                    JOptionPane.INFORMATION_MESSAGE
            );
        });
    }

    /**
     * Show success message dialog
     */
    public static void showSuccess(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                    mainFrame,
                    message,
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE
            );
        });
    }

    /**
     * Get current application settings
     */
    public static SettingsPanel getSettingsPanel() {
        return settingsPanel;
    }

    /**
     * Get current file manager panel
     */
    public static FileManagerPanel getFileManagerPanel() {
        return fileManagerPanel;
    }

    /**
     * Get current statistics panel
     */
    public static StatisticsPanel getStatisticsPanel() {
        return statisticsPanel;
    }

    /**
     * Get current admin panel (if available)
     */
    public static AdminPanel getAdminPanel() {
        return adminPanel;
    }

    /**
     * Apply theme settings to the application
     */
    public static void applyTheme(String themeName) {
        SwingUtilities.invokeLater(() -> {
            try {
                switch (themeName.toLowerCase()) {
                    case "dark":
                        applyDarkTheme();
                        break;
                    case "light":
                        applyLightTheme();
                        break;
                    default:
                        //UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel());
                        UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
                        break;
                }

                // Update all components
                if (mainFrame != null) {
                    SwingUtilities.updateComponentTreeUI(mainFrame);
                }

            } catch (Exception e) {
                System.err.println("Failed to apply theme: " + e.getMessage());
            }
        });
    }

    private static void applyDarkTheme() {
        // Simple dark theme implementation
        UIManager.put("Panel.background", new Color(45, 45, 45));
        UIManager.put("Button.background", new Color(60, 60, 60));
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("Label.foreground", Color.WHITE);
        UIManager.put("TextField.background", new Color(60, 60, 60));
        UIManager.put("TextField.foreground", Color.WHITE);
        UIManager.put("Table.background", new Color(50, 50, 50));
        UIManager.put("Table.foreground", Color.WHITE);
        UIManager.put("TableHeader.background", new Color(40, 40, 40));
        UIManager.put("TableHeader.foreground", Color.WHITE);
    }

    private static void applyLightTheme() {
        // Simple light theme implementation
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("Button.background", new Color(240, 240, 240));
        UIManager.put("Button.foreground", Color.BLACK);
        UIManager.put("Label.foreground", Color.BLACK);
        UIManager.put("TextField.background", Color.WHITE);
        UIManager.put("TextField.foreground", Color.BLACK);
        UIManager.put("Table.background", Color.WHITE);
        UIManager.put("Table.foreground", Color.BLACK);
        UIManager.put("TableHeader.background", new Color(230, 230, 230));
        UIManager.put("TableHeader.foreground", Color.BLACK);
    }
}

/**
 * Abstract base class for main application frame
 */
abstract class BaseMainApplicationFrame extends JFrame {
    protected User currentUser;
    protected java.util.Map<String, JPanel> panels = new java.util.HashMap<>();
    protected JPanel contentPanel;

    public void setUser(User user) {
        this.currentUser = user;
        updateUserDisplay();
    }

    public void addPanel(String name, JPanel panel) {
        panels.put(name, panel);
        addPanelToUI(name, panel);
    }

    public void showPanel(String name) {
        JPanel panel = panels.get(name);
        if (panel != null) {
            displayPanel(panel);
        }
    }

    //implemented in mainframe
    protected abstract void updateUserDisplay();
    protected abstract void addPanelToUI(String name, JPanel panel);
    protected abstract void displayPanel(JPanel panel);
}

/**
 * Utility class for consistent UI styling
 */
class UIStyleHelper {

    // Color constants
    public static final Color PRIMARY_COLOR = new Color(64, 128, 255);
    public static final Color SUCCESS_COLOR = new Color(76, 175, 80);
    public static final Color WARNING_COLOR = new Color(255, 152, 0);
    public static final Color DANGER_COLOR = new Color(244, 67, 54);
    public static final Color INFO_COLOR = new Color(33, 150, 243);

    // Font constants
    public static final Font HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    public static final Font SUBHEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    public static final Font BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static final Font MONOSPACE_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    /**
     * Create a styled button with primary color
     */
    public static JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(PRIMARY_COLOR);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return button;
    }

    /**
     * Create a styled button with success color
     */
    public static JButton createSuccessButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(SUCCESS_COLOR);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return button;
    }

    /**
     * Create a styled button with warning color
     */
    public static JButton createWarningButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(WARNING_COLOR);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return button;
    }

    /**
     * Create a styled button with danger color
     */
    public static JButton createDangerButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(DANGER_COLOR);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return button;
    }

    /**
     * Create a titled border with primary color
     */
    public static TitledBorder createTitledBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleColor(PRIMARY_COLOR);
        border.setTitleFont(SUBHEADER_FONT);
        return border;
    }

    /**
     * Create a status label with appropriate color based on status
     */
    public static JLabel createStatusLabel(String text, boolean isOnline) {
        JLabel label = new JLabel(text);
        label.setForeground(isOnline ? SUCCESS_COLOR : DANGER_COLOR);
        label.setFont(BODY_FONT);
        return label;
    }

    /**
     * Format file size for display
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";

        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = (int) (Math.log10(bytes) / Math.log10(1024));
        double size = bytes / Math.pow(1024, unitIndex);

        return String.format("%.1f %s", size, units[unitIndex]);
    }

    /**
     * Format duration in seconds to human readable format
     */
    public static String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%d:%02d", minutes, secs);
        }
    }

    /**
     * Create a loading spinner
     */
    public static JProgressBar createLoadingSpinner() {
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Loading...");
        progressBar.setStringPainted(true);
        return progressBar;
    }
}