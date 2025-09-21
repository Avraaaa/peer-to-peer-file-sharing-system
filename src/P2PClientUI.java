import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class P2PClientUI extends JFrame {
    private Transport serverTransport;
    private User loggedInUser;
    private FileHandler fileHandler;
    private DownloadStrategy downloadStrategy;
    private String localSharedDirectory;
    private PeerClient peerClient;
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private ClientConfigurationService configService;

    // UI Components
    private JPanel mainPanel;
    private CardLayout cardLayout;

    public P2PClientUI() {
        initializeUI();
        connectToServer();
    }

    private void initializeUI() {
        setTitle("Peer To Peer File Sharing!");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
                System.exit(0);
            }
        });

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        add(mainPanel);

        showLoginPage();
    }

    private void connectToServer() {
        try {
            serverTransport = new TCPTransport("localhost", 9090);
            configService = new ClientConfigurationService("client_config.csv");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Could not connect to server: " + e.getMessage(),
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void showLoginPage() {
        JPanel loginPanel = new JPanel(new GridBagLayout());
        loginPanel.setBackground(new Color(245, 245, 245));
        GridBagConstraints gbc = new GridBagConstraints();

        // Title
        JLabel titleLabel = new JLabel("Welcome to PeerShare!");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(new Color(51, 51, 51));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.insets = new Insets(20, 20, 30, 20);
        loginPanel.add(titleLabel, gbc);

        // Login Button
        JButton loginBtn = new JButton("Login");
        loginBtn.setPreferredSize(new Dimension(200, 40));
        loginBtn.setBackground(new Color(70, 130, 180));
        loginBtn.setForeground(Color.blue);
        loginBtn.setFont(new Font("Arial", Font.BOLD, 14));
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.insets = new Insets(10, 20, 10, 10);
        loginPanel.add(loginBtn, gbc);

        // Sign Up Button
        JButton signupBtn = new JButton("Sign Up");
        signupBtn.setPreferredSize(new Dimension(200, 40));
        signupBtn.setBackground(new Color(60, 179, 113));
        signupBtn.setForeground(Color.green);
        signupBtn.setFont(new Font("Arial", Font.BOLD, 14));
        gbc.gridx = 1; gbc.gridy = 1; gbc.insets = new Insets(10, 10, 10, 20);
        loginPanel.add(signupBtn, gbc);

        // Exit Button
        JButton exitBtn = new JButton("Exit");
        exitBtn.setPreferredSize(new Dimension(200, 40));
        exitBtn.setBackground(new Color(220, 20, 60));
        exitBtn.setForeground(Color.red);
        exitBtn.setFont(new Font("Arial", Font.BOLD, 14));
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.insets = new Insets(20, 20, 20, 20);
        loginPanel.add(exitBtn, gbc);

        // Event Listeners
        loginBtn.addActionListener(e -> showLoginDialog());
        signupBtn.addActionListener(e -> showSignupDialog());
        exitBtn.addActionListener(e -> {
            cleanup();
            System.exit(0);
        });

        mainPanel.add(loginPanel, "LOGIN");
        cardLayout.show(mainPanel, "LOGIN");
    }

    private void showLoginDialog() {
        JDialog dialog = new JDialog(this, "Login", true);
        dialog.setSize(350, 200);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JTextField usernameField = new JTextField(20);
        JPasswordField passwordField = new JPasswordField(20);

        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(10, 10, 5, 5);
        dialog.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.insets = new Insets(10, 5, 5, 10);
        dialog.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.insets = new Insets(5, 10, 10, 5);
        dialog.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.insets = new Insets(5, 5, 10, 10);
        dialog.add(passwordField, gbc);

        JButton loginBtn = new JButton("Login");
        loginBtn.setForeground(Color.blue);
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setForeground(Color.black);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(loginBtn);
        buttonPanel.add(cancelBtn);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.insets = new Insets(10, 10, 10, 10);
        dialog.add(buttonPanel, gbc);

        loginBtn.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please fill in all fields");
                return;
            }

            try {
                serverTransport.sendLine("LOGIN " + username + " " + password);
                String response = serverTransport.readLine();

                if (response != null && response.startsWith("LOGIN_SUCCESS")) {
                    String[] payload = response.substring(14).split(";", 4);
                    String u = payload[0];
                    boolean isAdmin = Boolean.parseBoolean(payload[1]);

                    DownloadStats dStats = new DownloadStats();
                    dStats.fromCsvString(payload[2]);
                    UploadStats uStats = new UploadStats();
                    uStats.fromCsvString(payload[3]);

                    localSharedDirectory = configService.getSharedDirectory(u);

                    if (localSharedDirectory == null) {
                        localSharedDirectory = selectSharedDirectory(u);
                        if (localSharedDirectory == null) {
                            JOptionPane.showMessageDialog(dialog, "Shared directory is required");
                            return;
                        }
                    }

                    if (isAdmin) {
                        loggedInUser = new AdminUser("", dStats, uStats);
                    } else {
                        loggedInUser = new RegularUser(u, "", dStats, uStats);
                    }

                    dialog.dispose();
                    initializePeerClient();
                    showDashboard();

                } else {
                    String errorMsg = response != null ? response.replace("LOGIN_FAIL ", "") : "No response from server";
                    JOptionPane.showMessageDialog(dialog, "Login failed: " + errorMsg);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dialog, "Connection error: " + ex.getMessage());
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    private void showSignupDialog() {
        JDialog dialog = new JDialog(this, "Sign Up", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JTextField usernameField = new JTextField(20);
        JPasswordField passwordField = new JPasswordField(20);
        JPasswordField confirmPasswordField = new JPasswordField(20);

        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(10, 10, 5, 5);
        dialog.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.insets = new Insets(10, 5, 5, 10);
        dialog.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.insets = new Insets(5, 10, 5, 5);
        dialog.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.insets = new Insets(5, 5, 5, 10);
        dialog.add(passwordField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.insets = new Insets(5, 10, 10, 5);
        dialog.add(new JLabel("Confirm Password:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.insets = new Insets(5, 5, 10, 10);
        dialog.add(confirmPasswordField, gbc);

        JButton signupBtn = new JButton("Sign Up");
        signupBtn.setForeground(Color.green);
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setForeground(Color.black);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(signupBtn);
        buttonPanel.add(cancelBtn);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.insets = new Insets(10, 10, 10, 10);
        dialog.add(buttonPanel, gbc);

        signupBtn.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            String confirmPassword = new String(confirmPasswordField.getPassword());

            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please fill in all fields");
                return;
            }

            if (!password.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(dialog, "Passwords do not match");
                return;
            }

            try {
                serverTransport.sendLine("SIGNUP " + username + " " + password);
                String response = serverTransport.readLine();

                if ("SIGNUP_SUCCESS".equals(response)) {
                    // Ask for shared directory
                    String sharedDir = selectSharedDirectory(username);
                    if (sharedDir != null) {
                        configService.saveSharedDirectory(username, sharedDir);
                        JOptionPane.showMessageDialog(dialog,
                                "Account created successfully!\nShared directory set to: " + sharedDir +
                                        "\nPlease login to continue.");
                    }
                    dialog.dispose();
                } else {
                    String errorMsg = response != null ? response.replace("SIGNUP_FAIL ", "") : "No response from server";
                    JOptionPane.showMessageDialog(dialog, "Signup failed: " + errorMsg);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dialog, "Connection error: " + ex.getMessage());
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    private String selectSharedDirectory(String username) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select Shared Directory for " + username);

        // Suggest a default directory
        String defaultDir = System.getProperty("user.dir") + File.separator + username + "_files";
        fileChooser.setSelectedFile(new File(defaultDir));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = fileChooser.getSelectedFile();
            try {
                if (!selectedDir.exists()) {
                    selectedDir.mkdirs();
                }
                configService.saveSharedDirectory(username, selectedDir.getAbsolutePath());
                return selectedDir.getAbsolutePath();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error saving directory configuration: " + e.getMessage());
            }
        }
        return null;
    }
    // In initializePeerClient() method, add this at the end:
    private void initializePeerClient() {
        try {
            Path userSharedPath = Paths.get(localSharedDirectory);
            if (!Files.exists(userSharedPath)) {
                Files.createDirectories(userSharedPath);
            }

            System.out.println("DEBUG: Initializing peer client...");
            System.out.println("DEBUG: Local shared directory: " + localSharedDirectory);

            // Check what files are actually in the directory
            try {
                Files.list(userSharedPath).forEach(path -> {
                    if (Files.isRegularFile(path)) {
                        System.out.println("DEBUG: File found in directory: " + path.getFileName() +
                                " (size: " + path.toFile().length() + " bytes)");
                    }
                });
            } catch (IOException e) {
                System.err.println("DEBUG: Error listing directory contents: " + e.getMessage());
            }

            int myPort = findAvailablePortPair();
            if (myPort == -1) {
                throw new IOException("Could not find available ports");
            }

            System.out.println("DEBUG: Using ports - TCP: " + myPort + ", UDP: " + (myPort + 1));

            fileHandler = new LocalFileHandler(localSharedDirectory);
            downloadStrategy = new ChunkedDownload(8192, fileHandler, userSharedPath);

            // Test the fileHandler immediately
            List<String> foundFiles = fileHandler.listSharedFiles();
            System.out.println("DEBUG: FileHandler found " + foundFiles.size() + " files:");
            for (String file : foundFiles) {
                System.out.println("DEBUG: - " + file);
            }

            peerClient = new PeerClient("localhost", 9090, myPort, fileHandler, downloadStrategy);
            peerClient.setSessionContext(loggedInUser, serverTransport);

            // Start peer client in background
            executorService.submit(() -> {
                try {
                    System.out.println("DEBUG: Starting peer client...");
                    peerClient.start(localSharedDirectory);
                } catch (IOException e) {
                    System.err.println("DEBUG: Error starting peer client: " + e.getMessage());
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, "Error starting peer client: " + e.getMessage()));
                }
            });

        } catch (IOException e) {
            System.err.println("DEBUG: Error in initializePeerClient: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error initializing peer client: " + e.getMessage());
        }
    }

    private void showDashboard() {
        JPanel dashboardPanel = new JPanel(new BorderLayout());
        dashboardPanel.setBackground(Color.WHITE);

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(70, 130, 180));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        JLabel welcomeLabel = new JLabel("Welcome, " + loggedInUser.getUsername() +
                (loggedInUser.isAdmin() ? " (Admin)" : ""));
        welcomeLabel.setForeground(Color.white);
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 18));
        headerPanel.add(welcomeLabel, BorderLayout.WEST);

        JButton settingsBtn = new JButton("Settings");
        settingsBtn.setBackground(new Color(100, 149, 237));
        settingsBtn.setForeground(Color.black);
        settingsBtn.addActionListener(e -> showSettingsDialog());
        headerPanel.add(settingsBtn, BorderLayout.EAST);

        dashboardPanel.add(headerPanel, BorderLayout.NORTH);

        // Main content
        JPanel contentPanel = new JPanel(new GridLayout(3, 3, 20, 20));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        contentPanel.setBackground(Color.WHITE);

        // Dashboard buttons
        addDashboardButton(contentPanel, "Search Files", "Search and download files", e -> showSearchDialog());
        addDashboardButton(contentPanel, "Browse Peer Files", "Browse files from other peers", e -> showBrowsePeersDialog());
        addDashboardButton(contentPanel, "List Peers", "View all online peers", e -> showPeersList());
        addDashboardButton(contentPanel, "My Statistics", "View download/upload stats", e -> showMyStats());
        addDashboardButton(contentPanel, "My Files", "View and manage shared files", e -> showMyFiles());

        if (loggedInUser.isAdmin()) {
            addDashboardButton(contentPanel, "Remove User", "Remove a user (Admin only)", e -> showRemoveUserDialog());
        }

        dashboardPanel.add(contentPanel, BorderLayout.CENTER);

        mainPanel.add(dashboardPanel, "DASHBOARD");
        cardLayout.show(mainPanel, "DASHBOARD");
    }

    private void addDashboardButton(JPanel parent, String title, String description, ActionListener action) {
        JButton button = new JButton("<html><center><b>" + title + "</b><br><small>" + description + "</small></center></html>");
        button.setPreferredSize(new Dimension(180, 100));
        button.setBackground(new Color(240, 248, 255));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createRaisedBevelBorder(),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        button.addActionListener(action);
        button.setFocusPainted(false);
        parent.add(button);
    }

    private void showSearchDialog() {
        JDialog dialog = new JDialog(this, "Search Files", true);
        dialog.setSize(700, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel searchPanel = new JPanel(new BorderLayout());
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField searchField = new JTextField(25);
        JButton searchBtn = new JButton("Search");

        inputPanel.add(new JLabel("Search for:"));
        inputPanel.add(searchField);
        inputPanel.add(searchBtn);

        // Add helpful hint
        JLabel hintLabel = new JLabel("<html><i>Tip: You can search for partial file names (e.g., 'music', '.mp3', 'report') - search is case insensitive</i></html>");
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        searchPanel.add(inputPanel, BorderLayout.NORTH);
        searchPanel.add(hintLabel, BorderLayout.SOUTH);

        dialog.add(searchPanel, BorderLayout.NORTH);

        String[] columns = {"File Name", "Size", "Type", "Peer", "Action"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4; // Only action column is editable
            }
        };
        JTable resultsTable = new JTable(tableModel);
        resultsTable.getColumnModel().getColumn(4).setMaxWidth(80);
        resultsTable.getColumnModel().getColumn(4).setMinWidth(80);

        JScrollPane scrollPane = new JScrollPane(resultsTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Search Results"));
        dialog.add(scrollPane, BorderLayout.CENTER);

        // Allow Enter key to trigger search
        searchField.addActionListener(e -> searchBtn.doClick());

        searchBtn.addActionListener(e -> {
            String searchTerm = searchField.getText().trim();
            if (searchTerm.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please enter a search term");
                return;
            }

            searchBtn.setText("Searching...");
            searchBtn.setEnabled(false);

            // Use the enhanced search method
            performEnhancedSearch(searchTerm, tableModel, dialog);

            // Re-enable the button after a delay
            Timer timer = new Timer(2000, evt -> {
                searchBtn.setText("Search");
                searchBtn.setEnabled(true);
            });
            timer.setRepeats(false);
            timer.start();
        });
        resultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                int row = resultsTable.rowAtPoint(evt.getPoint());
                int col = resultsTable.columnAtPoint(evt.getPoint());

                if (col == 4 && row >= 0) { // Download column
                    String fileName = (String) tableModel.getValueAt(row, 0);
                    String peerUsername = (String) tableModel.getValueAt(row, 3);
                    downloadFileFromPeer(fileName, peerUsername, dialog);
                }
            }
        });

        JButton closeBtn = new JButton("Close");
        closeBtn.setForeground(Color.black);
        closeBtn.addActionListener(e -> dialog.dispose());
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(closeBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void downloadFileFromPeer(String fileName, String peerUsername, JDialog parentDialog) {
        executorService.submit(() -> {
            try {
                // Get peer address directly from LIST_PEERS instead of searching again
                serverTransport.sendLine("LIST_PEERS");
                String response = serverTransport.readLine();
                Map<String, String> onlinePeers = parsePeerInfoResponse(response);

                String peerAddress = onlinePeers.get(peerUsername);

                if (peerAddress != null) {
                    // Verify the peer actually has this file
                    List<String> peerFiles = getFileListFromPeer(peerAddress);
                    boolean hasFile = peerFiles != null && peerFiles.contains(fileName);

                    if (!hasFile) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(parentDialog,
                                        "Peer " + peerUsername + " no longer has file: " + fileName,
                                        "File Not Available", JOptionPane.WARNING_MESSAGE));
                        return;
                    }

                    long fileSize = getPeerFileSize(peerAddress, fileName);

                    SwingUtilities.invokeLater(() -> {
                        int result = JOptionPane.showConfirmDialog(parentDialog,
                                "Download '" + fileName + "' (" + formatFileSize(fileSize) + ") from " + peerUsername + "?",
                                "Confirm Download", JOptionPane.YES_NO_OPTION);

                        if (result == JOptionPane.YES_OPTION) {
                            executorService.submit(() -> {
                                try {
                                    SwingUtilities.invokeLater(() -> {
                                        JOptionPane optionPane = new JOptionPane("Downloading " + fileName + "...",
                                                JOptionPane.INFORMATION_MESSAGE,
                                                JOptionPane.DEFAULT_OPTION,
                                                null, new Object[]{}, null);
                                        JDialog progressDialog = optionPane.createDialog(parentDialog, "Download in Progress");
                                        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                                        progressDialog.setModal(false);
                                        progressDialog.setVisible(true);

                                        executorService.submit(() -> {
                                            try {
                                                System.out.println("DEBUG: Downloading from address: " + peerAddress);
                                                downloadStrategy.download(peerAddress, fileName);

                                                if (fileSize > 0) {
                                                    loggedInUser.getDownloadStats().addFile();
                                                    loggedInUser.getDownloadStats().addBytes(fileSize);
                                                    updateRemoteStats();
                                                }

                                                serverTransport.sendLine("SHARE " + fileName);

                                                SwingUtilities.invokeLater(() -> {
                                                    progressDialog.dispose();
                                                    JOptionPane.showMessageDialog(parentDialog,
                                                            "Successfully downloaded: " + fileName);
                                                });
                                            } catch (IOException e) {
                                                SwingUtilities.invokeLater(() -> {
                                                    progressDialog.dispose();
                                                    JOptionPane.showMessageDialog(parentDialog,
                                                            "Download failed: " + e.getMessage(),
                                                            "Download Error", JOptionPane.ERROR_MESSAGE);
                                                });
                                            }
                                        });
                                    });
                                } catch (Exception e) {
                                    SwingUtilities.invokeLater(() ->
                                            JOptionPane.showMessageDialog(parentDialog,
                                                    "Download setup failed: " + e.getMessage(),
                                                    "Error", JOptionPane.ERROR_MESSAGE));
                                }
                            });
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(parentDialog,
                                    "Could not find peer " + peerUsername + " (they may have disconnected)",
                                    "Peer Not Found", JOptionPane.WARNING_MESSAGE));
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(parentDialog,
                                "Error connecting to peer: " + e.getMessage(),
                                "Connection Error", JOptionPane.ERROR_MESSAGE));
            }
        });
    }

    private void showBrowsePeersDialog() {
        JDialog dialog = new JDialog(this, "Browse Peer Files", true);
        dialog.setSize(700, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        // Peer list
        DefaultListModel<String> peerListModel = new DefaultListModel<>();
        JList<String> peerList = new JList<>(peerListModel);
        peerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane peerScrollPane = new JScrollPane(peerList);
        peerScrollPane.setPreferredSize(new Dimension(200, 0));
        peerScrollPane.setBorder(BorderFactory.createTitledBorder("Online Peers"));

        // File list
        String[] columns = {"File Name", "Size", "Type", "Action"};
        DefaultTableModel fileTableModel = new DefaultTableModel(columns, 0);
        JTable fileTable = new JTable(fileTableModel);
        JScrollPane fileScrollPane = new JScrollPane(fileTable);
        fileScrollPane.setBorder(BorderFactory.createTitledBorder("Peer's Files"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, peerScrollPane, fileScrollPane);
        splitPane.setDividerLocation(200);
        dialog.add(splitPane, BorderLayout.CENTER);

        // Load peers
        executorService.submit(() -> {
            try {
                serverTransport.sendLine("LIST_PEERS");
                String response = serverTransport.readLine();
                Map<String, String> onlinePeers = parsePeerInfoResponse(response);
                onlinePeers.remove(loggedInUser.getUsername());

                SwingUtilities.invokeLater(() -> {
                    for (String peerUsername : onlinePeers.keySet()) {
                        peerListModel.addElement(peerUsername);
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(dialog, "Error loading peers: " + e.getMessage()));
            }
        });

        // Peer selection handler
        peerList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedPeer = peerList.getSelectedValue();
                if (selectedPeer != null) {
                    loadPeerFiles(selectedPeer, fileTableModel, dialog);
                }
            }
        });

        // File download handler
        fileTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                int row = fileTable.rowAtPoint(evt.getPoint());
                int col = fileTable.columnAtPoint(evt.getPoint());

                if (col == 3 && row >= 0) { // Download column
                    String fileName = (String) fileTableModel.getValueAt(row, 0);
                    String selectedPeer = peerList.getSelectedValue();
                    if (selectedPeer != null) {
                        downloadFileFromPeer(fileName, selectedPeer, dialog);
                    }
                }
            }
        });

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(closeBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void loadPeerFiles(String peerUsername, DefaultTableModel tableModel, JDialog parentDialog) {
        executorService.submit(() -> {
            try {
                serverTransport.sendLine("LIST_PEERS");
                String response = serverTransport.readLine();
                Map<String, String> onlinePeers = parsePeerInfoResponse(response);

                if (onlinePeers.containsKey(peerUsername)) {
                    String peerAddress = onlinePeers.get(peerUsername);
                    List<String> fileNames = getFileListFromPeer(peerAddress);

                    SwingUtilities.invokeLater(() -> {
                        tableModel.setRowCount(0);

                        if (fileNames != null && !fileNames.isEmpty()) {
                            for (String fileName : fileNames) {
                                long fileSize = getPeerFileSize(peerAddress, fileName);
                                SharedFile sf = SharedFileFactory.createSharedFile(fileName, fileSize);

                                Object[] rowData = {
                                        fileName,
                                        formatFileSize(fileSize),
                                        sf.getClass().getSimpleName().replace("File", ""),
                                        "Download"
                                };
                                tableModel.addRow(rowData);
                            }
                        } else {
                            JOptionPane.showMessageDialog(parentDialog,
                                    "No files found for peer: " + peerUsername);
                        }
                    });
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(parentDialog, "Error loading peer files: " + e.getMessage()));
            }
        });
    }

    private void showPeersList() {
        JDialog dialog = new JDialog(this, "Online Peers", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        String[] columns = {"Username", "Address"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0);
        JTable peersTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(peersTable);
        dialog.add(scrollPane, BorderLayout.CENTER);

        executorService.submit(() -> {
            try {
                serverTransport.sendLine("LIST_PEERS");
                String response = serverTransport.readLine();
                Map<String, String> onlinePeers = parsePeerInfoResponse(response);
                onlinePeers.remove(loggedInUser.getUsername());

                SwingUtilities.invokeLater(() -> {
                    for (Map.Entry<String, String> entry : onlinePeers.entrySet()) {
                        Object[] rowData = {entry.getKey(), entry.getValue()};
                        tableModel.addRow(rowData);
                    }

                    if (tableModel.getRowCount() == 0) {
                        tableModel.addRow(new Object[]{"No other peers online", ""});
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(dialog, "Error loading peers: " + e.getMessage()));
            }
        });

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(closeBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void showMyStats() {
        JDialog dialog = new JDialog(this, "My Statistics", true);
        dialog.setSize(400, 350);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel titleLabel = new JLabel("User Statistics for: " + loggedInUser.getUsername());
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.insets = new Insets(20, 20, 20, 20);
        dialog.add(titleLabel, gbc);

        // Download stats
        JLabel downloadLabel = new JLabel("Download Statistics");
        downloadLabel.setFont(new Font("Arial", Font.BOLD, 14));
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.insets = new Insets(10, 20, 5, 20);
        dialog.add(downloadLabel, gbc);

        JLabel downloadFilesLabel = new JLabel("Files Downloaded: " + loggedInUser.getDownloadStats().getFileCount());
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.insets = new Insets(5, 40, 5, 20);
        dialog.add(downloadFilesLabel, gbc);

        JLabel downloadBytesLabel = new JLabel("Total Bytes Downloaded: " +
                formatFileSize(loggedInUser.getDownloadStats().getTotalBytes()));
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.insets = new Insets(5, 40, 10, 20);
        dialog.add(downloadBytesLabel, gbc);

        // Upload stats
        JLabel uploadLabel = new JLabel("Upload Statistics");
        uploadLabel.setFont(new Font("Arial", Font.BOLD, 14));
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.insets = new Insets(10, 20, 5, 20);
        dialog.add(uploadLabel, gbc);

        JLabel uploadFilesLabel = new JLabel("Files Uploaded: " + loggedInUser.getUploadStats().getFileCount());
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2; gbc.insets = new Insets(5, 40, 5, 20);
        dialog.add(uploadFilesLabel, gbc);

        JLabel uploadBytesLabel = new JLabel("Total Bytes Uploaded: " +
                formatFileSize(loggedInUser.getUploadStats().getTotalBytes()));
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; gbc.insets = new Insets(5, 40, 20, 20);
        dialog.add(uploadBytesLabel, gbc);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2; gbc.insets = new Insets(10, 20, 20, 20);
        dialog.add(closeBtn, gbc);

        dialog.setVisible(true);
    }

    private void showMyFiles() {
        JDialog dialog = new JDialog(this, "My Shared Files", true);
        dialog.setSize(600, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        String[] columns = {"File Name", "Size", "Type", "Full Path"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0);
        JTable filesTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(filesTable);
        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton uploadBtn = new JButton("Upload Files");
        JButton refreshBtn = new JButton("Refresh");
        JButton closeBtn = new JButton("Close");

        buttonPanel.add(uploadBtn);
        buttonPanel.add(refreshBtn);
        buttonPanel.add(closeBtn);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        // Load current files
        refreshFilesList(tableModel);

        uploadBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setMultiSelectionEnabled(true);
            fileChooser.setDialogTitle("Select files to upload to shared directory");

            int result = fileChooser.showOpenDialog(dialog);
            if (result == JFileChooser.APPROVE_OPTION) {
                File[] selectedFiles = fileChooser.getSelectedFiles();
                uploadFiles(selectedFiles, dialog, tableModel);
            }
        });

        refreshBtn.addActionListener(e -> refreshFilesList(tableModel));
        closeBtn.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    private void refreshFilesList(DefaultTableModel tableModel) {
        tableModel.setRowCount(0);

        if (fileHandler instanceof LocalFileHandler) {
            LocalFileHandler localHandler = (LocalFileHandler) fileHandler;
            List<SharedFile> sharedFiles = localHandler.listSharedFileObjects();

            for (SharedFile sf : sharedFiles) {
                Object[] rowData = {
                        sf.getName(),
                        formatFileSize(sf.getSize()),
                        sf.getClass().getSimpleName().replace("File", ""),
                        localHandler.getSharedDirectory().resolve(sf.getName()).toString()
                };
                tableModel.addRow(rowData);
            }

            if (tableModel.getRowCount() == 0) {
                tableModel.addRow(new Object[]{"No files in shared directory", "", "", ""});
            }
        }
    }

    private void uploadFiles(File[] files, JDialog parentDialog, DefaultTableModel tableModel) {
        executorService.submit(() -> {
            int successCount = 0;

            for (File file : files) {
                try {
                    Path targetPath = Paths.get(localSharedDirectory, file.getName());
                    Files.copy(file.toPath(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                    // Update upload stats
                    loggedInUser.getUploadStats().addFile();
                    loggedInUser.getUploadStats().addBytes(file.length());

                    // Share with network
                    serverTransport.sendLine("SHARE " + file.getName());

                    successCount++;
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(parentDialog,
                                    "Error uploading " + file.getName() + ": " + e.getMessage()));
                }
            }

            // Update remote stats
            updateRemoteStats();

            final int finalSuccessCount = successCount;
            SwingUtilities.invokeLater(() -> {
                if (finalSuccessCount > 0) {
                    JOptionPane.showMessageDialog(parentDialog,
                            "Successfully uploaded " + finalSuccessCount + " files");
                    refreshFilesList(tableModel);
                }
            });
        });
    }

    private void showRemoveUserDialog() {
        if (!loggedInUser.isAdmin()) {
            JOptionPane.showMessageDialog(this, "Access denied. Admin privileges required.");
            return;
        }

        JDialog dialog = new JDialog(this, "Remove User (Admin)", true);
        dialog.setSize(400, 200);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel warningLabel = new JLabel("<html><b>WARNING:</b> This will permanently remove the user!</html>");
        warningLabel.setForeground(Color.RED);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.insets = new Insets(20, 20, 20, 20);
        dialog.add(warningLabel, gbc);

        JLabel usernameLabel = new JLabel("Username to remove:");
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.insets = new Insets(10, 20, 10, 5);
        dialog.add(usernameLabel, gbc);

        JTextField usernameField = new JTextField(15);
        gbc.gridx = 1; gbc.gridy = 1; gbc.insets = new Insets(10, 5, 10, 20);
        dialog.add(usernameField, gbc);

        JPanel buttonPanel = new JPanel();
        JButton removeBtn = new JButton("Remove User");
        JButton cancelBtn = new JButton("Cancel");
        removeBtn.setBackground(Color.RED);
        removeBtn.setForeground(Color.red);
        buttonPanel.add(removeBtn);
        buttonPanel.add(cancelBtn);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.insets = new Insets(20, 20, 20, 20);
        dialog.add(buttonPanel, gbc);

        removeBtn.addActionListener(e -> {
            String usernameToRemove = usernameField.getText().trim();
            if (usernameToRemove.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please enter a username");
                return;
            }

            if (loggedInUser.getUsername().equalsIgnoreCase(usernameToRemove)) {
                JOptionPane.showMessageDialog(dialog, "You cannot remove yourself");
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(dialog,
                    "Are you sure you want to permanently remove user '" + usernameToRemove + "'?",
                    "Confirm Removal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                executorService.submit(() -> {
                    try {
                        serverTransport.sendLine("REMOVE_USER " + usernameToRemove);
                        String response = serverTransport.readLine();

                        SwingUtilities.invokeLater(() -> {
                            if ("REMOVE_SUCCESS".equals(response)) {
                                JOptionPane.showMessageDialog(dialog,
                                        "User '" + usernameToRemove + "' has been removed successfully");
                                dialog.dispose();
                            } else {
                                String errorMsg = response != null ? response.replace("REMOVE_FAIL ", "") : "No response";
                                JOptionPane.showMessageDialog(dialog, "Failed to remove user: " + errorMsg);
                            }
                        });
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage()));
                    }
                });
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    private void showSettingsDialog() {
        JDialog dialog = new JDialog(this, "Settings", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel titleLabel = new JLabel("Settings for " + loggedInUser.getUsername());
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(20, 20, 20, 20);
        dialog.add(titleLabel, gbc);

        JButton changePasswordBtn = new JButton("Change Password");
        changePasswordBtn.setPreferredSize(new Dimension(200, 35));
        changePasswordBtn.setForeground(Color.black);
        gbc.gridx = 0; gbc.gridy = 1; gbc.insets = new Insets(10, 20, 5, 20);
        dialog.add(changePasswordBtn, gbc);

        JButton deleteAccountBtn = new JButton("Delete Account");
        deleteAccountBtn.setPreferredSize(new Dimension(200, 35));
        deleteAccountBtn.setBackground(Color.RED);
        deleteAccountBtn.setForeground(Color.RED);
        gbc.gridx = 0; gbc.gridy = 2; gbc.insets = new Insets(5, 20, 5, 20);
        dialog.add(deleteAccountBtn, gbc);

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setPreferredSize(new Dimension(200, 35));
        logoutBtn.setBackground(Color.BLACK);
        logoutBtn.setForeground(Color.black);
        gbc.gridx = 0; gbc.gridy = 3; gbc.insets = new Insets(5, 20, 20, 20);
        dialog.add(logoutBtn, gbc);

        JButton closeBtn = new JButton("Close");
        gbc.gridx = 0; gbc.gridy = 4; gbc.insets = new Insets(10, 20, 20, 20);
        dialog.add(closeBtn, gbc);

        changePasswordBtn.addActionListener(e -> {
            dialog.dispose();
            showChangePasswordDialog();
        });

        deleteAccountBtn.addActionListener(e -> {
            dialog.dispose();
            showDeleteAccountDialog();
        });

        logoutBtn.addActionListener(e -> {
            dialog.dispose();
            logout();
        });

        closeBtn.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    private void showChangePasswordDialog() {
        JDialog dialog = new JDialog(this, "Change Password", true);
        dialog.setSize(400, 250);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel confirmLabel = new JLabel("Confirm Username:");
        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(20, 20, 5, 5);
        dialog.add(confirmLabel, gbc);

        JTextField confirmUsernameField = new JTextField(15);
        gbc.gridx = 1; gbc.gridy = 0; gbc.insets = new Insets(20, 5, 5, 20);
        dialog.add(confirmUsernameField, gbc);

        JLabel currentPasswordLabel = new JLabel("Current Password:");
        gbc.gridx = 0; gbc.gridy = 1; gbc.insets = new Insets(5, 20, 5, 5);
        dialog.add(currentPasswordLabel, gbc);

        JPasswordField currentPasswordField = new JPasswordField(15);
        gbc.gridx = 1; gbc.gridy = 1; gbc.insets = new Insets(5, 5, 5, 20);
        dialog.add(currentPasswordField, gbc);

        JLabel newPasswordLabel = new JLabel("New Password:");
        gbc.gridx = 0; gbc.gridy = 2; gbc.insets = new Insets(5, 20, 5, 5);
        dialog.add(newPasswordLabel, gbc);

        JPasswordField newPasswordField = new JPasswordField(15);
        gbc.gridx = 1; gbc.gridy = 2; gbc.insets = new Insets(5, 5, 5, 20);
        dialog.add(newPasswordField, gbc);

        JLabel confirmPasswordLabel = new JLabel("Confirm New Password:");
        gbc.gridx = 0; gbc.gridy = 3; gbc.insets = new Insets(5, 20, 20, 5);
        dialog.add(confirmPasswordLabel, gbc);

        JPasswordField confirmPasswordField = new JPasswordField(15);
        gbc.gridx = 1; gbc.gridy = 3; gbc.insets = new Insets(5, 5, 20, 20);
        dialog.add(confirmPasswordField, gbc);

        JPanel buttonPanel = new JPanel();
        JButton changeBtn = new JButton("Change Password");
        JButton cancelBtn = new JButton("Cancel");
        buttonPanel.add(changeBtn);
        buttonPanel.add(cancelBtn);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.insets = new Insets(10, 20, 20, 20);
        dialog.add(buttonPanel, gbc);

        changeBtn.addActionListener(e -> {
            String confirmUsername = confirmUsernameField.getText().trim();
            String currentPassword = new String(currentPasswordField.getPassword());
            String newPassword = new String(newPasswordField.getPassword());
            String confirmPassword = new String(confirmPasswordField.getPassword());

            if (!confirmUsername.equals(loggedInUser.getUsername())) {
                JOptionPane.showMessageDialog(dialog, "Username confirmation failed");
                return;
            }

            if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please fill in all fields");
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(dialog, "New passwords do not match");
                return;
            }

            if (newPassword.length() < 3) {
                JOptionPane.showMessageDialog(dialog, "New password must be at least 3 characters long");
                return;
            }

            executorService.submit(() -> {
                try {
                    serverTransport.sendLine("CHANGE_PASSWORD " + currentPassword + " " + newPassword);
                    String response = serverTransport.readLine();

                    SwingUtilities.invokeLater(() -> {
                        if ("CHANGE_PASSWORD_SUCCESS".equals(response)) {
                            JOptionPane.showMessageDialog(dialog, "Password changed successfully!");
                            dialog.dispose();
                        } else {
                            String errorMsg = response != null ? response.replace("CHANGE_PASSWORD_FAIL ", "") : "No response";
                            JOptionPane.showMessageDialog(dialog, "Failed to change password: " + errorMsg);
                        }
                    });
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(dialog, "Connection error: " + ex.getMessage()));
                }
            });
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    private void showDeleteAccountDialog() {
        JDialog dialog = new JDialog(this, "Delete Account", true);
        dialog.setSize(400, 280);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel warningLabel = new JLabel("<html><center><b>WARNING!</b><br>This will permanently delete your account.<br>This action cannot be undone!</center></html>");
        warningLabel.setForeground(Color.RED);
        warningLabel.setFont(new Font("Arial", Font.BOLD, 14));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.insets = new Insets(20, 20, 20, 20);
        dialog.add(warningLabel, gbc);

        JLabel usernameLabel = new JLabel("Confirm Username:");
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.insets = new Insets(10, 20, 5, 5);
        dialog.add(usernameLabel, gbc);

        JTextField confirmUsernameField = new JTextField(15);
        gbc.gridx = 1; gbc.gridy = 1; gbc.insets = new Insets(10, 5, 5, 20);
        dialog.add(confirmUsernameField, gbc);

        JLabel passwordLabel = new JLabel("Confirm Password:");
        gbc.gridx = 0; gbc.gridy = 2; gbc.insets = new Insets(5, 20, 20, 5);
        dialog.add(passwordLabel, gbc);

        JPasswordField confirmPasswordField = new JPasswordField(15);
        gbc.gridx = 1; gbc.gridy = 2; gbc.insets = new Insets(5, 5, 20, 20);
        dialog.add(confirmPasswordField, gbc);

        JPanel buttonPanel = new JPanel();
        JButton deleteBtn = new JButton("DELETE ACCOUNT");
        JButton cancelBtn = new JButton("Cancel");
        deleteBtn.setBackground(Color.RED);
        deleteBtn.setForeground(Color.red);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(cancelBtn);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.insets = new Insets(10, 20, 20, 20);
        dialog.add(buttonPanel, gbc);

        deleteBtn.addActionListener(e -> {
            String confirmUsername = confirmUsernameField.getText().trim();
            String confirmPassword = new String(confirmPasswordField.getPassword());

            if (!confirmUsername.equals(loggedInUser.getUsername())) {
                JOptionPane.showMessageDialog(dialog, "Username confirmation failed");
                return;
            }

            if (confirmPassword.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please enter your password");
                return;
            }

            int finalConfirm = JOptionPane.showConfirmDialog(dialog,
                    "Are you absolutely sure you want to delete your account?\n" +
                            "This will permanently remove all your data and cannot be undone.",
                    "Final Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);

            if (finalConfirm == JOptionPane.YES_OPTION) {
                executorService.submit(() -> {
                    try {
                        serverTransport.sendLine("DELETE_ACCOUNT " + confirmUsername + " " + confirmPassword);
                        String response = serverTransport.readLine();

                        SwingUtilities.invokeLater(() -> {
                            if ("DELETE_ACCOUNT_SUCCESS".equals(response)) {
                                JOptionPane.showMessageDialog(dialog,
                                        "Your account has been permanently deleted.\nThe application will now exit.");
                                dialog.dispose();
                                cleanup();
                                System.exit(0);
                            } else {
                                String errorMsg = response != null ? response.replace("DELETE_ACCOUNT_FAIL ", "") : "No response";
                                JOptionPane.showMessageDialog(dialog, "Failed to delete account: " + errorMsg);
                            }
                        });
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(dialog, "Connection error: " + ex.getMessage()));
                    }
                });
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to logout?", "Confirm Logout",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            cleanup();
            loggedInUser = null;
            showLoginPage();
        }
    }
    private void performEnhancedSearch(String searchTerm, DefaultTableModel tableModel, JDialog dialog) {
        executorService.submit(() -> {
            try {
                // Search for the term directly
                serverTransport.sendLine("SEARCH " + searchTerm);
                String response = serverTransport.readLine();

                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
                    Map<String, Map<String, String>> searchResults = parseEnhancedSearchResponse(response);

                    int totalFiles = 0;
                    for (Map.Entry<String, Map<String, String>> fileEntry : searchResults.entrySet()) {
                        String fileName = fileEntry.getKey();
                        Map<String, String> peersWithFile = fileEntry.getValue();

                        for (Map.Entry<String, String> peerEntry : peersWithFile.entrySet()) {
                            String peerUsername = peerEntry.getKey();
                            String peerAddress = peerEntry.getValue();
                            long fileSize = getPeerFileSize(peerAddress, fileName);

                            if (fileSize > -1) {
                                SharedFile sf = SharedFileFactory.createSharedFile(fileName, fileSize);
                                Object[] rowData = {
                                        fileName,
                                        formatFileSize(fileSize),
                                        sf.getClass().getSimpleName().replace("File", ""),
                                        peerUsername,
                                        "Download"
                                };
                                tableModel.addRow(rowData);
                                totalFiles++;
                            }
                        }
                    }

                    // Also search for files by peer name if no direct file matches
                    if (totalFiles == 0) {
                        searchByPeerName(searchTerm, tableModel);
                    }

                    if (tableModel.getRowCount() == 0) {
                        JOptionPane.showMessageDialog(dialog,
                                "No files found matching: '" + searchTerm + "'\n\n" +
                                        "Try searching for:\n" +
                                        "• File extensions (.mp3, .txt, .jpg)\n" +
                                        "• Partial filenames (music, document)\n" +
                                        "• Peer usernames",
                                "No Results", JOptionPane.INFORMATION_MESSAGE);
                        dialog.setTitle("Search Files - No Results");
                    } else {
                        dialog.setTitle("Search Files - " + tableModel.getRowCount() + " files found");
                    }
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(dialog, "Search error: " + ex.getMessage());
                });
            }
        });
    }
    private void searchByPeerName(String searchTerm, DefaultTableModel tableModel) {
        try {
            serverTransport.sendLine("LIST_PEERS");
            String peersResponse = serverTransport.readLine();
            Map<String, String> onlinePeers = parsePeerInfoResponse(peersResponse);

            // Find peers whose username contains the search term
            for (Map.Entry<String, String> peerEntry : onlinePeers.entrySet()) {
                String peerUsername = peerEntry.getKey();
                String peerAddress = peerEntry.getValue();

                if (peerUsername.toLowerCase().contains(searchTerm.toLowerCase())) {
                    // Get all files from this peer
                    List<String> fileNames = getFileListFromPeer(peerAddress);

                    if (fileNames != null && !fileNames.isEmpty()) {
                        for (String fileName : fileNames) {
                            long fileSize = getPeerFileSize(peerAddress, fileName);
                            if (fileSize > -1) {
                                SharedFile sf = SharedFileFactory.createSharedFile(fileName, fileSize);
                                Object[] rowData = {
                                        fileName,
                                        formatFileSize(fileSize),
                                        sf.getClass().getSimpleName().replace("File", ""),
                                        peerUsername,
                                        "Download"
                                };
                                tableModel.addRow(rowData);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error searching by peer name: " + e.getMessage());
        }
    }

    private void debugSharedDirectory() {
        System.out.println("=== DEBUGGING SHARED DIRECTORY ===");
        System.out.println("Local shared directory path: " + localSharedDirectory);

        if (fileHandler instanceof LocalFileHandler) {
            LocalFileHandler localHandler = (LocalFileHandler) fileHandler;
            System.out.println("FileHandler directory: " + localHandler.getSharedDirectory());

            List<String> files = fileHandler.listSharedFiles();
            System.out.println("Files found by FileHandler: " + files.size());
            for (String file : files) {
                System.out.println("  - " + file);
            }

            // Also check the actual directory on disk
            try {
                Path dirPath = Paths.get(localSharedDirectory);
                if (Files.exists(dirPath)) {
                    System.out.println("Directory exists on disk: " + dirPath);
                    Files.list(dirPath).forEach(path -> {
                        if (Files.isRegularFile(path)) {
                            System.out.println("  File on disk: " + path.getFileName());
                        }
                    });
                } else {
                    System.out.println("ERROR: Directory does not exist: " + dirPath);
                }
            } catch (Exception e) {
                System.out.println("ERROR checking directory: " + e.getMessage());
            }
        }
        System.out.println("=== END DEBUG ===");
    }

    // Enhanced helper method to parse the new search response format
    private Map<String, Map<String, String>> parseEnhancedSearchResponse(String response) {
        Map<String, Map<String, String>> results = new LinkedHashMap<>();

        System.out.println("DEBUG: Client received search response: '" + response + "'");

        if (response == null || response.trim().isEmpty()) {
            System.out.println("DEBUG: Empty response received");
            return results;
        }

        // Server sends format: filename1=peer1:address1,peer2:address2;filename2=peer3:address3
        String[] fileEntries = response.split(";");
        System.out.println("DEBUG: Split into " + fileEntries.length + " file entries");

        for (String fileEntry : fileEntries) {
            if (fileEntry.trim().isEmpty()) continue;

            System.out.println("DEBUG: Processing file entry: '" + fileEntry + "'");

            String[] fileParts = fileEntry.split("=", 2);
            if (fileParts.length == 2) {
                String fileName = fileParts[0];
                String peerList = fileParts[1];

                System.out.println("DEBUG: FileName: '" + fileName + "', PeerList: '" + peerList + "'");

                Map<String, String> peersForFile = new LinkedHashMap<>();

                String[] peers = peerList.split(",");
                for (String peer : peers) {
                    String[] peerParts = peer.split(":", 2);
                    if (peerParts.length == 2) {
                        String username = peerParts[0];
                        String address = peerParts[1];
                        peersForFile.put(username, address);
                        System.out.println("DEBUG: Added peer - Username: '" + username + "', Address: '" + address + "'");
                    }
                }

                results.put(fileName, peersForFile);
            }
        }

        System.out.println("DEBUG: Final parsed results: " + results.size() + " files");
        return results;
    }
    // Helper methods from backend
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

    private List<String> getFileListFromPeer(String peerAddress) {
        try {
            String[] parts = peerAddress.split(":");
            String host = parts[0];
            int udpPort = Integer.parseInt(parts[1]) + 1;

            try (UDPTransport peerTransport = new UDPTransport(host, udpPort)) {
                peerTransport.setSoTimeout(3000);
                peerTransport.sendLine("LIST_FILES");
                String line = peerTransport.readLine();
                if (line == null || line.trim().isEmpty()) return Collections.emptyList();
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
                return -1;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double displaySize = size;
        while (displaySize >= 1024 && unitIndex < units.length - 1) {
            displaySize /= 1024;
            unitIndex++;
        }
        return String.format(Locale.US, "%.1f %s", displaySize, units[unitIndex]);
    }

    private static int findAvailablePortPair() {
        for (int port = 10000; port <= 11000; port++) {
            if (arePortsAvailable(port, port + 1)) return port;
        }
        return -1;
    }

    private static boolean arePortsAvailable(int tcpPort, int udpPort) {
        try (java.net.ServerSocket tcpSocket = new java.net.ServerSocket(tcpPort);
             java.net.DatagramSocket udpSocket = new java.net.DatagramSocket(udpPort)) {
            tcpSocket.setReuseAddress(true);
            udpSocket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void updateRemoteStats() {
        try {
            String downloadStatsCsv = loggedInUser.getDownloadStats().toCsvString();
            String uploadStatsCsv = loggedInUser.getUploadStats().toCsvString();
            serverTransport.sendLine("UPDATE_STATS " + downloadStatsCsv + " " + uploadStatsCsv);
        } catch (IOException e) {
            System.err.println("Warning: Could not update stats with server: " + e.getMessage());
        }
    }

    private void cleanup() {
        try {
            if (serverTransport != null) {
                serverTransport.sendLine("UNREGISTER");
                serverTransport.close();
            }
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
            }
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Use default look and feel
            }

            new P2PClientUI().setVisible(true);
        });
    }
}