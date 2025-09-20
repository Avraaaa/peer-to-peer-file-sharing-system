import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DashboardPanel extends JPanel {
    private User currentUser;
    private PeerClient peerClient;

    // Dashboard components
    private JPanel statsCardsPanel;
    private JPanel quickActionsPanel;
    private JPanel networkStatusPanel;

    // Stats cards
    private StatCard filesSharedCard;
    private StatCard dataTransferredCard;


    // Activity list
    private DefaultListModel<String> activityListModel;
    private JList<String> activityList;

    public DashboardPanel(User user, PeerClient client) {
        this.currentUser = user;
        this.peerClient = client;

        initializePanel();
        createComponents();
        setupLayout();
        refreshData();
    }

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(248, 250, 252));
        setBorder(new EmptyBorder(20, 30, 20, 30));
    }

    private void createComponents() {
        createHeader();
        createStatsCards();
        createQuickActions();
        createNetworkStatus();
    }

    private void createHeader() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(248, 250, 252));
        headerPanel.setBorder(new EmptyBorder(0, 0, 20, 0));

        JLabel titleLabel = new JLabel("Dashboard");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(new Color(30, 41, 59));

        JLabel subtitleLabel = new JLabel("Welcome back, " + currentUser.getUsername());
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        subtitleLabel.setForeground(new Color(100, 116, 139));

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setBackground(new Color(248, 250, 252));
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(5));
        titlePanel.add(subtitleLabel);

        // Current time
        JLabel timeLabel = new JLabel(new SimpleDateFormat("EEEE, MMMM d, yyyy").format(new Date()));
        timeLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        timeLabel.setForeground(new Color(107, 114, 128));

        headerPanel.add(titlePanel, BorderLayout.WEST);
        headerPanel.add(timeLabel, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);
    }

    private void createStatsCards() {
        statsCardsPanel = new JPanel(new GridLayout(1, 4, 15, 0));
        statsCardsPanel.setBackground(new Color(248, 250, 252));
        statsCardsPanel.setBorder(new EmptyBorder(0, 0, 25, 0));

        // Initialize stat cards
        filesSharedCard = new StatCard("Files Shared", "0", new Color(59, 130, 246), "📁");
        dataTransferredCard = new StatCard("Data Transferred", "0 B", new Color(34, 197, 94), "📊");

        statsCardsPanel.add(filesSharedCard);
        statsCardsPanel.add(dataTransferredCard);
      
    }

    private void createQuickActions() {
        quickActionsPanel = new JPanel(new GridLayout(2, 3, 15, 15));
        quickActionsPanel.setBackground(new Color(248, 250, 252));
        quickActionsPanel.setBorder(new EmptyBorder(0, 0, 25, 0));

        // Quick action buttons
        JButton searchFilesBtn = createQuickActionButton("Search Files", "🔍", new Color(59, 130, 246));
        JButton browseNetworkBtn = createQuickActionButton("Browse Network", "🌐", new Color(34, 197, 94));
        JButton shareFileBtn = createQuickActionButton("Share New File", "📤", new Color(168, 85, 247));
        JButton viewStatsBtn = createQuickActionButton("View Statistics", "📈", new Color(245, 158, 11));
        JButton settingsBtn = createQuickActionButton("Settings", "⚙️", new Color(107, 114, 128));
        JButton helpBtn = createQuickActionButton("Help & Support", "❓", new Color(239, 68, 68));

        // Add action listeners
        searchFilesBtn.addActionListener(e -> triggerNavigation("Search Files"));
        browseNetworkBtn.addActionListener(e -> triggerNavigation("Browse Peers"));
        shareFileBtn.addActionListener(e -> triggerNavigation("My Files"));
        viewStatsBtn.addActionListener(e -> triggerNavigation("Statistics"));
        settingsBtn.addActionListener(e -> triggerNavigation("Settings"));
        helpBtn.addActionListener(e -> showHelp());

        quickActionsPanel.add(searchFilesBtn);
        quickActionsPanel.add(browseNetworkBtn);
        quickActionsPanel.add(shareFileBtn);
        quickActionsPanel.add(viewStatsBtn);
        quickActionsPanel.add(settingsBtn);
        quickActionsPanel.add(helpBtn);
    }


    private void createNetworkStatus() {
        networkStatusPanel = new JPanel(new BorderLayout());
        networkStatusPanel.setBackground(Color.WHITE);
        networkStatusPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
                new EmptyBorder(20, 20, 20, 20)
        ));
        networkStatusPanel.setPreferredSize(new Dimension(0, 150));

        JLabel networkTitle = new JLabel("Network Status");
        networkTitle.setFont(new Font("Arial", Font.BOLD, 18));
        networkTitle.setForeground(new Color(30, 41, 59));

        JPanel statusContent = new JPanel();
        statusContent.setLayout(new BoxLayout(statusContent, BoxLayout.Y_AXIS));
        statusContent.setBackground(Color.WHITE);

        JLabel connectionStatus = new JLabel("Status: Connected");
        connectionStatus.setFont(new Font("Arial", Font.PLAIN, 14));
        connectionStatus.setForeground(new Color(34, 197, 94));

        JLabel serverInfo = new JLabel("Server: localhost:9090");
        serverInfo.setFont(new Font("Arial", Font.PLAIN, 12));
        serverInfo.setForeground(new Color(107, 114, 128));

        JLabel networkPeers = new JLabel("Connected Peers: Loading...");
        networkPeers.setFont(new Font("Arial", Font.PLAIN, 12));
        networkPeers.setForeground(new Color(107, 114, 128));

        statusContent.add(connectionStatus);
        statusContent.add(Box.createVerticalStrut(8));
        statusContent.add(serverInfo);
        statusContent.add(Box.createVerticalStrut(5));
        statusContent.add(networkPeers);

        networkStatusPanel.add(networkTitle, BorderLayout.NORTH);
        networkStatusPanel.add(Box.createVerticalStrut(15));
        networkStatusPanel.add(statusContent, BorderLayout.CENTER);
    }

    private void setupLayout() {
        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.setBackground(new Color(248, 250, 252));

        // Top section with stats
        mainContent.add(statsCardsPanel, BorderLayout.NORTH);

        // Middle section with quick actions
        JPanel middleSection = new JPanel(new BorderLayout());
        middleSection.setBackground(new Color(248, 250, 252));
        middleSection.setBorder(new EmptyBorder(0, 0, 25, 0));

        JLabel quickActionsTitle = new JLabel("Quick Actions");
        quickActionsTitle.setFont(new Font("Arial", Font.BOLD, 20));
        quickActionsTitle.setForeground(new Color(30, 41, 59));
        quickActionsTitle.setBorder(new EmptyBorder(0, 0, 15, 0));

        middleSection.add(quickActionsTitle, BorderLayout.NORTH);
        middleSection.add(quickActionsPanel, BorderLayout.CENTER);

        mainContent.add(middleSection, BorderLayout.CENTER);

        // Bottom section with activity and network status
        JPanel bottomSection = new JPanel(new GridLayout(1, 2, 20, 0));
        bottomSection.setBackground(new Color(248, 250, 252));
        bottomSection.add(networkStatusPanel);

        mainContent.add(bottomSection, BorderLayout.SOUTH);

        add(mainContent, BorderLayout.CENTER);
    }

    private JButton createQuickActionButton(String text, String icon, Color color) {
        JButton button = new JButton("<html><div style='text-align: center;'>" +
                "<div style='font-size: 24px; margin-bottom: 5px;'>" + icon + "</div>" +
                "<div style='font-size: 12px;'>" + text + "</div></div></html>");

        button.setPreferredSize(new Dimension(150, 80));
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setFont(new Font("Arial", Font.BOLD, 12));

        // Hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            Color originalColor = color;

            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(originalColor.darker());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(originalColor);
            }
        });

        return button;
    }

    public void refreshData() {
        // Update statistics cards
        updateStatsCards();

        // Update network status
        updateNetworkStatus();
    }

    private void updateStatsCards() {
        SwingWorker<Void, Void> updateWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Get current stats
                long filesCount = currentUser.getDownloadStats().getFileCount() +
                        currentUser.getUploadStats().getFileCount();
                long totalBytes = currentUser.getDownloadStats().getTotalBytes() +
                        currentUser.getUploadStats().getTotalBytes();

                SwingUtilities.invokeLater(() -> {
                    filesSharedCard.updateValue(String.valueOf(filesCount));
                    dataTransferredCard.updateValue(formatFileSize(totalBytes));
                    
                });

                return null;
            }
        };

        updateWorker.execute();
    }


    private void updateNetworkStatus() {
        // This would typically query the peer client for network information
        SwingUtilities.invokeLater(() -> {
            // Update network status labels
            // This is a placeholder - real implementation would query peerClient
        });
    }

    private void triggerNavigation(String panelName) {
        // Get parent frame and trigger navigation
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        if (parentWindow instanceof MainApplicationFrame) {
            MainApplicationFrame mainFrame = (MainApplicationFrame) parentWindow;
            mainFrame.showPanel(panelName);
        }
    }

    private void showHelp() {
        String helpText = "P2P File Sharing Help\n\n" +
                "Quick Actions:\n" +
                "- Search Files: Find files shared by other peers\n" +
                "- Browse Network: View all connected peers\n" +
                "- Share New File: Add files to your shared directory\n" +
                "- View Statistics: See your download/upload stats\n" +
                "- Settings: Configure application preferences\n\n" +
                "For more help, check the documentation or contact support.";

        JOptionPane.showMessageDialog(this, helpText, "Help & Support", JOptionPane.INFORMATION_MESSAGE);
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double displaySize = bytes;
        while (displaySize >= 1024 && unitIndex < units.length - 1) {
            displaySize /= 1024;
            unitIndex++;
        }
        return String.format("%.1f %s", displaySize, units[unitIndex]);
    }

    // Inner class for statistics cards
    private static class StatCard extends JPanel {
        private JLabel valueLabel;
        private String title;
        private String icon;
        private Color color;

        public StatCard(String title, String initialValue, Color color, String icon) {
            this.title = title;
            this.color = color;
            this.icon = icon;

            setLayout(new BorderLayout());
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
                    new EmptyBorder(20, 20, 20, 20)
            ));
            setPreferredSize(new Dimension(200, 120));

            // Icon and title
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(Color.WHITE);

            JLabel iconLabel = new JLabel(icon);
            iconLabel.setFont(new Font("Arial", Font.PLAIN, 24));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(new Font("Arial", Font.BOLD, 12));
            titleLabel.setForeground(new Color(107, 114, 128));

            headerPanel.add(iconLabel, BorderLayout.WEST);
            headerPanel.add(titleLabel, BorderLayout.CENTER);

            // Value
            valueLabel = new JLabel(initialValue);
            valueLabel.setFont(new Font("Arial", Font.BOLD, 28));
            valueLabel.setForeground(color);

            add(headerPanel, BorderLayout.NORTH);
            add(Box.createVerticalStrut(10), BorderLayout.CENTER);
            add(valueLabel, BorderLayout.SOUTH);
        }

        public void updateValue(String newValue) {
            valueLabel.setText(newValue);
            repaint();
        }
    }

    // Custom cell renderer for activity list
    private static class ActivityListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            setBorder(new EmptyBorder(8, 12, 8, 12));
            setFont(new Font("Arial", Font.PLAIN, 12));

            if (!isSelected) {
                setBackground(index % 2 == 0 ? Color.WHITE : new Color(248, 250, 252));
                setForeground(new Color(30, 41, 59));
            }

            return this;
        }
    }

}

