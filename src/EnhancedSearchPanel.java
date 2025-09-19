import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingWorker;

public class EnhancedSearchPanel extends JPanel {
    private User currentUser;
    private Transport serverTransport;
    private PeerClient peerClient;

    // Search components
    private JTextField searchField;
    private JButton searchButton;
    private JComboBox<String> fileTypeFilter;
    private JComboBox<String> sizeFilter;
    private JCheckBox availableOnlyCheck;

    // Results table
    private JTable resultsTable;
    private DefaultTableModel resultsModel;
    private JLabel resultsCountLabel;
    private JButton downloadButton;
    private JButton refreshButton;

    // Search data
    private Map<String, SearchResult> searchResults = new ConcurrentHashMap<>();
    private Timer searchDelayTimer;

    public EnhancedSearchPanel(User user, Transport transport, PeerClient client) {
        this.currentUser = user;
        this.serverTransport = transport;
        this.peerClient = client;

        initializeComponents();
        setupLayout();
        setupEventListeners();
    }

    private void initializeComponents() {
        // Search field with live search
        searchField = new JTextField(30);
        searchField.setToolTipText("Enter file name to search (supports partial matches)");

        searchButton = new JButton("Search");
        searchButton.setBackground(new Color(59, 130, 246));
        searchButton.setForeground(Color.WHITE);

        // Filter components
        String[] fileTypes = {"All Files", "Documents", "Images", "Audio", "Video", "Archives", "Code"};
        fileTypeFilter = new JComboBox<>(fileTypes);
        fileTypeFilter.setToolTipText("Filter by file type");

        String[] sizeOptions = {"Any Size", "< 1 MB", "1-10 MB", "10-100 MB", "> 100 MB"};
        sizeFilter = new JComboBox<>(sizeOptions);
        sizeFilter.setToolTipText("Filter by file size");

        availableOnlyCheck = new JCheckBox("Available only", true);
        availableOnlyCheck.setToolTipText("Show only files from online peers");

        // Results table
        String[] columns = {"File Name", "Size", "Type", "Peers", "Status", "Actions"};
        resultsModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 5; // Only actions column editable
            }
        };

        resultsTable = new JTable(resultsModel);
        resultsTable.setRowSorter(new TableRowSorter<>(resultsModel));
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.setRowHeight(35);

        // Set column widths
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(250); // File Name
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // Size
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(80);  // Type
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(60);  // Peers
        resultsTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Status
        resultsTable.getColumnModel().getColumn(5).setPreferredWidth(100); // Actions

        // Status and control components
        resultsCountLabel = new JLabel("No search performed yet");
        downloadButton = new JButton("Download Selected");
        refreshButton = new JButton("Refresh Results");

        downloadButton.setEnabled(false);
        downloadButton.setBackground(new Color(34, 197, 94));
        downloadButton.setForeground(Color.WHITE);

        refreshButton.setBackground(new Color(107, 114, 128));
        refreshButton.setForeground(Color.WHITE);
    }

    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Header
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Search controls
        JPanel searchPanel = createSearchPanel();
        add(searchPanel, BorderLayout.CENTER);

        // Results area would go in CENTER of main layout
        JPanel resultsPanel = createResultsPanel();
        add(resultsPanel, BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        JLabel titleLabel = new JLabel("Search Network Files");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(new Color(30, 41, 59));

        JLabel subtitleLabel = new JLabel("Find and download files shared by peers on the network");
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        subtitleLabel.setForeground(new Color(107, 114, 128));

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(5));
        titlePanel.add(subtitleLabel);

        panel.add(titlePanel, BorderLayout.WEST);
        return panel;
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new TitledBorder("Search & Filters"));

        // Main search bar
        JPanel searchBarPanel = new JPanel(new BorderLayout(10, 0));
        searchBarPanel.add(searchField, BorderLayout.CENTER);
        searchBarPanel.add(searchButton, BorderLayout.EAST);

        // Filters panel
        JPanel filtersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filtersPanel.add(new JLabel("Type:"));
        filtersPanel.add(fileTypeFilter);
        filtersPanel.add(Box.createHorizontalStrut(10));
        filtersPanel.add(new JLabel("Size:"));
        filtersPanel.add(sizeFilter);
        filtersPanel.add(Box.createHorizontalStrut(10));
        filtersPanel.add(availableOnlyCheck);

        panel.add(searchBarPanel, BorderLayout.NORTH);
        panel.add(filtersPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new TitledBorder("Search Results"));

        // Results header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(resultsCountLabel, BorderLayout.WEST);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonsPanel.add(refreshButton);
        buttonsPanel.add(downloadButton);
        headerPanel.add(buttonsPanel, BorderLayout.EAST);

        // Results table
        JScrollPane scrollPane = new JScrollPane(resultsTable);
        scrollPane.setPreferredSize(new Dimension(0, 300));

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void setupEventListeners() {
        // Live search with delay
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performSearch();
                } else {
                    // Delay search to avoid too many requests
                    if (searchDelayTimer != null) {
                        searchDelayTimer.stop();
                    }
                    searchDelayTimer = new Timer(500, evt -> {
                        if (!searchField.getText().trim().isEmpty()) {
                            performSearch();
                        }
                    });
                    searchDelayTimer.setRepeats(false);
                    searchDelayTimer.start();
                }
            }
        });

        searchButton.addActionListener(e -> performSearch());

        // Filter change listeners
        fileTypeFilter.addActionListener(e -> applyFilters());
        sizeFilter.addActionListener(e -> applyFilters());
        availableOnlyCheck.addActionListener(e -> applyFilters());

        // Table selection
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                downloadButton.setEnabled(resultsTable.getSelectedRow() >= 0);
            }
        });

        // Button actions
        downloadButton.addActionListener(e -> downloadSelected());
        refreshButton.addActionListener(e -> refreshResults());

        // Double-click to download
        resultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && resultsTable.getSelectedRow() >= 0) {
                    downloadSelected();
                }
            }
        });
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            resultsCountLabel.setText("Enter a search term");
            return;
        }

        resultsCountLabel.setText("Searching...");
        searchButton.setEnabled(false);

        SwingWorker<Map<String, SearchResult>, Void> searchWorker = new SwingWorker<Map<String, SearchResult>, Void>() {
            @Override
            protected Map<String, SearchResult> doInBackground() throws Exception {
                Map<String, SearchResult> results = new HashMap<>();

                // Search for each word in the query
                String[] searchTerms = query.toLowerCase().split("\\s+");

                for (String term : searchTerms) {
                    try {
                        serverTransport.sendLine("SEARCH " + term);
                        String response = serverTransport.readLine();

                        if (response != null && !response.isEmpty()) {
                            Map<String, String> peers = parsePeerResponse(response);

                            for (Map.Entry<String, String> entry : peers.entrySet()) {
                                String peerName = entry.getKey();
                                String peerAddress = entry.getValue();

                                // Get file list from peer to find matching files
                                List<String> peerFiles = getFilesFromPeer(peerAddress);
                                for (String fileName : peerFiles) {
                                    if (fileName.toLowerCase().contains(term) ||
                                            term.toLowerCase().contains(fileName.toLowerCase())) {

                                        SearchResult result = results.get(fileName);
                                        if (result == null) {
                                            result = new SearchResult(fileName);
                                            results.put(fileName, result);
                                        }
                                        result.addPeer(peerName, peerAddress);
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        // Continue with other search terms
                    }
                }

                return results;
            }

            @Override
            protected void done() {
                try {
                    searchResults = get();
                    displayResults();
                } catch (Exception e) {
                    resultsCountLabel.setText("Search failed: " + e.getMessage());
                } finally {
                    searchButton.setEnabled(true);
                }
            }
        };

        searchWorker.execute();
    }

    private void displayResults() {
        resultsModel.setRowCount(0);

        Collection<SearchResult> filteredResults = applyFiltersToResults(searchResults.values());

        for (SearchResult result : filteredResults) {
            String fileName = result.fileName;
            String size = result.size > 0 ? formatFileSize(result.size) : "Unknown";
            String type = getFileTypeFromName(fileName);
            String peerCount = result.peers.size() + " peer(s)";
            String status = result.isAvailable() ? "Available" : "Offline";

            Object[] rowData = {fileName, size, type, peerCount, status, "Download"};
            resultsModel.addRow(rowData);
        }

        resultsCountLabel.setText("Found " + filteredResults.size() + " file(s)");
        applyFilters();
    }

    private Collection<SearchResult> applyFiltersToResults(Collection<SearchResult> results) {
        List<SearchResult> filtered = new ArrayList<>(results);

        // Filter by availability
        if (availableOnlyCheck.isSelected()) {
            filtered.removeIf(r -> !r.isAvailable());
        }

        // Filter by file type
        String selectedType = (String) fileTypeFilter.getSelectedItem();
        if (!"All Files".equals(selectedType)) {
            filtered.removeIf(r -> !matchesFileType(r.fileName, selectedType));
        }

        // Filter by size
        String selectedSize = (String) sizeFilter.getSelectedItem();
        if (!"Any Size".equals(selectedSize)) {
            filtered.removeIf(r -> !matchesSize(r.size, selectedSize));
        }

        return filtered;
    }

    private boolean matchesFileType(String fileName, String typeFilter) {
        String type = getFileTypeFromName(fileName);
        return type.equalsIgnoreCase(typeFilter);
    }

    private boolean matchesSize(long fileSize, String sizeFilter) {
        if (fileSize <= 0) return false;

        switch (sizeFilter) {
            case "< 1 MB": return fileSize < 1024 * 1024;
            case "1-10 MB": return fileSize >= 1024 * 1024 && fileSize < 10 * 1024 * 1024;
            case "10-100 MB": return fileSize >= 10 * 1024 * 1024 && fileSize < 100 * 1024 * 1024;
            case "> 100 MB": return fileSize >= 100 * 1024 * 1024;
            default: return true;
        }
    }

    private void applyFilters() {
        if (resultsModel.getRowCount() > 0) {
            displayResults();
        }
    }

    private void downloadSelected() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow < 0) return;

        int modelRow = resultsTable.convertRowIndexToModel(selectedRow);
        String fileName = (String) resultsModel.getValueAt(modelRow, 0);

        SearchResult result = searchResults.get(fileName);
        if (result == null || result.peers.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No peers available for this file.",
                    "Download Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Show peer selection dialog if multiple peers
        PeerInfo selectedPeer;
        if (result.peers.size() == 1) {
            selectedPeer = result.peers.get(0);
        } else {
            selectedPeer = showPeerSelectionDialog(result);
            if (selectedPeer == null) return;
        }

        startDownload(fileName, selectedPeer);
    }

    private PeerInfo showPeerSelectionDialog(SearchResult result) {
        PeerInfo[] peers = result.peers.toArray(new PeerInfo[0]);

        return (PeerInfo) JOptionPane.showInputDialog(
                this,
                "Select peer to download from:",
                "Choose Peer",
                JOptionPane.QUESTION_MESSAGE,
                null,
                peers,
                peers[0]
        );
    }

    private void startDownload(String fileName, PeerInfo peer) {
        // Show download progress dialog
        JDialog progressDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Downloading", true);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Starting download...");

        JButton cancelButton = new JButton("Cancel");

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(new JLabel("Downloading: " + fileName), BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(cancelButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        progressDialog.add(panel);
        progressDialog.setSize(400, 150);
        progressDialog.setLocationRelativeTo(this);

        // Start download in background
        SwingWorker<Boolean, String> downloadWorker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    publish("Connecting to peer...");

                    // Use peer client's download functionality
                    peerClient.getDownloadStrategy().download(peer.address, fileName);

                    publish("Download completed!");
                    return true;
                } catch (Exception e) {
                    publish("Download failed: " + e.getMessage());
                    return false;
                }
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    progressBar.setString(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    boolean success = get();
                    if (success) {
                        JOptionPane.showMessageDialog(EnhancedSearchPanel.this,
                                "Download completed successfully!",
                                "Download Complete",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(EnhancedSearchPanel.this,
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

    private void refreshResults() {
        if (!searchField.getText().trim().isEmpty()) {
            performSearch();
        }
    }

    // Helper methods
    private Map<String, String> parsePeerResponse(String response) {
        Map<String, String> peers = new HashMap<>();
        if (response == null || response.isEmpty()) return peers;

        String[] pairs = response.split(",");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                peers.put(parts[0], parts[1]);
            }
        }
        return peers;
    }

    private List<String> getFilesFromPeer(String peerAddress) {
        try {
            String[] parts = peerAddress.split(":");
            String host = parts[0];
            int udpPort = Integer.parseInt(parts[1]) + 1;

            try (UDPTransport transport = new UDPTransport(host, udpPort)) {
                transport.setSoTimeout(2000);
                transport.sendLine("LIST_FILES");
                String response = transport.readLine();

                if (response != null && !response.isEmpty()) {
                    return Arrays.asList(response.split(","));
                }
            }
        } catch (Exception e) {
            // Return empty list on error
        }
        return new ArrayList<>();
    }

    private String getFileTypeFromName(String fileName) {
        if (fileName == null) return "Unknown";

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            String extension = fileName.substring(lastDot + 1).toLowerCase();
            switch (extension) {
                case "pdf": case "doc": case "docx": case "txt": case "rtf": return "Documents";
                case "jpg": case "jpeg": case "png": case "gif": case "bmp": return "Images";
                case "mp3": case "wav": case "flac": case "m4a": case "aac": return "Audio";
                case "mp4": case "avi": case "mkv": case "mov": case "wmv": return "Video";
                case "zip": case "rar": case "7z": case "tar": case "gz": return "Archives";
                case "java": case "py": case "cpp": case "js": case "html": return "Code";
                default: return "Other";
            }
        }
        return "Unknown";
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";

        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = (int) (Math.log10(bytes) / Math.log10(1024));
        double size = bytes / Math.pow(1024, unitIndex);

        return String.format("%.1f %s", size, units[unitIndex]);
    }

    // Helper classes
    private static class SearchResult {
        String fileName;
        long size = 0;
        List<PeerInfo> peers = new ArrayList<>();

        SearchResult(String fileName) {
            this.fileName = fileName;
        }

        void addPeer(String name, String address) {
            peers.add(new PeerInfo(name, address));
        }

        boolean isAvailable() {
            return !peers.isEmpty();
        }
    }

    private static class PeerInfo {
        String name;
        String address;

        PeerInfo(String name, String address) {
            this.name = name;
            this.address = address;
        }

        @Override
        public String toString() {
            return name + " (" + address + ")";
        }
    }
}