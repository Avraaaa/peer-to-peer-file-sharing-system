import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.swing.SwingWorker;        // ✅ correct worker
import javax.swing.RowFilter;          // ✅ you use RowFilter below

public class FileManagerPanel extends JPanel {
    private JTable filesTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JLabel statusLabel;
    private JButton refreshButton, addFileButton, removeFileButton, openFolderButton;
    private LocalFileHandler fileHandler;
    private String sharedDirectory;
    private JProgressBar scanProgressBar;

    public FileManagerPanel(String sharedDirectory) {
        this.sharedDirectory = sharedDirectory;
        try {
            this.fileHandler = new LocalFileHandler(sharedDirectory);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error initializing file handler: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        initializeComponents();
        setupLayout();
        setupEventListeners();
        refreshFileList();
    }

    private void initializeComponents() {
        // Table setup
        String[] columns = {"Name", "Size", "Type", "Last Modified", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        filesTable = new JTable(tableModel);
        filesTable.setRowSorter(new TableRowSorter<>(tableModel));
        filesTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        filesTable.getTableHeader().setReorderingAllowed(false);

        // Set column widths
        filesTable.getColumnModel().getColumn(0).setPreferredWidth(200); // Name
        filesTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // Size
        filesTable.getColumnModel().getColumn(2).setPreferredWidth(100); // Type
        filesTable.getColumnModel().getColumn(3).setPreferredWidth(120); // Modified
        filesTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Status

        // Search field
        searchField = new JTextField(20);
        searchField.setToolTipText("Search files by name");

        // Buttons
        refreshButton = new JButton("Refresh");
        addFileButton = new JButton("Add Files");
        removeFileButton = new JButton("Remove");
        openFolderButton = new JButton("Open Folder");

        refreshButton.setToolTipText("Refresh file list");
        addFileButton.setToolTipText("Add files to shared directory");
        removeFileButton.setToolTipText("Remove selected files");
        openFolderButton.setToolTipText("Open shared directory in file explorer");

        // Status components
        statusLabel = new JLabel("Ready");
        scanProgressBar = new JProgressBar();
        scanProgressBar.setVisible(false);
        scanProgressBar.setStringPainted(true);
    }

    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel with search and controls
        JPanel topPanel = new JPanel(new BorderLayout(10, 5));

        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Search:"));
        searchPanel.add(searchField);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(refreshButton);
        buttonPanel.add(addFileButton);
        buttonPanel.add(removeFileButton);
        buttonPanel.add(openFolderButton);

        topPanel.add(searchPanel, BorderLayout.WEST);
        topPanel.add(buttonPanel, BorderLayout.EAST);

        // Center panel with table
        JScrollPane scrollPane = new JScrollPane(filesTable);
        scrollPane.setBorder(new TitledBorder("Shared Files - " + sharedDirectory));

        // Bottom panel with status
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(scanProgressBar, BorderLayout.CENTER);
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);
    }

    private void setupEventListeners() {
        // Refresh button
        refreshButton.addActionListener(e -> refreshFileList());

        // Add files button
        addFileButton.addActionListener(e -> addFiles());

        // Remove button
        removeFileButton.addActionListener(e -> removeSelectedFiles());

        // Open folder button
        openFolderButton.addActionListener(e -> openSharedDirectory());

        // Search functionality
        searchField.addActionListener(e -> filterTable());

        // Double-click to open file
        filesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedFile();
                }
            }
        });

        // Context menu
        JPopupMenu contextMenu = createContextMenu();
        filesTable.setComponentPopupMenu(contextMenu);
    }

    private JPopupMenu createContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem openItem = new JMenuItem("Open");
        openItem.addActionListener(e -> openSelectedFile());

        JMenuItem propertiesItem = new JMenuItem("Properties");
        propertiesItem.addActionListener(e -> showFileProperties());

        JMenuItem removeItem = new JMenuItem("Remove from sharing");
        removeItem.addActionListener(e -> removeSelectedFiles());

        menu.add(openItem);
        menu.addSeparator();
        menu.add(propertiesItem);
        menu.addSeparator();
        menu.add(removeItem);

        return menu;
    }

    private void refreshFileList() {
        SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() throws Exception {
                SwingUtilities.invokeLater(() -> {
                    scanProgressBar.setVisible(true);
                    scanProgressBar.setIndeterminate(true);
                    statusLabel.setText("Scanning files...");
                    refreshButton.setEnabled(false);
                });

                // Clear existing data
                SwingUtilities.invokeLater(() -> tableModel.setRowCount(0));

                if (fileHandler != null) {
                    List<SharedFile> sharedFiles = ((LocalFileHandler) fileHandler).listSharedFileObjects();

                    for (int i = 0; i < sharedFiles.size(); i++) {
                        if (isCancelled()) break;

                        SharedFile file = sharedFiles.get(i);
                        String name = file.getName();
                        String size = formatFileSize(file.getSize());
                        String type = getFileType(name);
                        String modified = getLastModified(name);
                        String status = "Shared";

                        Object[] rowData = {name, size, type, modified, status};
                        SwingUtilities.invokeLater(() -> tableModel.addRow(rowData));

                        // Update progress
                        final int progress = (i * 100) / sharedFiles.size();
                        SwingUtilities.invokeLater(() -> {
                            scanProgressBar.setIndeterminate(false);
                            scanProgressBar.setValue(progress);
                        });
                    }
                }

                return null;
            }

            @Override
            protected void done() {
                scanProgressBar.setVisible(false);
                refreshButton.setEnabled(true);
                statusLabel.setText("Found " + tableModel.getRowCount() + " files");
            }
        };

        worker.execute();
    }

    private void addFiles() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        // common file filters
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("All Files", "*"));
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("Documents", "pdf", "doc", "docx", "txt"));
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("Images", "jpg", "jpeg", "png", "gif", "bmp"));
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("Audio", "mp3", "wav", "flac", "m4a"));
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("Video", "mp4", "avi", "mkv", "mov"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            copyFilesToSharedDirectory(selectedFiles);
        }
    }

    private void copyFilesToSharedDirectory(File[] files) {
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                Path sharedPath = Paths.get(sharedDirectory);

                for (int i = 0; i < files.length; i++) {
                    if (isCancelled()) break;

                    File file = files[i];
                    publish("Copying " + file.getName() + "...");

                    try {
                        Path sourcePath = file.toPath();
                        Path targetPath = sharedPath.resolve(file.getName());

                        if (Files.exists(targetPath)) {
                            int choice = JOptionPane.showConfirmDialog(
                                    FileManagerPanel.this,
                                    "File " + file.getName() + " already exists. Replace it?",
                                    "File Exists",
                                    JOptionPane.YES_NO_OPTION
                            );
                            if (choice != JOptionPane.YES_OPTION) {
                                continue;
                            }
                        }

                        Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                    } catch (IOException e) {
                        publish("Error copying " + file.getName() + ": " + e.getMessage());
                    }
                }

                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    statusLabel.setText(message);
                }
            }

            @Override
            protected void done() {
                statusLabel.setText("Copy operation completed");
                refreshFileList();
            }
        };

        worker.execute();
    }

    private void removeSelectedFiles() {
        int[] selectedRows = filesTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select files to remove.",
                    "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "Remove " + selectedRows.length + " file(s) from shared directory?",
                "Confirm Removal",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            for (int i = selectedRows.length - 1; i >= 0; i--) {
                int modelRow = filesTable.convertRowIndexToModel(selectedRows[i]);
                String fileName = (String) tableModel.getValueAt(modelRow, 0);

                try {
                    Path filePath = Paths.get(sharedDirectory, fileName);
                    Files.deleteIfExists(filePath);
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this,
                            "Error removing " + fileName + ": " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
            refreshFileList();
        }
    }

    private void openSelectedFile() {
        int selectedRow = filesTable.getSelectedRow();
        if (selectedRow >= 0) {
            int modelRow = filesTable.convertRowIndexToModel(selectedRow);
            String fileName = (String) tableModel.getValueAt(modelRow, 0);

            try {
                Path filePath = Paths.get(sharedDirectory, fileName);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(filePath.toFile());
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "Error opening file: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openSharedDirectory() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(new File(sharedDirectory));
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Error opening directory: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showFileProperties() {
        int selectedRow = filesTable.getSelectedRow();
        if (selectedRow >= 0) {
            int modelRow = filesTable.convertRowIndexToModel(selectedRow);
            String fileName = (String) tableModel.getValueAt(modelRow, 0);

            try {
                Path filePath = Paths.get(sharedDirectory, fileName);
                SharedFile sharedFile = SharedFileFactory.createSharedFile(filePath);

                JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                        "File Properties", true);
                dialog.setLayout(new BorderLayout());

                JTextArea textArea = new JTextArea(sharedFile.getFileInfo());
                textArea.setEditable(false);
                textArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

                dialog.add(new JScrollPane(textArea), BorderLayout.CENTER);

                JButton closeButton = new JButton("Close");
                closeButton.addActionListener(e -> dialog.dispose());
                JPanel buttonPanel = new JPanel();
                buttonPanel.add(closeButton);
                dialog.add(buttonPanel, BorderLayout.SOUTH);

                dialog.setSize(400, 300);
                dialog.setLocationRelativeTo(this);
                dialog.setVisible(true);

            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Error getting file properties: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void filterTable() {
        String searchText = searchField.getText().toLowerCase();
        TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) filesTable.getRowSorter();

        if (searchText.trim().isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText));
        }

        statusLabel.setText("Showing " + filesTable.getRowCount() + " of " + tableModel.getRowCount() + " files");
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";

        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = (int) (Math.log10(bytes) / Math.log10(1024));
        double size = bytes / Math.pow(1024, unitIndex);

        return String.format("%.1f %s", size, units[unitIndex]);
    }
    // Add this method to FileManagerPanel.java
    public void updateSharedDirectory(String newSharedDirectory) {
        this.sharedDirectory = newSharedDirectory;

        try {
            this.fileHandler = new LocalFileHandler(newSharedDirectory);

            //  title border
            Component[] components = getComponents();
            for (Component comp : components) {
                if (comp instanceof JScrollPane) {
                    JScrollPane scrollPane = (JScrollPane) comp;
                    if (scrollPane.getBorder() instanceof TitledBorder) {
                        TitledBorder border = (TitledBorder) scrollPane.getBorder();
                        border.setTitle("Shared Files - " + newSharedDirectory);
                    }
                }
            }

            // Refresh the file list
            refreshFileList();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Error updating shared directory: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    private String getFileType(String fileName) {
        String extension = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            extension = fileName.substring(lastDot + 1).toUpperCase();
        }

        switch (extension) {
            case "MP3": case "WAV": case "FLAC": case "M4A": return "Audio";
            case "MP4": case "AVI": case "MKV": case "MOV": return "Video";
            case "JPG": case "JPEG": case "PNG": case "GIF": case "BMP": return "Image";
            case "PDF": case "DOC": case "DOCX": case "TXT": return "Document";
            case "ZIP": case "RAR": case "7Z": return "Archive";
            case "JAVA": case "C": case "CPP": case "PY": case "JS": return "Source Code";
            default: return extension.isEmpty() ? "File" : extension + " File";
        }
    }

    private String getLastModified(String fileName) {
        try {
            Path filePath = Paths.get(sharedDirectory, fileName);
            return Files.getLastModifiedTime(filePath).toString();
        } catch (IOException e) {
            return "Unknown";
        }
    }

    // Method to update file list from external source
    public void updateFileList() {
        SwingUtilities.invokeLater(this::refreshFileList);
    }

    // Method to get current file count
    public int getFileCount() {
        return tableModel.getRowCount();
    }
}
