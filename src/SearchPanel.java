import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.Map;
import java.util.LinkedHashMap;

public class SearchPanel extends JPanel {
    private User currentUser;
    private Transport serverTransport;
    private PeerClient peerClient;

    // Search components
    private JTextField searchField;
    private JButton searchButton;
    private JComboBox<String> filterComboBox;
    private JCheckBox advancedSearchCheck;

    // Results components
    private JTable resultsTable;
    private DefaultTableModel tableModel;
    private JLabel resultsLabel;
    private JProgressBar searchProgress;

    // Advanced search components
    private JPanel advancedPanel;
    private JTextField minSizeField;
    private JTextField maxSizeField;
    private JComboBox<String> fileTypeComboBox;

    // Search history
    private DefaultComboBoxModel<String> historyModel;
    private JComboBox<String> historyComboBox;

    // Current search results
    private Map<String, String> currentResults = new LinkedHashMap<>();

    public SearchPanel(User user, Transport transport, PeerClient client) {
        this.currentUser = user;
        this.serverTransport = transport;
        this.peerClient = client;

        initializePanel();
        createComponents();
        setupLayout();
        setupEventHandlers();
    }

    private void initializePanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(248, 250, 252));
        setBorder(new EmptyBorder(20, 30, 20, 30));
    }

    private void createComponents() {
        createHeader();
        createSearchSection();
        createAdvancedSearch();
        createResultsSection();
    }

    private void createHeader() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(248, 250, 252));
        headerPanel.setBorder(new EmptyBorder(0, 0, 25, 0));

        JLabel titleLabel = new JLabel("Search Files");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(new Color(30, 41, 59));

        JLabel subtitleLabel = new JLabel("Find files shared by peers on the network");
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        subtitleLabel.setForeground(new Color(100, 116, 139));

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setBackground(new Color(248, 250, 252));
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(5));
        titlePanel.add(subtitleLabel);

        headerPanel.add(titlePanel, BorderLayout.WEST);

        add(headerPanel, BorderLayout.NORTH);
    }

    private JPanel createSearchSection() {
        JPanel searchPanel = new JPanel();
        searchPanel.setLayout(new BoxLayout(searchPanel, BoxLayout.Y_AXIS));
        searchPanel.setBackground(Color.WHITE);
        searchPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
                new EmptyBorder(20, 25, 20, 25)
        ));

        // Search input row
        JPanel searchInputPanel = new JPanel(new BorderLayout(10, 0));
        searchInputPanel.setBackground(Color.WHITE);

        // Search field with placeholder effect
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(0, 40));
        searchField.setFont(new Font("Arial", Font.PLAIN, 14));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(209, 213, 219), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        searchField.setToolTipText("Enter filename to search for");

        // Search button
        searchButton = new JButton("Search");
        searchButton.setPreferredSize(new Dimension(100, 40));
        searchButton.setFont(new Font("Arial", Font.BOLD, 14));
        searchButton.setBackground(new Color(59, 130, 246));
        searchButton.setForeground(Color.WHITE);
        searchButton.setBorder(BorderFactory.createEmptyBorder());
        searchButton.setFocusPainted(false);
        searchButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        searchInputPanel.add(searchField, BorderLayout.CENTER);
        searchInputPanel.add(searchButton, BorderLayout.EAST);

        // Filters row
        JPanel filtersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filtersPanel.setBackground(Color.WHITE);
        filtersPanel.setBorder(new EmptyBorder(15, 0, 0, 0));

        JLabel filterLabel = new JLabel("Filter by:");
        filterLabel.setFont(new Font("Arial", Font.BOLD, 12));
        filterLabel.setForeground(new Color(107, 114, 128));

        String[] filterOptions = {"All Files", "Audio", "Video", "Images", "Documents", "Archives"};
        filterComboBox = new JComboBox<>(filterOptions);
        filterComboBox.setPreferredSize(new Dimension(120, 30));
        filterComboBox.setFont(new Font("Arial", Font.PLAIN, 12));

        advancedSearchCheck = new JCheckBox("Advanced Search");
        advancedSearchCheck.setBackground(Color.WHITE);
        advancedSearchCheck.setFont(new Font("Arial", Font.PLAIN, 12));
        advancedSearchCheck.setForeground(new Color(107, 114, 128));

        filtersPanel.add(filterLabel);
        filtersPanel.add(Box.createHorizontalStrut(10));
        filtersPanel.add(filterComboBox);
        filtersPanel.add(Box.createHorizontalStrut(20));
        filtersPanel.add(advancedSearchCheck);

        // Search history
        JPanel historyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        historyPanel.setBackground(Color.WHITE);
        historyPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        JLabel historyLabel = new JLabel("Recent searches:");
        historyLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        historyLabel.setForeground(new Color(107, 114, 128));

        historyModel = new DefaultComboBoxModel<>();
        historyComboBox = new JComboBox<>(historyModel);
        historyComboBox.setPreferredSize(new Dimension(200, 25));
        historyComboBox.setFont(new Font("Arial", Font.PLAIN, 11));

        historyPanel.add(historyLabel);
        historyPanel.add(Box.createHorizontalStrut(10));
        historyPanel.add(historyComboBox);

        searchPanel.add(searchInputPanel);
        searchPanel.add(filtersPanel);
        searchPanel.add(historyPanel);

        return searchPanel;
    }

    private void createAdvancedSearch() {
        advancedPanel = new JPanel();
        advancedPanel.setLayout(new BoxLayout(advancedPanel, BoxLayout.Y_AXIS));
        advancedPanel.setBackground(new Color(249, 250, 251));
        advancedPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
                new EmptyBorder(15, 25, 15, 25)
        ));
        advancedPanel.setVisible(false);

        JLabel advancedTitle = new JLabel("Advanced Search Options");
        advancedTitle.setFont(new Font("Arial", Font.BOLD, 14));
        advancedTitle.setForeground(new Color(30, 41, 59));

        // Size filters
        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        sizePanel.setBackground(new Color(249, 250, 251));

        JLabel sizeLabel = new JLabel("File size:");
        sizeLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        minSizeField = new JTextField(8);
        minSizeField.setToolTipText("Minimum size (MB)");
        maxSizeField = new JTextField(8);
        maxSizeField.setToolTipText("Maximum size (MB)");

        sizePanel.add(sizeLabel);
        sizePanel.add(Box.createHorizontalStrut(10));
        sizePanel.add(new JLabel("Min:"));
        sizePanel.add(minSizeField);
        sizePanel.add(Box.createHorizontalStrut(10));
        sizePanel.add(new JLabel("Max:"));
        sizePanel.add(maxSizeField);
        sizePanel.add(new JLabel("MB"));

        // File type
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        typePanel.setBackground(new Color(249, 250, 251));

        JLabel typeLabel = new JLabel("File type:");
        typeLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        String[] fileTypes = {"Any", ".pdf", ".doc", ".docx", ".txt", ".mp3", ".mp4", ".jpg", ".png", ".zip"};
        fileTypeComboBox = new JComboBox<>(fileTypes);

        typePanel.add(typeLabel);
        typePanel.add(Box.createHorizontalStrut(10));
        typePanel.add(fileTypeComboBox);

        advancedPanel.add(advancedTitle);
        advancedPanel.add(Box.createVerticalStrut(10));
        advancedPanel.add(sizePanel);
        advancedPanel.add(typePanel);
    }

    private void createResultsSection() {
        JPanel resultsPanel = new JPanel(new BorderLayout());
        resultsPanel.setBackground(Color.WHITE);
        resultsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
                new EmptyBorder(20, 25, 20, 25)
        ));

        // Results header
        JPanel resultsHeader = new JPanel(new BorderLayout());
        resultsHeader.setBackground(Color.WHITE);
        resultsHeader.setBorder(new EmptyBorder(0, 0, 15, 0));

        resultsLabel = new JLabel("Search results will appear here");
        resultsLabel.setFont(new Font("Arial", Font.BOLD, 16));
        resultsLabel.setForeground(new Color(30, 41, 59));

        searchProgress = new JProgressBar();
        searchProgress.setIndeterminate(true);
        searchProgress.setVisible(false);
        searchProgress.setPreferredSize(new Dimension(0, 4));

        resultsHeader.add(resultsLabel, BorderLayout.WEST);
        resultsHeader.add(searchProgress, BorderLayout.SOUTH);

        // Results table
        String[] columnNames = {"File Name", "Size", "Type", "Peer", "Actions"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4; // Only actions column is editable
            }
        };

        resultsTable = new JTable(tableModel);
        resultsTable.setFont(new Font("Arial", Font.PLAIN, 12));
        resultsTable.setRowHeight(35);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        resultsTable.getTableHeader().setBackground(new Color(248, 250, 252));
        resultsTable.setGridColor(new Color(229, 231, 235));

        // Set column widths
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        resultsTable.getColumnModel().getColumn(4).setPreferredWidth(100);

        // Custom button renderer for actions column
        resultsTable.getColumnModel().getColumn(4).setCellRenderer(new ButtonRenderer());
        resultsTable.getColumnModel().getColumn(4).setCellEditor(new ButtonEditor());

        JScrollPane scrollPane = new JScrollPane(resultsTable);
        scrollPane.setPreferredSize(new Dimension(0, 300));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(229, 231, 235), 1));

        resultsPanel.add(resultsHeader, BorderLayout.NORTH);
        resultsPanel.add(scrollPane, BorderLayout.CENTER);

        add(resultsPanel, BorderLayout.SOUTH);
    }

    private void setupLayout() {
        // Create the main content panel with proper layout
        JPanel mainContent = new JPanel(new BorderLayout(0, 15));
        mainContent.setBackground(new Color(248, 250, 252));

        // Create the search section panel
        JPanel searchSection = createSearchSection();

        // Create a container for search section and advanced panel
        JPanel searchContainer = new JPanel(new BorderLayout(0, 15));
        searchContainer.setBackground(new Color(248, 250, 252));
        searchContainer.add(searchSection, BorderLayout.NORTH);
        searchContainer.add(advancedPanel, BorderLayout.CENTER);

        // remove the old panels first
        removeAll();

        // Re-add header
        createHeader();

        // Add the search container
        add(searchContainer, BorderLayout.CENTER);
    }

    private void setupEventHandlers() {
        // Search button action
        searchButton.addActionListener(this::performSearch);

        // Enter key in search field
        searchField.addKeyListener(new KeyListener() {
            public void keyTyped(KeyEvent e) {}
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performSearch(null);
                }
            }
            public void keyReleased(KeyEvent e) {}
        });

        // Advanced search toggle
        advancedSearchCheck.addActionListener(e -> {
            advancedPanel.setVisible(advancedSearchCheck.isSelected());
            revalidate();
            repaint();
        });

        // History selection
        historyComboBox.addActionListener(e -> {
            String selected = (String) historyComboBox.getSelectedItem();
            if (selected != null && !selected.isEmpty()) {
                searchField.setText(selected);
            }
        });

        // Filter change
        filterComboBox.addActionListener(e -> {
            if (!searchField.getText().trim().isEmpty()) {
                performSearch(null);
            }
        });
    }

    private void performSearch(ActionEvent e) {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a filename to search for", "Search Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Add to history
        addToSearchHistory(searchTerm);

        // Show progress
        searchProgress.setVisible(true);
        searchButton.setEnabled(false);
        resultsLabel.setText("Searching for \"" + searchTerm + "\"...");

        // Clear previous results
        tableModel.setRowCount(0);
        currentResults.clear();

        // Perform search in background
        SwingWorker<Map<String, String>, Void> searchWorker = new SwingWorker<Map<String, String>, Void>() {
            @Override
            protected Map<String, String> doInBackground() throws Exception {
                try {
                    serverTransport.sendLine("SEARCH " + searchTerm);
                    String response = serverTransport.readLine();
                    return parsePeerInfoResponse(response);
                } catch (IOException ex) {
                    throw ex;
                }
            }

            @Override
            protected void done() {
                try {
                    Map<String, String> searchResults = get();
                    displaySearchResults(searchTerm, searchResults);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(SearchPanel.this,
                            "Search failed: " + ex.getMessage(),
                            "Search Error",
                            JOptionPane.ERROR_MESSAGE);
                    resultsLabel.setText("Search failed");
                } finally {
                    searchProgress.setVisible(false);
                    searchButton.setEnabled(true);
                }
            }
        };

        searchWorker.execute();
    }

    private void displaySearchResults(String searchTerm, Map<String, String> results) {
        currentResults = results;

        if (results.isEmpty()) {
            resultsLabel.setText("No files found matching \"" + searchTerm + "\"");
            return;
        }

        resultsLabel.setText("Found " + results.size() + " peer(s) with \"" + searchTerm + "\"");

        // Verify which peers actually have the file
        SwingWorker<Void, Object[]> verificationWorker = new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (Map.Entry<String, String> entry : results.entrySet()) {
                    try {
                        long fileSize = getPeerFileSize(entry.getValue(), searchTerm);
                        if (fileSize > -1) {
                            String fileType = getFileTypeFromName(searchTerm);
                            Object[] rowData = {
                                    searchTerm,
                                    formatFileSize(fileSize),
                                    fileType,
                                    entry.getKey(),
                                    "Download"
                            };
                            publish(rowData);
                        }
                    } catch (Exception e) {
                        // Skip peers that don't respond
                    }
                }
                return null;
            }

            @Override
            protected void process(java.util.List<Object[]> chunks) {
                for (Object[] rowData : chunks) {
                    tableModel.addRow(rowData);
                }
            }

            @Override
            protected void done() {
                if (tableModel.getRowCount() == 0) {
                    resultsLabel.setText("No accessible peers found with \"" + searchTerm + "\"");
                }
            }
        };

        verificationWorker.execute();
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

    private void addToSearchHistory(String searchTerm) {
        // Remove if already exists
        historyModel.removeElement(searchTerm);

        // Add to beginning
        historyModel.insertElementAt(searchTerm, 0);

        // Limit history size
        while (historyModel.getSize() > 10) {
            historyModel.removeElementAt(historyModel.getSize() - 1);
        }

        historyComboBox.setSelectedIndex(0);
    }

    // Custom button renderer for actions column
    private class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
            setFont(new Font("Arial", Font.BOLD, 11));
            setBackground(new Color(34, 197, 94));
            setForeground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder());
            setFocusPainted(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            setText((value == null) ? "Download" : value.toString());
            return this;
        }
    }

    // Custom button editor for actions column
    private class ButtonEditor extends DefaultCellEditor {
        private JButton button;
        private String label;
        private boolean isPushed;
        private int currentRow;

        public ButtonEditor() {
            super(new JCheckBox());
            button = new JButton();
            button.setOpaque(true);
            button.setFont(new Font("Arial", Font.BOLD, 11));
            button.setBackground(new Color(34, 197, 94));
            button.setForeground(Color.WHITE);
            button.setBorder(BorderFactory.createEmptyBorder());
            button.setFocusPainted(false);

            button.addActionListener(e -> fireEditingStopped());
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            label = (value == null) ? "Download" : value.toString();
            button.setText(label);
            isPushed = true;
            currentRow = row;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            if (isPushed) {
                performDownload();
            }
            isPushed = false;
            return label;
        }

        @Override
        public boolean stopCellEditing() {
            isPushed = false;
            return super.stopCellEditing();
        }

        private void performDownload() {
            if (currentRow >= 0 && currentRow < tableModel.getRowCount()) {
                String fileName = (String) tableModel.getValueAt(currentRow, 0);
                String peerName = (String) tableModel.getValueAt(currentRow, 3);
                String peerAddress = currentResults.get(peerName);

                if (peerAddress != null) {
                    initiateDownload(fileName, peerName, peerAddress);
                }
            }
        }
    }

    private void initiateDownload(String fileName, String peerName, String peerAddress) {
        int option = JOptionPane.showConfirmDialog(
                this,
                "Download \"" + fileName + "\" from " + peerName + "?",
                "Confirm Download",
                JOptionPane.YES_NO_OPTION
        );

        if (option == JOptionPane.YES_OPTION) {
            // Create progress dialog
            JDialog progressDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                    "Downloading", true);
            progressDialog.setSize(400, 150);
            progressDialog.setLocationRelativeTo(this);

            JPanel progressPanel = new JPanel(new BorderLayout());
            progressPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

            JLabel progressLabel = new JLabel("Downloading " + fileName + "...");
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
            progressPanel.add(Box.createVerticalStrut(15), BorderLayout.CENTER);
            progressPanel.add(progressBar, BorderLayout.CENTER);
            progressPanel.add(Box.createVerticalStrut(15));

            JPanel buttonPanel = new JPanel(new FlowLayout());
            buttonPanel.add(cancelButton);
            progressPanel.add(buttonPanel, BorderLayout.SOUTH);

            progressDialog.add(progressPanel);

            // Start download in background
            SwingWorker<Boolean, String> downloadWorker = new SwingWorker<Boolean, String>() {
                @Override
                protected Boolean doInBackground() throws Exception {
                    try {
                        publish("Starting download...");

                        // This would integrate with your existing download strategy
                        // For now, simulate download progress
                        for (int i = 0; i <= 100; i += 10) {
                            if (isCancelled()) break;
                            Thread.sleep(200);
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
                            JOptionPane.showMessageDialog(SearchPanel.this,
                                    "Download completed successfully!",
                                    "Download Complete",
                                    JOptionPane.INFORMATION_MESSAGE);
                        }
                    } catch (Exception e) {
                        progressDialog.dispose();
                        JOptionPane.showMessageDialog(SearchPanel.this,
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
    }
}