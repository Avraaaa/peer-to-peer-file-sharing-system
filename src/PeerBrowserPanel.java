import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;

public class PeerBrowserPanel extends JPanel {
    private User currentUser;
    private Transport serverTransport;
    private PeerClient peerClient;

    // Components
    private JTable peersTable;
    private DefaultTableModel peersTableModel;
    private JLabel statusLabel;
    private JButton refreshButton;
    private JProgressBar loadingProgress;

    // File browser components
    private JPanel fileBrowserPanel;
    private JTable filesTable;
    private DefaultTableModel filesTableModel;
    private JLabel selectedPeerLabel;
    private JButton downloadButton;
    private JButton viewDetailsButton;

    // Current data
    private Map<String, String> onlinePeers = new LinkedHashMap<>();
    private String selectedPeerName;
    private String selectedPeerAddress;

    public PeerBrowserPanel(User user, Transport transport, PeerClient client) {
        this.currentUser = user;
        this.serverTransport = transport;
        this.peerClient = client;

        initializePanel();
        createComponents();
        setupLayout();
        setupEventHandlers();
        refreshPeerList();
    }

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(248, 250, 252));
        setBorder(new EmptyBorder(20, 30, 20, 30));
    }

    private void createComponents() {
        createHeader();
        createPeersList();
        createFileBrowser();
    }

    private void createHeader() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(248, 250, 252));
        headerPanel.setBorder(new EmptyBorder(0, 0, 25, 0));

        JLabel titleLabel = new JLabel("Network Peers");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(new Color(30, 41, 59));

        JLabel subtitleLabel = new JLabel("Browse files shared by peers on the network");
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        subtitleLabel.setForeground(new Color(100, 116, 139));

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setBackground(new Color(248, 250, 252));
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(5));
        titlePanel.add(subtitleLabel);

        // Refresh button
        refreshButton = new JButton("Refresh");
        refreshButton.setPreferredSize(new Dimension(100, 35));
        refreshButton.setFont(new Font("Arial", Font.BOLD, 12));
        refreshButton.setBackground(new Color(59, 130, 246));
        refreshButton.setForeground(Color.WHITE);
        refreshButton.setBorder(BorderFactory.createEmptyBorder());
        refreshButton.setFocusPainted(false);
        refreshButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        headerPanel.add(titlePanel, BorderLayout.WEST);
        headerPanel.add(refreshButton, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);
    }

    private void createPeersList() {
        JPanel peersPanel = new JPanel(new BorderLayout());
        peersPanel.setBackground(Color.WHITE);
        peersPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
                new EmptyBorder(20, 25, 20, 25)
        ));
        peersPanel.setPreferredSize(new Dimension(0, 280));

        // Header
        JPanel peersHeader = new JPanel(new BorderLayout());
        peersHeader.setBackground(Color.WHITE);
        peersHeader.setBorder(new EmptyBorder(0, 0, 15, 0));

        statusLabel = new JLabel("Loading peers...");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        statusLabel.setForeground(new Color(30, 41, 59));

        loadingProgress = new JProgressBar();
        loadingProgress.setIndeterminate(true);
        loadingProgress.setVisible(true);
        loadingProgress.setPreferredSize(new Dimension(0, 4));

        peersHeader.add(statusLabel, BorderLayout.WEST);
        peersHeader.add(loadingProgress, BorderLayout.SOUTH);

        // Peers table
        String[] peerColumns = {"Status", "Username", "Address", "Files Shared"};
        peersTableModel = new DefaultTableModel(peerColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        peersTable = new JTable(peersTableModel);
        peersTable.setFont(new Font("Arial", Font.PLAIN, 12));
        peersTable.setRowHeight(40);
        peersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peersTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        peersTable.getTableHeader().setBackground(new Color(248, 250, 252));
        peersTable.setGridColor(new Color(229, 231, 235));
        peersTable.setSelectionBackground(new Color(219, 234, 254));

        // Set column widths
        peersTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        peersTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        peersTable.getColumnModel().getColumn(2).setPreferredWidth(180);
        peersTable.getColumnModel().getColumn(3).setPreferredWidth(100);

        // Custom renderer for status column
        peersTable.getColumnModel().getColumn(0).setCellRenderer(new StatusCellRenderer());

        JScrollPane peersScrollPane = new JScrollPane(peersTable);
        peersScrollPane.setPreferredSize(new Dimension(0, 200));
        peersScrollPane.setBorder(BorderFactory.createLineBorder(new Color(229, 231, 235), 1));

        peersPanel.add(peersHeader, BorderLayout.NORTH);
        peersPanel.add(peersScrollPane, BorderLayout.CENTER);

        add(peersPanel, BorderLayout.CENTER);
    }

    private void createFileBrowser() {
        fileBrowserPanel = new JPanel(new BorderLayout());
        fileBrowserPanel.setBackground(Color.WHITE);
        fileBrowserPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
                new EmptyBorder(20, 25, 20, 25)
        ));
        fileBrowserPanel.setVisible(false);

        // Header
        JPanel fileHeader = new JPanel(new BorderLayout());
        fileHeader.setBackground(Color.WHITE);
        fileHeader.setBorder(new EmptyBorder(0, 0, 15, 0));

        selectedPeerLabel = new JLabel("Select a peer to browse files");
        selectedPeerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        selectedPeerLabel.setForeground(new Color(30, 41, 59));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonPanel.setBackground(Color.WHITE);

        viewDetailsButton = new JButton("View Details");
        viewDetailsButton.setPreferredSize(new Dimension(110, 30));
        viewDetailsButton.setFont(new Font("Arial", Font.PLAIN, 11));
        viewDetailsButton.setBackground(new Color(107, 114, 128));
        viewDetailsButton.setForeground(Color.WHITE);
        viewDetailsButton.setBorder(BorderFactory.createEmptyBorder());
        viewDetailsButton.setEnabled(false);

        downloadButton = new JButton("Download");
        downloadButton.setPreferredSize(new Dimension(90, 30));
        downloadButton.setFont(new Font("Arial", Font.BOLD, 11));
        downloadButton.setBackground(new Color(34, 197, 94));
        downloadButton.setForeground(Color.WHITE);
        downloadButton.setBorder(BorderFactory.createEmptyBorder());
        downloadButton.setEnabled(false);

        buttonPanel.add(viewDetailsButton);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(downloadButton);

        fileHeader.add(selectedPeerLabel, BorderLayout.WEST);
        fileHeader.add(buttonPanel, BorderLayout.EAST);

        // Files table
        String[] fileColumns = {"File Name", "Size", "Type", "Last Modified"};
        filesTableModel = new DefaultTableModel(fileColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        filesTable = new JTable(filesTableModel);
        filesTable.setFont(new Font("Arial", Font.PLAIN, 12));
        filesTable.setRowHeight(35);
        filesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        filesTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        filesTable.getTableHeader().setBackground(new Color(248, 250, 252));
        filesTable.setGridColor(new Color(229, 231, 235));
        filesTable.setSelectionBackground(new Color(219, 234, 254));

        // Set column widths
        filesTable.getColumnModel().getColumn(0).setPreferredWidth(250);
        filesTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        filesTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        filesTable.getColumnModel().getColumn(3).setPreferredWidth(120);

        JScrollPane filesScrollPane = new JScrollPane(filesTable);
        filesScrollPane.setBorder(BorderFactory.createLineBorder(new Color(229, 231, 235), 1));

        fileBrowserPanel.add(fileHeader, BorderLayout.NORTH);
        fileBrowserPanel.add(filesScrollPane, BorderLayout.CENTER);

        add(fileBrowserPanel, BorderLayout.SOUTH);
    }

    private void setupLayout() {
        // Layout is already set up in createComponents
    }

    private void setupEventHandlers() {
        // Refresh button
        refreshButton.addActionListener(e -> refreshPeerList());

        // Peer selection
        peersTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = peersTable.getSelectedRow();
                if (selectedRow >= 0) {
                    selectedPeerName = (String) peersTableModel.getValueAt(selectedRow, 1);
                    selectedPeerAddress = onlinePeers.get(selectedPeerName);
                    loadPeerFiles();
                }
            }
        });

        // File selection
        filesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                boolean hasSelection = filesTable.getSelectedRow() >= 0;
                downloadButton.setEnabled(hasSelection);
                viewDetailsButton.setEnabled(hasSelection);
            }
        });

        // Double-click to download
        filesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && filesTable.getSelectedRow() >= 0) {
                    downloadSelectedFile();
                }
            }
        });

        // Button actions
        downloadButton.addActionListener(e -> downloadSelectedFile());
        viewDetailsButton.addActionListener(e -> viewFileDetails());
    }

    public void refreshPeerList() {
        loadingProgress.setVisible(true);
        refreshButton.setEnabled(false);
        statusLabel.setText("Loading peers...");
        peersTableModel.setRowCount(0);
        fileBrowserPanel.setVisible(false);

        SwingWorker<Map<String, String>, Void> peerWorker = new SwingWorker<Map<String, String>, Void>() {
            @Override
            protected Map<String, String> doInBackground() throws Exception {
                try {
                    serverTransport.sendLine("LIST_PEERS");
                    String response = serverTransport.readLine();
                    return parsePeerInfoResponse(response);
                } catch (IOException e) {
                    throw e;
                }
            }

            @Override
            protected void done() {
                try {
                    onlinePeers = get();
                    displayPeers();
                } catch (Exception e) {
                    statusLabel.setText("Failed to load peers: " + e.getMessage());
                } finally {
                    loadingProgress.setVisible(false);
                    refreshButton.setEnabled(true);
                }
            }
        };

        peerWorker.execute();
    }

    private void displayPeers() {
        // Remove current user from the list
        onlinePeers.remove(currentUser.getUsername());

        if (onlinePeers.isEmpty()) {
            statusLabel.setText("No other peers online");
            return;
        }

        statusLabel.setText("Found " + onlinePeers.size() + " peer(s) online");

        // Add peers to table
        for (Map.Entry<String, String> entry : onlinePeers.entrySet()) {
            String username = entry.getKey();
            String address = entry.getValue();

            // Check if peer is responsive
            boolean isOnline = checkPeerStatus(address);
            String status = isOnline ? "Online" : "Offline";

            // Get file count (placeholder for now)
            int fileCount = isOnline ? getFileCount(address) : 0;

            Object[] rowData = {
                    status,
                    username,
                    address,
                    fileCount + " files"
            };

            peersTableModel.addRow(rowData);
        }
    }

    private boolean checkPeerStatus(String peerAddress) {
        try {
            String[] parts = peerAddress.split(":");
            String host = parts[0];
            int udpPort = Integer.parseInt(parts[1]) + 1;

            try (UDPTransport transport = new UDPTransport(host, udpPort)) {
                transport.setSoTimeout(2000);
                transport.sendLine("LIST_FILES");
                transport.readLine();
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private int getFileCount(String peerAddress) {
        try {
            List<String> files = getFileListFromPeer(peerAddress);
            return files != null ? files.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void loadPeerFiles() {
        if (selectedPeerAddress == null) return;

        selectedPeerLabel.setText("Loading files from " + selectedPeerName + "...");
        fileBrowserPanel.setVisible(true);
        filesTableModel.setRowCount(0);
        revalidate();

        SwingWorker<List<String>, Void> fileWorker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return getFileListFromPeer(selectedPeerAddress);
            }

            @Override
            protected void done() {
                try {
                    List<String> files = get();
                    displayPeerFiles(files);
                } catch (Exception e) {
                    selectedPeerLabel.setText("Failed to load files from " + selectedPeerName);
                }
            }
        };

        fileWorker.execute();
    }

    private void displayPeerFiles(List<String> files) {
        if (files == null || files.isEmpty()) {
            selectedPeerLabel.setText(selectedPeerName + " has no shared files");
            return;
        }

        selectedPeerLabel.setText("Files from " + selectedPeerName + " (" + files.size() + " files)");

        // Load file details
        SwingWorker<Void, Object[]> detailWorker = new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (String fileName : files) {
                    long fileSize = getPeerFileSize(selectedPeerAddress, fileName);
                    String fileType = getFileTypeFromName(fileName);
                    String sizeString = fileSize > 0 ? formatFileSize(fileSize) : "Unknown";

                    Object[] rowData = {
                            fileName,
                            sizeString,
                            fileType,
                            "Unknown"
                    };

                    publish(rowData);
                }
                return null;
            }

            @Override
            protected void process(java.util.List<Object[]> chunks) {
                for (Object[] rowData : chunks) {
                    filesTableModel.addRow(rowData);
                }
            }
        };

        detailWorker.execute();
    }

    private List<String> getFileListFromPeer(String peerAddress) {
        try {
            String[] parts = peerAddress.split(":");
            String host = parts[0];
            int udpPort = Integer.parseInt(parts[1]) + 1;

            try (UDPTransport peerTransport = new UDPTransport(host, udpPort)) {
                peerTransport.setSoTimeout(3000);
                peerTransport.sendLine("LIST_FILES");
                String line = peerTransport.readLine();
                if (line == null || line.trim().isEmpty()) {
                    return Collections.emptyList();
                }
                return Arrays.asList(line.split(","));
            }
        } catch (Exception e) {
            return null;
        }
    }

    private long getPeerFileSize(String peerAddress, String fileName) {
        try {
            String[] parts = peerAddress.split(":");
            String host = parts[0];
            int udpPort = Integer.parseInt(parts[1]) + 1;

            try (UDPTransport transport = new UDPTransport(host, udpPort)) {
                transport.setSoTimeout(3000);
                transport.sendLine("FILESIZE " + fileName);
                String response = transport.readLine();
                if (response != null && !response.trim().isEmpty()) {
                    return Long.parseLong(response.trim());
                }
            }
        } catch (Exception e) {
            // Return -1 for any error
        }
        return -1;
    }

    private void downloadSelectedFile() {
        int selectedRow = filesTable.getSelectedRow();
        if (selectedRow < 0) return;

        String fileName = (String) filesTableModel.getValueAt(selectedRow, 0);

        int option = JOptionPane.showConfirmDialog(
                this,
                "Download \"" + fileName + "\" from " + selectedPeerName + "?",
                "Confirm Download",
                JOptionPane.YES_NO_OPTION
        );

        if (option == JOptionPane.YES_OPTION) {
            showDownloadProgress(fileName);
        }
    }

    private void viewFileDetails() {
        int selectedRow = filesTable.getSelectedRow();
        if (selectedRow < 0) return;

        String fileName = (String) filesTableModel.getValueAt(selectedRow, 0);
        String fileSize = (String) filesTableModel.getValueAt(selectedRow, 1);
        String fileType = (String) filesTableModel.getValueAt(selectedRow, 2);

        // Create details dialog
        JDialog detailsDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "File Details", true);
        detailsDialog.setSize(400, 300);
        detailsDialog.setLocationRelativeTo(this);

        JPanel detailsPanel = new JPanel();
        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
        detailsPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        detailsPanel.setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel("File Information");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel infoPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        infoPanel.setBackground(Color.WHITE);
        infoPanel.setBorder(new EmptyBorder(20, 0, 20, 0));

        infoPanel.add(new JLabel("File Name:"));
        infoPanel.add(new JLabel(fileName));
        infoPanel.add(new JLabel("Size:"));
        infoPanel.add(new JLabel(fileSize));
        infoPanel.add(new JLabel("Type:"));
        infoPanel.add(new JLabel(fileType));
        infoPanel.add(new JLabel("Peer:"));
        infoPanel.add(new JLabel(selectedPeerName));
        infoPanel.add(new JLabel("Address:"));
        infoPanel.add(new JLabel(selectedPeerAddress));

        JButton closeButton = new JButton("Close");
        closeButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        closeButton.addActionListener(e -> detailsDialog.dispose());

        detailsPanel.add(titleLabel);
        detailsPanel.add(infoPanel);
        detailsPanel.add(closeButton);

        detailsDialog.add(detailsPanel);
        detailsDialog.setVisible(true);
    }

    private void showDownloadProgress(String fileName) {
        JDialog progressDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Downloading", true);
        progressDialog.setSize(400, 150);
        progressDialog.setLocationRelativeTo(this);

        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel progressLabel = new JLabel("Downloading " + fileName + " from " + selectedPeerName + "...");
        progressLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);
        progressBar.setString("Connecting to peer...");

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(new Color(239, 68, 68));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setBorder(BorderFactory.createEmptyBorder());

        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(Box.createVerticalStrut(15));
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(Box.createVerticalStrut(15));

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(cancelButton);
        progressPanel.add(buttonPanel, BorderLayout.SOUTH);

        progressDialog.add(progressPanel);

        // Simulate download (integrate with actual download logic)
        SwingWorker<Boolean, String> downloadWorker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    publish("Starting download...");

                    // Simulate download progress
                    for (int i = 0; i <= 100; i += 5) {
                        if (isCancelled()) break;
                        Thread.sleep(100);
                        publish("Downloading... " + i + "%");
                    }

                    if (!isCancelled()) {
                        publish("Download completed!");
                        return true;
                    }
                    return false;

                } catch (Exception e) {
                    publish("Download failed: " + e.getMessage());
                    return false;
                }
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                if (!chunks.isEmpty()) {
                    progressBar.setString(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    progressDialog.dispose();

                    if (success && !isCancelled()) {
                        JOptionPane.showMessageDialog(PeerBrowserPanel.this,
                                "Download completed successfully!",
                                "Download Complete",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(PeerBrowserPanel.this,
                            "Download failed: " + e.getMessage(),
                            "Download Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        cancelButton.addActionListener(e -> {
            downloadWorker.cancel(true);
            progressDialog.dispose();
        });

        downloadWorker.execute();
        progressDialog.setVisible(true);
    }

    private Map<String, String> parsePeerInfoResponse(String response) {
        Map<String, String> peerInfoMap = new LinkedHashMap<>();
        if (response == null || response.isEmpty()) return peerInfoMap;

        String[] pairs = response.split(",");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                peerInfoMap.put(parts[0], parts[1]);
            }
        }
        return peerInfoMap;
    }

    private String getFileTypeFromName(String fileName) {
        if (fileName == null) return "Unknown";

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            String extension = fileName.substring(lastDot + 1).toLowerCase();
            switch (extension) {
                case "mp3": case "wav": case "flac": case "m4a": return "Audio";
                case "mp4": case "mkv": case "avi": case "mov": return "Video";
                case "png": case "jpg": case "jpeg": case "gif": case "bmp": return "Image";
                case "pdf": case "doc": case "docx": case "txt": case "md": return "Document";
                case "zip": case "rar": case "7z": case "tar": case "gz": return "Archive";
                case "java": case "c": case "cpp": case "py": case "js": return "Code";
                default: return "File";
            }
        }
        return "File";
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

    // Custom cell renderer for status column
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            String status = (String) value;
            if ("Online".equals(status)) {
                setForeground(new Color(34, 197, 94));
                setText("● Online");
            } else {
                setForeground(new Color(239, 68, 68));
                setText("● Offline");
            }

            if (isSelected) {
                setBackground(table.getSelectionBackground());
            } else {
                setBackground(table.getBackground());
            }

            return this;
        }
    }
}