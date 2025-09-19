import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class StatisticsPanel extends JPanel {
    private User currentUser;
    private JLabel downloadFilesLabel, downloadBytesLabel, uploadFilesLabel, uploadBytesLabel;
    private JLabel sessionFilesLabel, sessionBytesLabel, uptimeLabel, peersConnectedLabel;
    private ChartPanel downloadChart, uploadChart, networkChart;
    private JProgressBar storageUsageBar;
    private JLabel storageLabel;
    private Timer refreshTimer;
    private LocalDateTime sessionStart;
    private List<Long> downloadHistory, uploadHistory, networkActivity;
    private long sessionDownloads, sessionUploads;
    private int connectedPeers;

    // Color scheme
    private final Color PRIMARY_COLOR = new Color(64, 128, 255);
    private final Color SUCCESS_COLOR = new Color(76, 175, 80);
    private final Color WARNING_COLOR = new Color(255, 152, 0);
    private final Color DANGER_COLOR = new Color(244, 67, 54);

    public StatisticsPanel(User user) {
        this.currentUser = user;
        this.sessionStart = LocalDateTime.now();
        this.downloadHistory = new ArrayList<>();
        this.uploadHistory = new ArrayList<>();
        this.networkActivity = new ArrayList<>();

        // Initialize with some sample data
        for (int i = 0; i < 20; i++) {
            downloadHistory.add(0L);
            uploadHistory.add(0L);
            networkActivity.add(0L);
        }

        initializeComponents();
        setupLayout();
        startRefreshTimer();
        updateStatistics();
    }

    private void initializeComponents() {
        // Statistics labels
        downloadFilesLabel = new JLabel("0");
        downloadBytesLabel = new JLabel("0 B");
        uploadFilesLabel = new JLabel("0");
        uploadBytesLabel = new JLabel("0 B");
        sessionFilesLabel = new JLabel("0");
        sessionBytesLabel = new JLabel("0 B");
        uptimeLabel = new JLabel("00:00:00");
        peersConnectedLabel = new JLabel("0");

        // Storage usage
        storageUsageBar = new JProgressBar(0, 100);
        storageUsageBar.setStringPainted(true);
        storageLabel = new JLabel("Storage: 0 B / 0 B");

        // Charts
        downloadChart = new ChartPanel("Download Activity", SUCCESS_COLOR);
        uploadChart = new ChartPanel("Upload Activity", PRIMARY_COLOR);
        networkChart = new ChartPanel("Network Activity", WARNING_COLOR);

        // Set fonts
        Font labelFont = new Font(Font.SANS_SERIF, Font.BOLD, 16);
        Font valueFont = new Font(Font.SANS_SERIF, Font.PLAIN, 20);

        downloadFilesLabel.setFont(valueFont);
        downloadBytesLabel.setFont(valueFont);
        uploadFilesLabel.setFont(valueFont);
        uploadBytesLabel.setFont(valueFont);
        sessionFilesLabel.setFont(valueFont);
        sessionBytesLabel.setFont(valueFont);
        uptimeLabel.setFont(valueFont);
        peersConnectedLabel.setFont(valueFont);
    }

    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Top panel with statistics cards
        JPanel statsPanel = createStatsPanel();

        // Center panel with charts
        JPanel chartsPanel = createChartsPanel();

        // Bottom panel with additional info
        JPanel bottomPanel = createBottomPanel();

        add(statsPanel, BorderLayout.NORTH);
        add(chartsPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 4, 15, 15));
        panel.setBorder(new TitledBorder("Statistics Overview"));

        // Download stats
        panel.add(createStatCard("Total Downloads", downloadFilesLabel, downloadBytesLabel, SUCCESS_COLOR));

        // Upload stats
        panel.add(createStatCard("Total Uploads", uploadFilesLabel, uploadBytesLabel, PRIMARY_COLOR));

        // Session stats
        panel.add(createStatCard("Session Activity", sessionFilesLabel, sessionBytesLabel, WARNING_COLOR));

        // Network stats
        panel.add(createStatCard("Network", peersConnectedLabel, uptimeLabel, DANGER_COLOR));

        return panel;
    }

    private JPanel createStatCard(String title, JLabel mainLabel, JLabel subLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accentColor, 2),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        card.setBackground(Color.WHITE);

        // Title
        JLabel titleLabel = new JLabel(title, JLabel.CENTER);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        titleLabel.setForeground(accentColor);

        // Main value
        mainLabel.setHorizontalAlignment(JLabel.CENTER);
        mainLabel.setForeground(Color.BLACK);

        // Sub value
        subLabel.setHorizontalAlignment(JLabel.CENTER);
        subLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        subLabel.setForeground(Color.GRAY);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(mainLabel, BorderLayout.CENTER);
        card.add(subLabel, BorderLayout.SOUTH);

        return card;
    }

    private JPanel createChartsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBorder(new TitledBorder("Activity Charts"));

        panel.add(downloadChart);
        panel.add(uploadChart);
        panel.add(networkChart);

        // Storage usage panel
        JPanel storagePanel = new JPanel(new BorderLayout(5, 5));
        storagePanel.setBorder(new TitledBorder("Storage Usage"));

        storagePanel.add(storageLabel, BorderLayout.NORTH);
        storagePanel.add(storageUsageBar, BorderLayout.CENTER);

        JPanel storageWrapper = new JPanel(new BorderLayout());
        storageWrapper.add(storagePanel, BorderLayout.NORTH);

        panel.add(storageWrapper);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Session Information"));

        JLabel userLabel = new JLabel("User: " + currentUser.getUsername());
        JLabel sessionStartLabel = new JLabel("Session started: " +
                sessionStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        userLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        sessionStartLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        panel.add(userLabel);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(sessionStartLabel);

        return panel;
    }

    private void startRefreshTimer() {
        refreshTimer = new Timer(true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> updateStatistics());
            }
        }, 1000, 5000); // Update every 5 seconds
    }

    public void updateStatistics() {
        // Update main statistics
        downloadFilesLabel.setText(String.valueOf(currentUser.getDownloadStats().getFileCount()));
        downloadBytesLabel.setText(formatBytes(currentUser.getDownloadStats().getTotalBytes()));

        uploadFilesLabel.setText(String.valueOf(currentUser.getUploadStats().getFileCount()));
        uploadBytesLabel.setText(formatBytes(currentUser.getUploadStats().getTotalBytes()));

        sessionFilesLabel.setText(String.valueOf(sessionDownloads + sessionUploads));
        sessionBytesLabel.setText(formatBytes(getSessionBytes()));

        peersConnectedLabel.setText(String.valueOf(connectedPeers) + " peers");
        uptimeLabel.setText(getUptime());

        // Update charts
        updateChartData();

        // Update storage
        updateStorageInfo();
    }

    public void refreshData() {
        if (SwingUtilities.isEventDispatchThread()) {
            updateStatistics();
        } else {
            SwingUtilities.invokeLater(this::updateStatistics);
        }
    }


    private void updateChartData() {
        // Add current data to history
        long currentDownload = currentUser.getDownloadStats().getTotalBytes();
        long currentUpload = currentUser.getUploadStats().getTotalBytes();

        // Calculate recent activity (difference from last reading)
        long downloadDelta = downloadHistory.isEmpty() ? 0 :
                Math.max(0, currentDownload - downloadHistory.get(downloadHistory.size() - 1));
        long uploadDelta = uploadHistory.isEmpty() ? 0 :
                Math.max(0, currentUpload - uploadHistory.get(uploadHistory.size() - 1));

        // Maintain history size
        if (downloadHistory.size() >= 20) {
            downloadHistory.remove(0);
            uploadHistory.remove(0);
            networkActivity.remove(0);
        }

        downloadHistory.add(downloadDelta);
        uploadHistory.add(uploadDelta);
        networkActivity.add((long) connectedPeers);

        // Update charts
        downloadChart.updateData(downloadHistory);
        uploadChart.updateData(uploadHistory);
        networkChart.updateData(networkActivity);
    }

    private void updateStorageInfo() {
        try {
            java.io.File file = new java.io.File(System.getProperty("user.home"));
            long totalSpace = file.getTotalSpace();
            long freeSpace = file.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;

            int usage = (int) ((usedSpace * 100) / totalSpace);
            storageUsageBar.setValue(usage);
            storageUsageBar.setString(usage + "%");

            storageLabel.setText(String.format("Storage: %s / %s",
                    formatBytes(usedSpace), formatBytes(totalSpace)));

        } catch (Exception e) {
            storageLabel.setText("Storage: Unknown");
        }
    }

    private String formatBytes(long bytes) {
        if (bytes == 0) return "0 B";

        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = (int) (Math.log10(bytes) / Math.log10(1024));
        double size = bytes / Math.pow(1024, unitIndex);

        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(size) + " " + units[unitIndex];
    }

    private String getUptime() {
        LocalDateTime now = LocalDateTime.now();
        java.time.Duration duration = java.time.Duration.between(sessionStart, now);

        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private long getSessionBytes() {
        // Calculate session-specific bytes (this would need integration with actual session tracking)
        return sessionDownloads * 1024 + sessionUploads * 1024; // Placeholder calculation
    }

    // Public methods for external updates
    public void updatePeerCount(int peerCount) {
        this.connectedPeers = peerCount;
    }

    public void recordDownload(long bytes) {
        sessionDownloads++;
    }

    public void recordUpload(long bytes) {
        sessionUploads++;
    }

    public void updateUser(User user) {
        this.currentUser = user;
        updateStatistics();
    }

    public void cleanup() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
        }
    }

    // Inner class for chart panels
    private class ChartPanel extends JPanel {
        private String title;
        private Color color;
        private List<Long> data;
        private long maxValue = 1;

        public ChartPanel(String title, Color color) {
            this.title = title;
            this.color = color;
            this.data = new ArrayList<>();
            setBorder(new TitledBorder(title));
            setPreferredSize(new Dimension(200, 150));
        }

        public void updateData(List<Long> newData) {
            this.data = new ArrayList<>(newData);

            // Calculate max value for scaling
            maxValue = Math.max(1, data.stream().mapToLong(Long::longValue).max().orElse(1));

            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (data.isEmpty()) return;

            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth() - 20;
            int height = getHeight() - 40;
            int startX = 10;
            int startY = 20;

            // Draw background
            g2d.setColor(new Color(245, 245, 245));
            g2d.fillRect(startX, startY, width, height);

            // Draw grid lines
            g2d.setColor(new Color(220, 220, 220));
            for (int i = 1; i < 5; i++) {
                int y = startY + (height * i) / 5;
                g2d.drawLine(startX, y, startX + width, y);
            }

            // Draw chart line
            if (data.size() > 1) {
                g2d.setColor(color);
                g2d.setStroke(new BasicStroke(2));

                int pointSpacing = width / Math.max(1, data.size() - 1);

                for (int i = 0; i < data.size() - 1; i++) {
                    int x1 = startX + i * pointSpacing;
                    int x2 = startX + (i + 1) * pointSpacing;

                    int y1 = startY + height - (int) ((data.get(i) * height) / maxValue);
                    int y2 = startY + height - (int) ((data.get(i + 1) * height) / maxValue);

                    g2d.drawLine(x1, y1, x2, y2);
                }

                // Draw points
                g2d.setColor(color.darker());
                for (int i = 0; i < data.size(); i++) {
                    int x = startX + i * pointSpacing;
                    int y = startY + height - (int) ((data.get(i) * height) / maxValue);
                    g2d.fillOval(x - 2, y - 2, 4, 4);
                }
            }

            // Draw current value
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            if (!data.isEmpty()) {
                String valueText = formatBytes(data.get(data.size() - 1));
                FontMetrics fm = g2d.getFontMetrics();
                Rectangle2D rect = fm.getStringBounds(valueText, g2d);
                g2d.drawString(valueText,
                        (int) (getWidth() - rect.getWidth() - 5),
                        getHeight() - 5);
            }

            g2d.dispose();
        }
    }
}