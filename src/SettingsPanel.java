import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class SettingsPanel extends JPanel {
    private Properties appSettings;
    private String settingsFilePath;
    private User currentUser;

    // Network Settings
    private JTextField serverHostField;
    private JSpinner serverPortSpinner;
    private JSpinner listenPortSpinner;
    private JSpinner maxConnectionsSpinner;
    private JSpinner downloadTimeoutSpinner;

    // File Settings
    private JTextField sharedDirectoryField;
    // replaced the single Browse with two explicit actions
    private JButton changeDirectoryButton;   // NEW
    private JButton removeDirectoryButton;   // NEW
    private JTextField downloadDirectoryField;
    private JButton browseDownloadButton;
    private JCheckBox autoShareNewFilesBox;
    private JSpinner maxFileSizeSpinner;
    private JCheckBox allowDuplicatesBox;

    // UI Settings
    private JComboBox<String> themeComboBox;
    private JCheckBox minimizeToTrayBox;
    private JCheckBox startMinimizedBox;
    private JCheckBox showNotificationsBox;
    private JSpinner refreshIntervalSpinner;

    // Security Settings
    private JPasswordField currentPasswordField;
    private JPasswordField newPasswordField;
    private JPasswordField confirmPasswordField;
    private JButton changePasswordButton;
    private JCheckBox rememberCredentialsBox;
    private JButton deleteAccountButton;

    // Advanced Settings
    private JSpinner chunkSizeSpinner;
    private JSpinner retryAttemptsSpinner;
    private JCheckBox enableLoggingBox;
    private JComboBox<String> logLevelComboBox;
    private JButton clearCacheButton;
    private JButton resetSettingsButton;

    // Buttons
    private JButton saveButton;
    private JButton cancelButton;
    private JButton applyButton;

    public SettingsPanel(User user) {
        this.currentUser = user;
        this.settingsFilePath = "app_settings.properties";
        this.appSettings = new Properties();

        loadSettings();
        initializeComponents();
        setupLayout();
        setupEventListeners();
        populateFields();
    }

    private void loadSettings() {
        // Load settings from file or set defaults
        try (FileInputStream fis = new FileInputStream(settingsFilePath)) {
            appSettings.load(fis);
        } catch (IOException e) {
            // Set default values if file doesn't exist
            setDefaultSettings();
        }
    }

    private void setDefaultSettings() {
        appSettings.setProperty("server.host", "localhost");
        appSettings.setProperty("server.port", "9090");
        appSettings.setProperty("client.listen.port", "10000");
        appSettings.setProperty("network.max.connections", "50");
        appSettings.setProperty("network.download.timeout", "30000");

        appSettings.setProperty("files.shared.directory", System.getProperty("user.home") + "/SharedFiles");
        appSettings.setProperty("files.auto.share.new", "true");
        appSettings.setProperty("files.max.size.mb", "1024");
        appSettings.setProperty("files.allow.duplicates", "false");

        appSettings.setProperty("ui.theme", "Default");
        appSettings.setProperty("ui.minimize.to.tray", "true");
        appSettings.setProperty("ui.start.minimized", "false");
        appSettings.setProperty("ui.show.notifications", "true");
        appSettings.setProperty("ui.refresh.interval", "5000");

        appSettings.setProperty("security.remember.credentials", "false");

        appSettings.setProperty("advanced.chunk.size", "8192");
        appSettings.setProperty("advanced.retry.attempts", "3");
        appSettings.setProperty("advanced.enable.logging", "true");
        appSettings.setProperty("advanced.log.level", "INFO");
    }

    private void initializeComponents() {
        // Network settings
        serverHostField = new JTextField(20);
        serverPortSpinner = new JSpinner(new SpinnerNumberModel(9090, 1, 65535, 1));
        listenPortSpinner = new JSpinner(new SpinnerNumberModel(10000, 1024, 65535, 1));
        maxConnectionsSpinner = new JSpinner(new SpinnerNumberModel(50, 1, 500, 1));
        downloadTimeoutSpinner = new JSpinner(new SpinnerNumberModel(30000, 5000, 300000, 1000));

        // File settings
        sharedDirectoryField = new JTextField(30);
        sharedDirectoryField.setEditable(false);

        changeDirectoryButton = new JButton("Change…");           // NEW
        changeDirectoryButton.setToolTipText("Pick a new shared directory");
        removeDirectoryButton = new JButton("Remove");            // NEW
        removeDirectoryButton.setToolTipText("Clear the current shared directory");
        removeDirectoryButton.setForeground(new Color(220, 38, 38));

        downloadDirectoryField = new JTextField(30);
        downloadDirectoryField.setEditable(false);
        browseDownloadButton = new JButton("Browse...");

        autoShareNewFilesBox = new JCheckBox("Automatically share new files");
        maxFileSizeSpinner = new JSpinner(new SpinnerNumberModel(1024, 1, 10240, 1));
        allowDuplicatesBox = new JCheckBox("Allow duplicate files");

        // UI settings
        themeComboBox = new JComboBox<>(new String[]{"Default", "Dark", "Light"});
        minimizeToTrayBox = new JCheckBox("Minimize to system tray");
        startMinimizedBox = new JCheckBox("Start minimized");
        showNotificationsBox = new JCheckBox("Show notifications");
        refreshIntervalSpinner = new JSpinner(new SpinnerNumberModel(5000, 1000, 60000, 1000));

        // Security settings
        currentPasswordField = new JPasswordField(20);
        newPasswordField = new JPasswordField(20);
        confirmPasswordField = new JPasswordField(20);
        changePasswordButton = new JButton("Change Password");
        rememberCredentialsBox = new JCheckBox("Remember login credentials");

        // Advanced settings
        chunkSizeSpinner = new JSpinner(new SpinnerNumberModel(8192, 1024, 65536, 1024));
        retryAttemptsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
        enableLoggingBox = new JCheckBox("Enable application logging");
        logLevelComboBox = new JComboBox<>(new String[]{"ERROR", "WARN", "INFO", "DEBUG"});
        clearCacheButton = new JButton("Clear Cache");
        resetSettingsButton = new JButton("Reset to Defaults");

        // Control buttons
        saveButton = new JButton("Save");
        cancelButton = new JButton("Cancel");
        applyButton = new JButton("Apply");

        // Set tooltips
        setupTooltips();
    }

    private void setupTooltips() {
        serverHostField.setToolTipText("Server hostname or IP address");
        serverPortSpinner.setToolTipText("Server port number");
        listenPortSpinner.setToolTipText("Local port for incoming connections");
        maxConnectionsSpinner.setToolTipText("Maximum concurrent connections");
        downloadTimeoutSpinner.setToolTipText("Download timeout in milliseconds");

        sharedDirectoryField.setToolTipText("Directory containing shared files");
        autoShareNewFilesBox.setToolTipText("Automatically share files added to the directory");
        maxFileSizeSpinner.setToolTipText("Maximum file size to share (MB)");

        chunkSizeSpinner.setToolTipText("File transfer chunk size in bytes");
        retryAttemptsSpinner.setToolTipText("Number of retry attempts for failed operations");
        refreshIntervalSpinner.setToolTipText("UI refresh interval in milliseconds");
    }

    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Create tabbed pane for different setting categories
        JTabbedPane tabbedPane = new JTabbedPane();

        tabbedPane.addTab("Network", createNetworkPanel());
        tabbedPane.addTab("Files", createFilesPanel());
        tabbedPane.addTab("Interface", createInterfacePanel());
        tabbedPane.addTab("Security", createSecurityPanel());
        tabbedPane.addTab("Advanced", createAdvancedPanel());

        // Button panel
        JPanel buttonPanel = createButtonPanel();

        add(tabbedPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createNetworkPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Server settings
        JPanel serverPanel = new JPanel(new GridBagLayout());
        serverPanel.setBorder(new TitledBorder("Server Connection"));
        GridBagConstraints serverGbc = new GridBagConstraints();
        serverGbc.insets = new Insets(3, 3, 3, 3);
        serverGbc.anchor = GridBagConstraints.WEST;

        serverGbc.gridx = 0; serverGbc.gridy = 0;
        serverPanel.add(new JLabel("Server Host:"), serverGbc);
        serverGbc.gridx = 1;
        serverPanel.add(serverHostField, serverGbc);

        serverGbc.gridx = 0; serverGbc.gridy = 1;
        serverPanel.add(new JLabel("Server Port:"), serverGbc);
        serverGbc.gridx = 1;
        serverPanel.add(serverPortSpinner, serverGbc);

        // Client settings
        JPanel clientPanel = new JPanel(new GridBagLayout());
        clientPanel.setBorder(new TitledBorder("Client Settings"));
        GridBagConstraints clientGbc = new GridBagConstraints();
        clientGbc.insets = new Insets(3, 3, 3, 3);
        clientGbc.anchor = GridBagConstraints.WEST;

        clientGbc.gridx = 0; clientGbc.gridy = 0;
        clientPanel.add(new JLabel("Listen Port:"), clientGbc);
        clientGbc.gridx = 1;
        clientPanel.add(listenPortSpinner, clientGbc);

        clientGbc.gridx = 0; clientGbc.gridy = 1;
        clientPanel.add(new JLabel("Max Connections:"), clientGbc);
        clientGbc.gridx = 1;
        clientPanel.add(maxConnectionsSpinner, clientGbc);

        clientGbc.gridx = 0; clientGbc.gridy = 2;
        clientPanel.add(new JLabel("Download Timeout (ms):"), clientGbc);
        clientGbc.gridx = 1;
        clientPanel.add(downloadTimeoutSpinner, clientGbc);

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(serverPanel, gbc);

        gbc.gridy = 1;
        panel.add(clientPanel, gbc);

        return panel;
    }

    private JPanel createFilesPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Shared directory
        JPanel dirPanel = new JPanel(new BorderLayout(5, 5));
        dirPanel.setBorder(new TitledBorder("Shared Directory"));
        dirPanel.add(sharedDirectoryField, BorderLayout.CENTER);

        // Right-side buttons for directory actions
        JPanel dirButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        dirButtons.add(changeDirectoryButton);
        dirButtons.add(removeDirectoryButton);
        dirPanel.add(dirButtons, BorderLayout.EAST);

        // File options
        JPanel optionsPanel = new JPanel(new GridBagLayout());
        optionsPanel.setBorder(new TitledBorder("File Options"));
        GridBagConstraints optGbc = new GridBagConstraints();
        optGbc.insets = new Insets(3, 3, 3, 3);
        optGbc.anchor = GridBagConstraints.WEST;

        optGbc.gridx = 0; optGbc.gridy = 0;
        optGbc.gridwidth = 2;
        optionsPanel.add(autoShareNewFilesBox, optGbc);

        optGbc.gridx = 0; optGbc.gridy = 1;
        optGbc.gridwidth = 2;
        optionsPanel.add(allowDuplicatesBox, optGbc);

        optGbc.gridx = 0; optGbc.gridy = 2;
        optGbc.gridwidth = 1;
        optionsPanel.add(new JLabel("Max File Size (MB):"), optGbc);
        optGbc.gridx = 1;
        optionsPanel.add(maxFileSizeSpinner, optGbc);

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(dirPanel, gbc);

        gbc.gridy = 1;
        panel.add(optionsPanel, gbc);

        return panel;
    }

    private JPanel createInterfacePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Appearance
        JPanel appearancePanel = new JPanel(new GridBagLayout());
        appearancePanel.setBorder(new TitledBorder("Appearance"));
        GridBagConstraints appGbc = new GridBagConstraints();
        appGbc.insets = new Insets(3, 3, 3, 3);
        appGbc.anchor = GridBagConstraints.WEST;

        appGbc.gridx = 0; appGbc.gridy = 0;
        appearancePanel.add(new JLabel("Theme:"), appGbc);
        appGbc.gridx = 1;
        appearancePanel.add(themeComboBox, appGbc);

        // Behavior
        JPanel behaviorPanel = new JPanel(new GridBagLayout());
        behaviorPanel.setBorder(new TitledBorder("Behavior"));
        GridBagConstraints behGbc = new GridBagConstraints();
        behGbc.insets = new Insets(3, 3, 3, 3);
        behGbc.anchor = GridBagConstraints.WEST;

        behGbc.gridx = 0; behGbc.gridy = 0;
        behGbc.gridwidth = 2;
        behaviorPanel.add(minimizeToTrayBox, behGbc);

        behGbc.gridy = 1;
        behaviorPanel.add(startMinimizedBox, behGbc);

        behGbc.gridy = 2;
        behaviorPanel.add(showNotificationsBox, behGbc);

        behGbc.gridx = 0; behGbc.gridy = 3;
        behGbc.gridwidth = 1;
        behaviorPanel.add(new JLabel("Refresh Interval (ms):"), behGbc);
        behGbc.gridx = 1;
        behaviorPanel.add(refreshIntervalSpinner, behGbc);

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(appearancePanel, gbc);

        gbc.gridy = 1;
        panel.add(behaviorPanel, gbc);

        return panel;
    }

    private JPanel createSecurityPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Password change
        JPanel passwordPanel = new JPanel(new GridBagLayout());
        passwordPanel.setBorder(new TitledBorder("Change Password"));
        GridBagConstraints pwdGbc = new GridBagConstraints();
        pwdGbc.insets = new Insets(3, 3, 3, 3);
        pwdGbc.anchor = GridBagConstraints.WEST;

        pwdGbc.gridx = 0; pwdGbc.gridy = 0;
        passwordPanel.add(new JLabel("Current Password:"), pwdGbc);
        pwdGbc.gridx = 1;
        passwordPanel.add(currentPasswordField, pwdGbc);

        pwdGbc.gridx = 0; pwdGbc.gridy = 1;
        passwordPanel.add(new JLabel("New Password:"), pwdGbc);
        pwdGbc.gridx = 1;
        passwordPanel.add(newPasswordField, pwdGbc);

        pwdGbc.gridx = 0; pwdGbc.gridy = 2;
        passwordPanel.add(new JLabel("Confirm Password:"), pwdGbc);
        pwdGbc.gridx = 1;
        passwordPanel.add(confirmPasswordField, pwdGbc);

        pwdGbc.gridx = 1; pwdGbc.gridy = 3;
        passwordPanel.add(changePasswordButton, pwdGbc);

        // Security options
        JPanel securityOptionsPanel = new JPanel(new GridBagLayout());
        securityOptionsPanel.setBorder(new TitledBorder("Security Options"));
        GridBagConstraints secGbc = new GridBagConstraints();
        secGbc.insets = new Insets(3, 3, 3, 3);
        secGbc.anchor = GridBagConstraints.WEST;

        secGbc.gridx = 0; secGbc.gridy = 0;
        securityOptionsPanel.add(rememberCredentialsBox, secGbc);

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(passwordPanel, gbc);
        JPanel accountManagementPanel = new JPanel(new GridBagLayout());
        accountManagementPanel.setBorder(new TitledBorder("Account Management"));
        GridBagConstraints amGbc = new GridBagConstraints();
        amGbc.insets = new Insets(3, 3, 3, 3);
        amGbc.anchor = GridBagConstraints.WEST;

        deleteAccountButton = new JButton("Delete Account");
        deleteAccountButton.setBackground(new Color(220, 38, 38));
        deleteAccountButton.setForeground(Color.WHITE);
        deleteAccountButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        deleteAccountButton.setFocusPainted(false);

        JLabel warningLabel = new JLabel("<html><small>⚠️ This action cannot be undone</small></html>");
        warningLabel.setForeground(new Color(220, 38, 38));

        amGbc.gridx = 0; amGbc.gridy = 0;
        accountManagementPanel.add(deleteAccountButton, amGbc);
        amGbc.gridy = 1;
        accountManagementPanel.add(warningLabel, amGbc);

        gbc.gridy = 1;
        panel.add(securityOptionsPanel, gbc);

        gbc.gridy = 2;
        panel.add(accountManagementPanel, gbc);

        return panel;
    }

    private JPanel createAdvancedPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Performance settings
        JPanel perfPanel = new JPanel(new GridBagLayout());
        perfPanel.setBorder(new TitledBorder("Performance"));
        GridBagConstraints perfGbc = new GridBagConstraints();
        perfGbc.insets = new Insets(3, 3, 3, 3);
        perfGbc.anchor = GridBagConstraints.WEST;

        perfGbc.gridx = 0; perfGbc.gridy = 0;
        perfPanel.add(new JLabel("Chunk Size (bytes):"), perfGbc);
        perfGbc.gridx = 1;
        perfPanel.add(chunkSizeSpinner, perfGbc);

        perfGbc.gridx = 0; perfGbc.gridy = 1;
        perfPanel.add(new JLabel("Retry Attempts:"), perfGbc);
        perfGbc.gridx = 1;
        perfPanel.add(retryAttemptsSpinner, perfGbc);

        // Logging settings
        JPanel logPanel = new JPanel(new GridBagLayout());
        logPanel.setBorder(new TitledBorder("Logging"));
        GridBagConstraints logGbc = new GridBagConstraints();
        logGbc.insets = new Insets(3, 3, 3, 3);
        logGbc.anchor = GridBagConstraints.WEST;

        logGbc.gridx = 0; logGbc.gridy = 0;
        logGbc.gridwidth = 2;
        logPanel.add(enableLoggingBox, logGbc);

        logGbc.gridx = 0; logGbc.gridy = 1;
        logGbc.gridwidth = 1;
        logPanel.add(new JLabel("Log Level:"), logGbc);
        logGbc.gridx = 1;
        logPanel.add(logLevelComboBox, logGbc);

        // Maintenance
        JPanel maintPanel = new JPanel(new FlowLayout());
        maintPanel.setBorder(new TitledBorder("Maintenance"));
        maintPanel.add(clearCacheButton);
        maintPanel.add(resetSettingsButton);

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(perfPanel, gbc);

        gbc.gridy = 1;
        panel.add(logPanel, gbc);

        gbc.gridy = 2;
        panel.add(maintPanel, gbc);



        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        panel.add(saveButton);
        panel.add(applyButton);
        panel.add(cancelButton);

        return panel;
    }

    private void setupEventListeners() {
        //  Change / Remove shared directory
        changeDirectoryButton.addActionListener(e -> changeSharedDirectory());
        removeDirectoryButton.addActionListener(e -> removeSharedDirectory());

        // Password change button
        changePasswordButton.addActionListener(e -> changePassword());

        // Maintenance buttons
        clearCacheButton.addActionListener(e -> clearCache());
        resetSettingsButton.addActionListener(e -> resetSettings());
        deleteAccountButton.addActionListener(e -> deleteAccount());

        // Control buttons
        saveButton.addActionListener(e -> saveSettings());
        applyButton.addActionListener(e -> applySettings());
        cancelButton.addActionListener(e -> cancelChanges());
    }

    private void populateFields() {
        // Network settings
        serverHostField.setText(appSettings.getProperty("server.host", "localhost"));
        serverPortSpinner.setValue(Integer.parseInt(appSettings.getProperty("server.port", "9090")));
        listenPortSpinner.setValue(Integer.parseInt(appSettings.getProperty("client.listen.port", "10000")));
        maxConnectionsSpinner.setValue(Integer.parseInt(appSettings.getProperty("network.max.connections", "50")));
        downloadTimeoutSpinner.setValue(Integer.parseInt(appSettings.getProperty("network.download.timeout", "30000")));

        // File settings
        sharedDirectoryField.setText(appSettings.getProperty("files.shared.directory", ""));
        autoShareNewFilesBox.setSelected(Boolean.parseBoolean(appSettings.getProperty("files.auto.share.new", "true")));
        maxFileSizeSpinner.setValue(Integer.parseInt(appSettings.getProperty("files.max.size.mb", "1024")));
        allowDuplicatesBox.setSelected(Boolean.parseBoolean(appSettings.getProperty("files.allow.duplicates", "false")));

        // UI settings
        themeComboBox.setSelectedItem(appSettings.getProperty("ui.theme", "Default"));
        minimizeToTrayBox.setSelected(Boolean.parseBoolean(appSettings.getProperty("ui.minimize.to.tray", "true")));
        startMinimizedBox.setSelected(Boolean.parseBoolean(appSettings.getProperty("ui.start.minimized", "false")));
        showNotificationsBox.setSelected(Boolean.parseBoolean(appSettings.getProperty("ui.show.notifications", "true")));
        refreshIntervalSpinner.setValue(Integer.parseInt(appSettings.getProperty("ui.refresh.interval", "5000")));

        // Security settings
        rememberCredentialsBox.setSelected(Boolean.parseBoolean(appSettings.getProperty("security.remember.credentials", "false")));

        // Advanced settings
        chunkSizeSpinner.setValue(Integer.parseInt(appSettings.getProperty("advanced.chunk.size", "8192")));
        retryAttemptsSpinner.setValue(Integer.parseInt(appSettings.getProperty("advanced.retry.attempts", "3")));
        enableLoggingBox.setSelected(Boolean.parseBoolean(appSettings.getProperty("advanced.enable.logging", "true")));
        logLevelComboBox.setSelectedItem(appSettings.getProperty("advanced.log.level", "INFO"));
    }

    // pick a new shared directory
    private void changeSharedDirectory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setCurrentDirectory(
                sharedDirectoryField.getText().isEmpty()
                        ? FileSystemView.getFileSystemView().getHomeDirectory()
                        : new File(sharedDirectoryField.getText())
        );

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDirectory = fileChooser.getSelectedFile();
            sharedDirectoryField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    // clear current shared directory (validation will enforce choosing a new one on save/apply)
    private void removeSharedDirectory() {
        if (sharedDirectoryField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "There is no shared directory set.",
                    "Nothing to Remove",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "This will clear the current shared directory setting.\n" +
                        "You will need to choose a new folder before saving.",
                "Remove Shared Directory",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            sharedDirectoryField.setText("");
            // autoShareNewFilesBox.setSelected(false);
        }
    }

    private void changePassword() {
        String currentPassword = new String(currentPasswordField.getPassword());
        String newPassword = new String(newPasswordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());

        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all password fields.",
                    "Incomplete Information", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(this, "New passwords do not match.",
                    "Password Mismatch", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (newPassword.length() < 6) {
            JOptionPane.showMessageDialog(this, "Password must be at least 6 characters long.",
                    "Weak Password", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // TODO: Implement actual password change logic with AccountService
        JOptionPane.showMessageDialog(this, "Password change functionality would be implemented here.",
                "Not Implemented", JOptionPane.INFORMATION_MESSAGE);

        // Clear password fields
        currentPasswordField.setText("");
        newPasswordField.setText("");
        confirmPasswordField.setText("");
    }

    private void clearCache() {
        int choice = JOptionPane.showConfirmDialog(this,
                "This will clear all cached data. Continue?",
                "Clear Cache",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            try {
                // Clear cache directories and files
                Path cachePath = Paths.get("cache");
                if (Files.exists(cachePath)) {
                    Files.walk(cachePath)
                            .map(Path::toFile)
                            .forEach(File::delete);
                }

                JOptionPane.showMessageDialog(this, "Cache cleared successfully.",
                        "Cache Cleared", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error clearing cache: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void resetSettings() {
        int choice = JOptionPane.showConfirmDialog(this,
                "This will reset all settings to default values. Continue?",
                "Reset Settings",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            setDefaultSettings();
            populateFields();
            JOptionPane.showMessageDialog(this, "Settings reset to defaults.",
                    "Settings Reset", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void saveSettings() {
        if (applySettings()) {
            saveSettingsToFile();
            JOptionPane.showMessageDialog(this, "Settings saved successfully.",
                    "Settings Saved", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private boolean applySettings() {
        try {
            // Validate settings before applying
            if (!validateSettings()) {
                return false;
            }

            // Update properties with current field values
            if (!applyDirectoryChange()) {
                return false;
            }
            updatePropertiesFromFields();

            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error applying settings: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private boolean validateSettings() {
        // Validate server host
        if (serverHostField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Server host cannot be empty.",
                    "Invalid Setting", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // Validate shared directory
        if (sharedDirectoryField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Shared directory must be specified.",
                    "Invalid Setting", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        File sharedDir = new File(sharedDirectoryField.getText());
        if (!sharedDir.exists()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Shared directory does not exist. Create it?",
                    "Create Directory",
                    JOptionPane.YES_NO_OPTION);

            if (choice == JOptionPane.YES_OPTION) {
                if (!sharedDir.mkdirs()) {
                    JOptionPane.showMessageDialog(this, "Could not create shared directory.",
                            "Directory Creation Failed", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            } else {
                return false;
            }
        }

        // Validate port ranges
        int serverPort = (Integer) serverPortSpinner.getValue();
        int listenPort = (Integer) listenPortSpinner.getValue();

        if (serverPort == listenPort) {
            JOptionPane.showMessageDialog(this, "Server port and listen port cannot be the same.",
                    "Port Conflict", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }
    private String getCurrentSharedDirectory() {
        try {
            ClientConfigurationService configService = new ClientConfigurationService("client_config.csv");
            return configService.getSharedDirectory(currentUser.getUsername());
        } catch (Exception e) {
            return "";
        }
    }

    private void updatePeerClientDirectory(String newDirectory) {
        // Get reference to peer client through UIIntegrationHelper
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Signal to restart file watcher and update file handler
                return null;
            }

            @Override
            protected void done() {
                UIIntegrationHelper.updateFileList();
            }
        };
        worker.execute();
    }
    private void updatePropertiesFromFields() {
        // Network settings
        appSettings.setProperty("server.host", serverHostField.getText().trim());
        appSettings.setProperty("server.port", serverPortSpinner.getValue().toString());
        appSettings.setProperty("client.listen.port", listenPortSpinner.getValue().toString());
        appSettings.setProperty("network.max.connections", maxConnectionsSpinner.getValue().toString());
        appSettings.setProperty("network.download.timeout", downloadTimeoutSpinner.getValue().toString());

        // File settings
        appSettings.setProperty("files.shared.directory", sharedDirectoryField.getText().trim());
        appSettings.setProperty("files.auto.share.new", String.valueOf(autoShareNewFilesBox.isSelected()));
        appSettings.setProperty("files.max.size.mb", maxFileSizeSpinner.getValue().toString());
        appSettings.setProperty("files.allow.duplicates", String.valueOf(allowDuplicatesBox.isSelected()));

        // UI settings
        appSettings.setProperty("ui.theme", (String) themeComboBox.getSelectedItem());
        appSettings.setProperty("ui.minimize.to.tray", String.valueOf(minimizeToTrayBox.isSelected()));
        appSettings.setProperty("ui.start.minimized", String.valueOf(startMinimizedBox.isSelected()));
        appSettings.setProperty("ui.show.notifications", String.valueOf(showNotificationsBox.isSelected()));
        appSettings.setProperty("ui.refresh.interval", refreshIntervalSpinner.getValue().toString());

        // Security settings
        appSettings.setProperty("security.remember.credentials", String.valueOf(rememberCredentialsBox.isSelected()));

        // Advanced settings
        appSettings.setProperty("advanced.chunk.size", chunkSizeSpinner.getValue().toString());
        appSettings.setProperty("advanced.retry.attempts", retryAttemptsSpinner.getValue().toString());
        appSettings.setProperty("advanced.enable.logging", String.valueOf(enableLoggingBox.isSelected()));
        appSettings.setProperty("advanced.log.level", (String) logLevelComboBox.getSelectedItem());
    }

    private void saveSettingsToFile() {
        try (FileOutputStream fos = new FileOutputStream(settingsFilePath)) {
            appSettings.store(fos, "Application Settings - Generated on " + new java.util.Date());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error saving settings file: " + e.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cancelChanges() {
        // Reload settings from file and repopulate fields
        loadSettings();
        populateFields();
    }

    private void deleteAccount() {
        // Multi-step confirmation
        int firstConfirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete your account?\n" +
                        "This will permanently remove:\n" +
                        "• Your user account\n" +
                        "• All your statistics\n" +
                        "• Your shared files configuration\n\n" +
                        "This action cannot be undone!",
                "Delete Account - Confirmation 1/2",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (firstConfirm != JOptionPane.YES_OPTION) {
            return;
        }

        // Password confirmation
        String password = JOptionPane.showInputDialog(
                this,
                "Enter your password to confirm account deletion:",
                "Delete Account - Confirmation 2/2",
                JOptionPane.WARNING_MESSAGE
        );

        if (password == null || password.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Account deletion cancelled.",
                    "Cancelled", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Final confirmation with typing
        String confirmText = JOptionPane.showInputDialog(
                this,
                "Type 'DELETE MY ACCOUNT' to confirm (case sensitive):",
                "Final Confirmation",
                JOptionPane.WARNING_MESSAGE
        );

        if (!"DELETE MY ACCOUNT".equals(confirmText)) {
            JOptionPane.showMessageDialog(this, "Confirmation text does not match. Account deletion cancelled.",
                    "Cancelled", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Execute deletion
        deleteAccountButton.setEnabled(false);
        deleteAccountButton.setText("Deleting...");

        SwingWorker<Boolean, Void> deleteWorker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    // First verify password with server
                    Transport tempTransport = new TCPTransport("localhost", 9090);
                    tempTransport.sendLine("LOGIN " + currentUser.getUsername() + " " + password);
                    String loginResponse = tempTransport.readLine();

                    if (!loginResponse.startsWith("LOGIN_SUCCESS")) {
                        tempTransport.close();
                        throw new Exception("Invalid password");
                    }

                    // Send delete account command
                    tempTransport.sendLine("DELETE_ACCOUNT " + currentUser.getUsername());
                    String deleteResponse = tempTransport.readLine();
                    tempTransport.close();

                    return "DELETE_SUCCESS".equals(deleteResponse);

                } catch (Exception e) {
                    throw e;
                }
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        JOptionPane.showMessageDialog(SettingsPanel.this,
                                "Your account has been successfully deleted.\nThe application will now close.",
                                "Account Deleted",
                                JOptionPane.INFORMATION_MESSAGE);

                        // Close application
                        System.exit(0);
                    } else {
                        JOptionPane.showMessageDialog(SettingsPanel.this,
                                "Failed to delete account. Please try again or contact support.",
                                "Deletion Failed",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                            "Error during account deletion: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    deleteAccountButton.setEnabled(true);
                    deleteAccountButton.setText("Delete Account");
                }
            }
        };

        deleteWorker.execute();
    }

    // Public methods for external access
    public String getServerHost() {
        return appSettings.getProperty("server.host", "localhost");
    }

    public int getServerPort() {
        return Integer.parseInt(appSettings.getProperty("server.port", "9090"));
    }

    public int getListenPort() {
        return Integer.parseInt(appSettings.getProperty("client.listen.port", "10000"));
    }

    public String getSharedDirectory() {
        return appSettings.getProperty("files.shared.directory", "");
    }

    public boolean isAutoShareEnabled() {
        return Boolean.parseBoolean(appSettings.getProperty("files.auto.share.new", "true"));
    }

    public int getChunkSize() {
        return Integer.parseInt(appSettings.getProperty("advanced.chunk.size", "8192"));
    }

    public int getRetryAttempts() {
        return Integer.parseInt(appSettings.getProperty("advanced.retry.attempts", "3"));
    }

    public boolean isLoggingEnabled() {
        return Boolean.parseBoolean(appSettings.getProperty("advanced.enable.logging", "true"));
    }

    public String getLogLevel() {
        return appSettings.getProperty("advanced.log.level", "INFO");
    }

    public Properties getSettings() {
        return new Properties(appSettings);
    }

    // Method to update user reference
    public void updateUser(User user) {
        this.currentUser = user;
    }

    private boolean applyDirectoryChange() {
        String newDirectory = sharedDirectoryField.getText().trim();
        String currentDirectory = getCurrentSharedDirectory();

        if (!newDirectory.equals(currentDirectory)) {
            try {
                // Update client configuration
                ClientConfigurationService configService = new ClientConfigurationService("client_config.csv");
                configService.saveSharedDirectory(currentUser.getUsername(), newDirectory);

                // Notify the file manager to update

                if (UIIntegrationHelper.getFileManagerPanel() != null) {
                    // You'll need to add this method to FileManagerPanel
                    UIIntegrationHelper.getFileManagerPanel().updateSharedDirectory(newDirectory);
                }

                // Update the peer client's file handler
                updatePeerClientDirectory(newDirectory);

                UIIntegrationHelper.showInfo("Shared directory updated successfully. Restart may be required for full effect.");
                return true;

            } catch (IOException e) {
                UIIntegrationHelper.showError("Failed to update shared directory: " + e.getMessage());
                return false;
            }
        }
        return true;
    }

}
