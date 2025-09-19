import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.Timer;

public class AdminPanel extends JPanel {
    private AdminUser adminUser;
    private Transport serverTransport;

    // User management components
    private JTable usersTable;
    private DefaultTableModel usersTableModel;
    private JButton removeUserButton, kickUserButton, viewUserStatsButton;
    private JTextField userSearchField;

    // System monitoring components
    private JTable serverLogsTable;
    private DefaultTableModel logsTableModel;
    private JButton clearLogsButton, exportLogsButton, refreshLogsButton;
    private JTextArea systemStatusArea;
    private JLabel serverUptimeLabel, totalUsersLabel, activeUsersLabel, totalFilesLabel;

    // Network monitoring
    private JTable networkActivityTable;
    private DefaultTableModel networkTableModel;
    private JProgressBar serverLoadBar;
    private JLabel networkTrafficLabel;

    // Admin controls
    private JButton shutdownServerButton, restartServerButton, broadcastMessageButton;
    private JButton backupDataButton, restoreDataButton;
    private JTextArea broadcastMessageArea;

    // Statistics
    private JPanel statsChartsPanel;
    private Timer refreshTimer;

    // Colors for status indication
    private final Color ONLINE_COLOR = new Color(76, 175, 80);
    private final Color OFFLINE_COLOR = new Color(244, 67, 54);
    private final Color WARNING_COLOR = new Color(255, 152, 0);
    private final Color INFO_COLOR = new Color(33, 150, 243);

    public AdminPanel(AdminUser adminUser, Transport serverTransport) {
        this.adminUser = adminUser;
        this.serverTransport = serverTransport;

        initializeComponents();
        setupLayout();
        setupEventListeners();
        startMonitoring();
        refreshData();
    }

    private void initializeComponents() {
        // User management table
        String[] userColumns = {"Username", "Status", "Last Seen", "Downloads", "Uploads", "IP Address"};
        usersTableModel = new DefaultTableModel(userColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1) return JLabel.class; // Status column for icons
                return String.class;
            }
        };

        usersTable = new JTable(usersTableModel);
        usersTable.setRowSorter(new TableRowSorter<>(usersTableModel));
        usersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Set column widths for users table
        usersTable.getColumnModel().getColumn(0).setPreferredWidth(120); // Username
        usersTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // Status
        usersTable.getColumnModel().getColumn(2).setPreferredWidth(120); // Last Seen
        usersTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Downloads
        usersTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Uploads
        usersTable.getColumnModel().getColumn(5).setPreferredWidth(100); // IP Address

        // User management controls
        userSearchField = new JTextField(15);
        removeUserButton = new JButton("Remove User");
        kickUserButton = new JButton("Kick User");
        viewUserStatsButton = new JButton("View Stats");

        // Server logs table
        String[] logColumns = {"Timestamp", "Level", "Source", "Message"};
        logsTableModel = new DefaultTableModel(logColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        serverLogsTable = new JTable(logsTableModel);
        serverLogsTable.setRowSorter(new TableRowSorter<>(logsTableModel));

        // Set column widths for logs table
        serverLogsTable.getColumnModel().getColumn(0).setPreferredWidth(120); // Timestamp
        serverLogsTable.getColumnModel().getColumn(1).setPreferredWidth(60);  // Level
        serverLogsTable.getColumnModel().getColumn(2).setPreferredWidth(100); // Source
        serverLogsTable.getColumnModel().getColumn(3).setPreferredWidth(300); // Message

        // Logs controls
        clearLogsButton = new JButton("Clear Logs");
        exportLogsButton = new JButton("Export");
        refreshLogsButton = new JButton("Refresh");

        // System status components
        systemStatusArea = new JTextArea(8, 30);
        systemStatusArea.setEditable(false);
        systemStatusArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        systemStatusArea.setBackground(new Color(245, 245, 245));

        serverUptimeLabel = new JLabel("Uptime: Calculating...");
        totalUsersLabel = new JLabel("Total Users: 0");
        activeUsersLabel = new JLabel("Active Users: 0");
        totalFilesLabel = new JLabel("Total Files: 0");

        // Network monitoring
        String[] networkColumns = {"Time", "Active Connections", "Data In (KB/s)", "Data Out (KB/s)"};
        networkTableModel = new DefaultTableModel(networkColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        networkActivityTable = new JTable(networkTableModel);
        serverLoadBar = new JProgressBar(0, 100);
        serverLoadBar.setStringPainted(true);
        serverLoadBar.setString("Server Load: 0%");
        networkTrafficLabel = new JLabel("Network Traffic: 0 KB/s");

        // Admin controls
        shutdownServerButton = new JButton("Shutdown Server");
        restartServerButton = new JButton("Restart Server");
        broadcastMessageButton = new JButton("Broadcast Message");
        backupDataButton = new JButton("Backup Data");
        restoreDataButton = new JButton("Restore Data");

        broadcastMessageArea = new JTextArea(3, 30);
        broadcastMessageArea.setBorder(BorderFactory.createTitledBorder("Broadcast Message"));
        broadcastMessageArea.setLineWrap(true);
        broadcastMessageArea.setWrapStyleWord(true);

        // Set button colors for critical operations
        shutdownServerButton.setBackground(new Color(244, 67, 54));
        shutdownServerButton.setForeground(Color.WHITE);
        restartServerButton.setBackground(WARNING_COLOR);
        restartServerButton.setForeground(Color.WHITE);

        // Set tooltips
        setupTooltips();
    }

    private void setupTooltips() {
        removeUserButton.setToolTipText("Permanently remove selected user account");
        kickUserButton.setToolTipText("Disconnect selected user from server");
        viewUserStatsButton.setToolTipText("View detailed statistics for selected user");
        shutdownServerButton.setToolTipText("Shutdown the server (requires confirmation)");
        restartServerButton.setToolTipText("Restart the server (requires confirmation)");
        broadcastMessageButton.setToolTipText("Send message to all connected users");
        backupDataButton.setToolTipText("Create backup of user data and settings");
        restoreDataButton.setToolTipText("Restore data from backup file");
    }

    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create main tabbed pane
        JTabbedPane mainTabs = new JTabbedPane();

        // User Management tab
        mainTabs.addTab("User Management", createUserManagementPanel());

        // System Monitor tab
        mainTabs.addTab("System Monitor", createSystemMonitorPanel());

        // Network Monitor tab
        mainTabs.addTab("Network Monitor", createNetworkMonitorPanel());

        // Server Control tab
        mainTabs.addTab("Server Control", createServerControlPanel());

        add(mainTabs, BorderLayout.CENTER);

        // Status bar at bottom
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel createUserManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Top panel with search and controls
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));

        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Search Users:"));
        searchPanel.add(userSearchField);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(viewUserStatsButton);
        buttonPanel.add(kickUserButton);
        buttonPanel.add(removeUserButton);

        topPanel.add(searchPanel, BorderLayout.WEST);
        topPanel.add(buttonPanel, BorderLayout.EAST);

        // Users table
        JScrollPane usersScrollPane = new JScrollPane(usersTable);
        usersScrollPane.setBorder(new TitledBorder("Registered Users"));

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(usersScrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createSystemMonitorPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Top panel with system stats
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        statsPanel.setBorder(new TitledBorder("System Statistics"));

        statsPanel.add(createStatCard("Server Uptime", serverUptimeLabel, INFO_COLOR));
        statsPanel.add(createStatCard("Total Users", totalUsersLabel, ONLINE_COLOR));
        statsPanel.add(createStatCard("Active Users", activeUsersLabel, WARNING_COLOR));
        statsPanel.add(createStatCard("Total Files", totalFilesLabel, INFO_COLOR));

        // Center panel with logs
        JPanel logsPanel = new JPanel(new BorderLayout(5, 5));
        logsPanel.setBorder(new TitledBorder("System Logs"));

        // Logs controls
        JPanel logsControlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        logsControlPanel.add(refreshLogsButton);
        logsControlPanel.add(exportLogsButton);
        logsControlPanel.add(clearLogsButton);

        JScrollPane logsScrollPane = new JScrollPane(serverLogsTable);

        logsPanel.add(logsControlPanel, BorderLayout.NORTH);
        logsPanel.add(logsScrollPane, BorderLayout.CENTER);

        // Bottom panel with system status
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(new TitledBorder("System Status"));
        statusPanel.add(new JScrollPane(systemStatusArea), BorderLayout.CENTER);

        panel.add(statsPanel, BorderLayout.NORTH);
        panel.add(logsPanel, BorderLayout.CENTER);
        panel.add(statusPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createNetworkMonitorPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Top panel with network stats
        JPanel networkStatsPanel = new JPanel(new BorderLayout(10, 5));
        networkStatsPanel.setBorder(new TitledBorder("Network Status"));

        JPanel metricsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        metricsPanel.add(networkTrafficLabel);
        metricsPanel.add(Box.createHorizontalStrut(20));
        metricsPanel.add(new JLabel("Server Load:"));
        metricsPanel.add(serverLoadBar);

        networkStatsPanel.add(metricsPanel, BorderLayout.NORTH);

        // Center panel with network activity table
        JScrollPane networkScrollPane = new JScrollPane(networkActivityTable);
        networkScrollPane.setBorder(new TitledBorder("Network Activity History"));

        panel.add(networkStatsPanel, BorderLayout.NORTH);
        panel.add(networkScrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createServerControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Server controls
        JPanel serverControlsPanel = new JPanel(new GridBagLayout());
        serverControlsPanel.setBorder(new TitledBorder("Server Controls"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0;
        serverControlsPanel.add(restartServerButton, gbc);
        gbc.gridx = 1;
        serverControlsPanel.add(shutdownServerButton, gbc);

        // Data management
        JPanel dataPanel = new JPanel(new FlowLayout());
        dataPanel.setBorder(new TitledBorder("Data Management"));
        dataPanel.add(backupDataButton);
        dataPanel.add(restoreDataButton);

        // Broadcast panel
        JPanel broadcastPanel = new JPanel(new BorderLayout(5, 5));
        broadcastPanel.setBorder(new TitledBorder("Broadcast to Users"));

        JPanel broadcastControlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        broadcastControlPanel.add(broadcastMessageButton);

        broadcastPanel.add(new JScrollPane(broadcastMessageArea), BorderLayout.CENTER);
        broadcastPanel.add(broadcastControlPanel, BorderLayout.SOUTH);

        panel.add(serverControlsPanel, BorderLayout.NORTH);
        panel.add(dataPanel, BorderLayout.CENTER);
        panel.add(broadcastPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createStatCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accentColor, 2),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        card.setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel(title, JLabel.CENTER);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        titleLabel.setForeground(accentColor);

        valueLabel.setHorizontalAlignment(JLabel.CENTER);
        valueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        valueLabel.setForeground(Color.BLACK);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusBar.setBorder(BorderFactory.createLoweredBevelBorder());

        JLabel adminLabel = new JLabel("Admin: " + adminUser.getUsername());
        JLabel timeLabel = new JLabel("Server Time: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        statusBar.add(adminLabel);
        statusBar.add(Box.createHorizontalStrut(20));
        statusBar.add(timeLabel);

        return statusBar;
    }

    private void setupEventListeners() {
        // User management events
        removeUserButton.addActionListener(e -> removeSelectedUser());
        kickUserButton.addActionListener(e -> kickSelectedUser());
        viewUserStatsButton.addActionListener(e -> viewUserStats());

        // User search functionality
        userSearchField.addActionListener(e -> filterUsers());

        // System monitoring events
        clearLogsButton.addActionListener(e -> clearLogs());
        exportLogsButton.addActionListener(e -> exportLogs());
        refreshLogsButton.addActionListener(e -> refreshLogs());

        // Server control events
        shutdownServerButton.addActionListener(e -> shutdownServer());
        restartServerButton.addActionListener(e -> restartServer());
        broadcastMessageButton.addActionListener(e -> broadcastMessage());
        backupDataButton.addActionListener(e -> backupData());
        restoreDataButton.addActionListener(e -> restoreData());

        // Table double-click events
        usersTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    viewUserStats();
                }
            }
        });

        // Context menus
        usersTable.setComponentPopupMenu(createUserContextMenu());
        serverLogsTable.setComponentPopupMenu(createLogsContextMenu());
    }

    private JPopupMenu createUserContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem viewStatsItem = new JMenuItem("View Statistics");
        viewStatsItem.addActionListener(e -> viewUserStats());

        JMenuItem kickUserItem = new JMenuItem("Kick User");
        kickUserItem.addActionListener(e -> kickSelectedUser());

        JMenuItem removeUserItem = new JMenuItem("Remove User");
        removeUserItem.addActionListener(e -> removeSelectedUser());

        menu.add(viewStatsItem);
        menu.addSeparator();
        menu.add(kickUserItem);
        menu.add(removeUserItem);

        return menu;
    }

    private JPopupMenu createLogsContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.addActionListener(e -> copySelectedLogEntry());

        JMenuItem clearItem = new JMenuItem("Clear All Logs");
        clearItem.addActionListener(e -> clearLogs());

        menu.add(copyItem);
        menu.addSeparator();
        menu.add(clearItem);

        return menu;
    }

    private void startMonitoring() {
        refreshTimer = new Timer(true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    updateSystemStats();
                    updateNetworkStats();
                    refreshUserList();
                });
            }
        }, 1000, 10000); // Update every 10 seconds
    }

    public void refreshData() {
        refreshUserList();
        refreshLogs();
        updateSystemStats();
        updateSystemStatus();
    }

    private void refreshUserList() {
        // TODO: Implement actual user list retrieval from server
        // This would typically involve sending a command to the server transport
        // For now, we'll simulate with sample data

        SwingUtilities.invokeLater(() -> {
            usersTableModel.setRowCount(0);

            // Simulate some users - in real implementation, get from server
            String[][] sampleUsers = {
                    {"alice", "Online", "2024-01-15 14:30:25", "25", "18", "192.168.1.101"},
                    {"bob", "Offline", "2024-01-15 12:15:10", "12", "8", "192.168.1.102"},
                    {"charlie", "Online", "2024-01-15 14:28:45", "35", "42", "192.168.1.103"}
            };

            for (String[] userData : sampleUsers) {
                usersTableModel.addRow(userData);
            }

            totalUsersLabel.setText("Total Users: " + sampleUsers.length);
            activeUsersLabel.setText("Active Users: 2"); // Count online users
        });
    }

    private void refreshLogs() {
        SwingUtilities.invokeLater(() -> {
            // Add some sample log entries - in real implementation, get from server logs
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentTime = dateFormat.format(new Date());

            // Keep only recent logs (last 100 entries)
            if (logsTableModel.getRowCount() > 100) {
                for (int i = 0; i < 50; i++) {
                    logsTableModel.removeRow(0);
                }
            }

            // Add new log entries periodically
            if (Math.random() < 0.3) { // 30% chance to add a log entry
                String[] logLevels = {"INFO", "WARN", "ERROR", "DEBUG"};
                String[] sources = {"Server", "Network", "FileSystem", "Authentication"};
                String[] messages = {
                        "User connected successfully",
                        "File transfer completed",
                        "Connection timeout occurred",
                        "Authentication attempt failed",
                        "System backup completed",
                        "Network congestion detected"
                };

                String level = logLevels[(int)(Math.random() * logLevels.length)];
                String source = sources[(int)(Math.random() * sources.length)];
                String message = messages[(int)(Math.random() * messages.length)];

                logsTableModel.addRow(new String[]{currentTime, level, source, message});
            }
        });
    }

    private void updateSystemStats() {
        SwingUtilities.invokeLater(() -> {
            // Update uptime (simulate)
            long uptimeMillis = System.currentTimeMillis() % (24 * 60 * 60 * 1000); // Mock 24h cycle
            long hours = uptimeMillis / (60 * 60 * 1000);
            long minutes = (uptimeMillis % (60 * 60 * 1000)) / (60 * 1000);
            serverUptimeLabel.setText(String.format("Uptime: %02d:%02d", hours, minutes));

            // Update file count (simulate)
            int fileCount = 150 + (int)(Math.random() * 50);
            totalFilesLabel.setText("Total Files: " + fileCount);
        });
    }

    private void updateNetworkStats() {
        SwingUtilities.invokeLater(() -> {
            // Simulate network activity
            int connections = 3 + (int)(Math.random() * 10);
            double dataIn = Math.random() * 100;
            double dataOut = Math.random() * 80;

            // Update network activity table
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            String currentTime = timeFormat.format(new Date());

            if (networkTableModel.getRowCount() > 20) {
                networkTableModel.removeRow(0);
            }

            networkTableModel.addRow(new String[]{
                    currentTime,
                    String.valueOf(connections),
                    String.format("%.1f", dataIn),
                    String.format("%.1f", dataOut)
            });

            // Update server load
            int serverLoad = 20 + (int)(Math.random() * 60);
            serverLoadBar.setValue(serverLoad);
            serverLoadBar.setString("Server Load: " + serverLoad + "%");

            // Update network traffic
            networkTrafficLabel.setText(String.format("Network Traffic: %.1f KB/s", dataIn + dataOut));
        });
    }

    private void updateSystemStatus() {
        SwingUtilities.invokeLater(() -> {
            StringBuilder status = new StringBuilder();
            status.append("=== SYSTEM STATUS ===\n");
            status.append("Server Status: RUNNING\n");
            status.append("Database Status: CONNECTED\n");
            status.append("File System: ACCESSIBLE\n");
            status.append("Network Interface: UP\n");
            status.append("Memory Usage: ").append(getMemoryUsage()).append("\n");
            status.append("CPU Usage: ").append(getCpuUsage()).append("\n");
            status.append("Disk Space: ").append(getDiskSpace()).append("\n");
            status.append("\n=== RECENT ACTIVITY ===\n");
            status.append("Last user login: ").append(getLastLoginTime()).append("\n");
            status.append("Files shared today: ").append((int)(Math.random() * 50)).append("\n");
            status.append("Data transferred: ").append(String.format("%.2f MB", Math.random() * 1000)).append("\n");

            systemStatusArea.setText(status.toString());
        });
    }

    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        return String.format("%.1f / %.1f MB",
                usedMemory / (1024.0 * 1024.0),
                totalMemory / (1024.0 * 1024.0));
    }

    private String getCpuUsage() {
        return String.format("%.1f%%", Math.random() * 30 + 10);
    }

    private String getDiskSpace() {
        java.io.File file = new java.io.File(".");
        long totalSpace = file.getTotalSpace();
        long freeSpace = file.getFreeSpace();

        return String.format("%.1f / %.1f GB free",
                freeSpace / (1024.0 * 1024.0 * 1024.0),
                totalSpace / (1024.0 * 1024.0 * 1024.0));
    }

    private String getLastLoginTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return dateFormat.format(new Date(System.currentTimeMillis() - (long)(Math.random() * 3600000)));
    }

    private void removeSelectedUser() {
        int selectedRow = usersTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a user to remove.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = usersTable.convertRowIndexToModel(selectedRow);
        String username = (String) usersTableModel.getValueAt(modelRow, 0);

        if ("admin".equalsIgnoreCase(username)) {
            JOptionPane.showMessageDialog(this, "Cannot remove admin user.",
                    "Invalid Operation", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to permanently remove user '" + username + "'?",
                "Confirm Removal",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            try {
                // Send remove user command to server
                serverTransport.sendLine("REMOVE_USER " + username);
                String response = serverTransport.readLine();

                if ("REMOVE_SUCCESS".equals(response)) {
                    JOptionPane.showMessageDialog(this, "User '" + username + "' removed successfully.",
                            "User Removed", JOptionPane.INFORMATION_MESSAGE);
                    refreshUserList();
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to remove user: " + response,
                            "Removal Failed", JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error communicating with server: " + e.getMessage(),
                        "Communication Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void kickSelectedUser() {
        int selectedRow = usersTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a user to kick.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = usersTable.convertRowIndexToModel(selectedRow);
        String username = (String) usersTableModel.getValueAt(modelRow, 0);
        String status = (String) usersTableModel.getValueAt(modelRow, 1);

        if ("Offline".equals(status)) {
            JOptionPane.showMessageDialog(this, "User is already offline.",
                    "User Offline", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Kick user '" + username + "' from the server?",
                "Confirm Kick",
                JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            // In a real implementation, this would send a kick command
            JOptionPane.showMessageDialog(this, "User '" + username + "' has been kicked.",
                    "User Kicked", JOptionPane.INFORMATION_MESSAGE);
            refreshUserList();
        }
    }

    private void viewUserStats() {
        int selectedRow = usersTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a user to view statistics.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = usersTable.convertRowIndexToModel(selectedRow);
        String username = (String) usersTableModel.getValueAt(modelRow, 0);
        String downloads = (String) usersTableModel.getValueAt(modelRow, 3);
        String uploads = (String) usersTableModel.getValueAt(modelRow, 4);

        // Create and show user statistics dialog
        JDialog statsDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "User Statistics - " + username, true);
        statsDialog.setLayout(new BorderLayout());

        JTextArea statsArea = new JTextArea(15, 40);
        statsArea.setEditable(false);
        statsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        StringBuilder stats = new StringBuilder();
        stats.append("=== USER STATISTICS ===\n\n");
        stats.append("Username: ").append(username).append("\n");
        stats.append("Downloads: ").append(downloads).append(" files\n");
        stats.append("Uploads: ").append(uploads).append(" files\n");
        stats.append("Total Activity: ").append(Integer.parseInt(downloads) + Integer.parseInt(uploads)).append(" operations\n\n");
        stats.append("=== SESSION HISTORY ===\n");
        stats.append("Last Login: 2024-01-15 14:30:25\n");
        stats.append("Session Duration: 2h 15m\n");
        stats.append("Data Transferred: 1.2 GB\n\n");
        stats.append("=== NETWORK INFO ===\n");
        stats.append("IP Address: ").append(usersTableModel.getValueAt(modelRow, 5)).append("\n");
        stats.append("Connection Type: TCP\n");
        stats.append("Average Speed: 1.5 MB/s\n");

        statsArea.setText(stats.toString());

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> statsDialog.dispose());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(closeButton);

        statsDialog.add(new JScrollPane(statsArea), BorderLayout.CENTER);
        statsDialog.add(buttonPanel, BorderLayout.SOUTH);
        statsDialog.setSize(500, 400);
        statsDialog.setLocationRelativeTo(this);
        statsDialog.setVisible(true);
    }

    private void filterUsers() {
        String searchText = userSearchField.getText().toLowerCase();
        TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) usersTable.getRowSorter();

        if (searchText.trim().isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText));
        }
    }

    private void clearLogs() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Clear all log entries?",
                "Clear Logs",
                JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            logsTableModel.setRowCount(0);
        }
    }

    private void exportLogs() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new java.io.File("server_logs_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.PrintWriter writer = new java.io.PrintWriter(fileChooser.getSelectedFile());

                for (int i = 0; i < logsTableModel.getRowCount(); i++) {
                    StringBuilder line = new StringBuilder();
                    for (int j = 0; j < logsTableModel.getColumnCount(); j++) {
                        if (j > 0) line.append("\t");
                        line.append(logsTableModel.getValueAt(i, j));
                    }
                    writer.println(line.toString());
                }

                writer.close();
                JOptionPane.showMessageDialog(this, "Logs exported successfully.",
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error exporting logs: " + e.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void copySelectedLogEntry() {
        int selectedRow = serverLogsTable.getSelectedRow();
        if (selectedRow >= 0) {
            int modelRow = serverLogsTable.convertRowIndexToModel(selectedRow);
            StringBuilder logEntry = new StringBuilder();

            for (int i = 0; i < logsTableModel.getColumnCount(); i++) {
                if (i > 0) logEntry.append(" | ");
                logEntry.append(logsTableModel.getValueAt(modelRow, i));
            }

            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(logEntry.toString()), null);
        }
    }

    private void shutdownServer() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to shutdown the server?\nThis will disconnect all users.",
                "Confirm Shutdown",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            // Additional confirmation for critical operation
            String confirm = JOptionPane.showInputDialog(this,
                    "Type 'SHUTDOWN' to confirm server shutdown:");

            if ("SHUTDOWN".equals(confirm)) {
                JOptionPane.showMessageDialog(this,
                        "Server shutdown command would be executed here.\n(Not implemented in demo)",
                        "Shutdown", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void restartServer() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to restart the server?\nThis will temporarily disconnect all users.",
                "Confirm Restart",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            JOptionPane.showMessageDialog(this,
                    "Server restart command would be executed here.\n(Not implemented in demo)",
                    "Restart", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void broadcastMessage() {
        String message = broadcastMessageArea.getText().trim();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a message to broadcast.",
                    "Empty Message", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Send the following message to all connected users?\n\n\"" + message + "\"",
                "Confirm Broadcast",
                JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            // In real implementation, send broadcast command to server
            JOptionPane.showMessageDialog(this, "Message broadcast to all users.",
                    "Message Sent", JOptionPane.INFORMATION_MESSAGE);
            broadcastMessageArea.setText("");
        }
    }

    private void backupData() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new java.io.File("server_backup_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".zip"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            // Simulate backup process
            JProgressBar progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);

            JDialog progressDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                    "Creating Backup", true);
            progressDialog.add(progressBar);
            progressDialog.setSize(300, 100);
            progressDialog.setLocationRelativeTo(this);

            SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
                @Override
                protected Void doInBackground() throws Exception {
                    for (int i = 0; i <= 100; i += 10) {
                        Thread.sleep(200);
                        publish(i);
                        if (isCancelled()) break;
                    }
                    return null;
                }

                @Override
                protected void process(List<Integer> chunks) {
                    for (Integer value : chunks) {
                        progressBar.setValue(value);
                        progressBar.setString("Backing up... " + value + "%");
                    }
                }

                @Override
                protected void done() {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(AdminPanel.this, "Backup created successfully.",
                            "Backup Complete", JOptionPane.INFORMATION_MESSAGE);
                }
            };

            worker.execute();
            progressDialog.setVisible(true);
        }
    }

    private void restoreData() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Backup files", "zip"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to restore from backup?\nThis will overwrite current data.",
                    "Confirm Restore",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (choice == JOptionPane.YES_OPTION) {
                JOptionPane.showMessageDialog(this, "Data restore would be executed here.\n(Not implemented in demo)",
                        "Restore", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    // Cleanup method
    public void cleanup() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
        }
    }

    // Public methods for external updates
    public void updateUserCount(int totalUsers, int activeUsers) {
        SwingUtilities.invokeLater(() -> {
            totalUsersLabel.setText("Total Users: " + totalUsers);
            activeUsersLabel.setText("Active Users: " + activeUsers);
        });
    }

    public void addLogEntry(String level, String source, String message) {
        SwingUtilities.invokeLater(() -> {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = dateFormat.format(new Date());
            logsTableModel.addRow(new String[]{timestamp, level, source, message});

            // Keep log table manageable
            if (logsTableModel.getRowCount() > 500) {
                logsTableModel.removeRow(0);
            }
        });
    }
}
