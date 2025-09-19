import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FirstTimeSetupDialog extends JDialog {
    private User user;
    private String selectedDirectory;
    private boolean setupCompleted = false;

    // Components
    private JLabel welcomeLabel;
    private JTextField directoryField;
    private JButton browseButton;
    private JButton createButton;
    private JButton continueButton;
    private JLabel statusLabel;

    public FirstTimeSetupDialog(Frame parent, User user) {
        super(parent, "First-Time Setup", true);
        this.user = user;

        // Check if setup is needed
        if (isSetupNeeded()) {
            initializeDialog();
            createComponents();
            setupLayout();
            setupEventHandlers();
            setVisible(true);
        } else {
            loadExistingConfiguration();
            setupCompleted = true;
        }
    }

    private void initializeDialog() {
        setSize(500, 350);
        setLocationRelativeTo(getParent());
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setResizable(false);

        getContentPane().setBackground(Color.WHITE);
    }

    private void createComponents() {
        // Welcome section
        welcomeLabel = new JLabel("<html><center><h2>Welcome, " + user.getUsername() + "!</h2>" +
                "<p>Let's set up your shared files directory.</p></center></html>");
        welcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        welcomeLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        welcomeLabel.setForeground(new Color(30, 41, 59));

        // Directory selection
        JLabel instructionLabel = new JLabel("Choose where to store your shared files:");
        instructionLabel.setFont(new Font("Arial", Font.BOLD, 13));
        instructionLabel.setForeground(new Color(30, 41, 59));

        directoryField = new JTextField();
        directoryField.setPreferredSize(new Dimension(0, 35));
        directoryField.setFont(new Font("Arial", Font.PLAIN, 12));
        directoryField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(209, 213, 219), 1),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        // Set default directory
        String defaultDir = System.getProperty("user.home") + File.separator + user.getUsername() + "_files";
        directoryField.setText(defaultDir);

        browseButton = new JButton("Browse");
        browseButton.setPreferredSize(new Dimension(80, 35));
        browseButton.setFont(new Font("Arial", Font.PLAIN, 12));
        browseButton.setBackground(new Color(107, 114, 128));
        browseButton.setForeground(Color.WHITE);
        browseButton.setBorder(BorderFactory.createEmptyBorder());
        browseButton.setFocusPainted(false);

        createButton = new JButton("Create Directory");
        createButton.setPreferredSize(new Dimension(130, 35));
        createButton.setFont(new Font("Arial", Font.PLAIN, 12));
        createButton.setBackground(new Color(59, 130, 246));
        createButton.setForeground(Color.WHITE);
        createButton.setBorder(BorderFactory.createEmptyBorder());
        createButton.setFocusPainted(false);

        continueButton = new JButton("Continue");
        continueButton.setPreferredSize(new Dimension(120, 40));
        continueButton.setFont(new Font("Arial", Font.BOLD, 14));
        continueButton.setBackground(new Color(34, 197, 94));
        continueButton.setForeground(Color.WHITE);
        continueButton.setBorder(BorderFactory.createEmptyBorder());
        continueButton.setFocusPainted(false);
        continueButton.setEnabled(false);

        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(30, 40, 30, 40));
        mainPanel.setBackground(Color.WHITE);

        // Welcome section
        mainPanel.add(welcomeLabel);
        mainPanel.add(Box.createVerticalStrut(30));

        // Instruction
        JLabel instructionLabel = new JLabel("Choose where to store your shared files:");
        instructionLabel.setFont(new Font("Arial", Font.BOLD, 13));
        instructionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(instructionLabel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Directory selection panel
        JPanel directoryPanel = new JPanel(new BorderLayout(10, 0));
        directoryPanel.setBackground(Color.WHITE);
        directoryPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        directoryPanel.add(directoryField, BorderLayout.CENTER);
        directoryPanel.add(browseButton, BorderLayout.EAST);

        mainPanel.add(directoryPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Create directory button
        JPanel createPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        createPanel.setBackground(Color.WHITE);
        createPanel.add(createButton);
        mainPanel.add(createPanel);

        mainPanel.add(Box.createVerticalStrut(15));

        // Status label
        mainPanel.add(statusLabel);

        mainPanel.add(Box.createVerticalGlue());

        // Continue button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.add(continueButton);

        mainPanel.add(buttonPanel);

        add(mainPanel, BorderLayout.CENTER);

        // Check initial directory
        checkDirectoryExists();
    }

    private void setupEventHandlers() {
        browseButton.addActionListener(this::handleBrowse);
        createButton.addActionListener(this::handleCreateDirectory);
        continueButton.addActionListener(this::handleContinue);

        // Check directory when text changes
        directoryField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { checkDirectoryExists(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { checkDirectoryExists(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { checkDirectoryExists(); }
        });
    }

    private void handleBrowse(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select Shared Files Directory");

        // Set current directory if valid
        String currentPath = directoryField.getText();
        if (!currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.getParentFile() != null && currentDir.getParentFile().exists()) {
                fileChooser.setCurrentDirectory(currentDir.getParentFile());
            }
        }

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = fileChooser.getSelectedFile();
            directoryField.setText(selectedDir.getAbsolutePath());
        }
    }

    private void handleCreateDirectory(ActionEvent e) {
        String directoryPath = directoryField.getText().trim();
        if (directoryPath.isEmpty()) {
            showStatus("Please enter a directory path", Color.RED);
            return;
        }

        try {
            Path path = Paths.get(directoryPath);
            Files.createDirectories(path);
            showStatus("Directory created successfully!", new Color(34, 197, 94));
            checkDirectoryExists();
        } catch (Exception ex) {
            showStatus("Failed to create directory: " + ex.getMessage(), Color.RED);
        }
    }

    private void handleContinue(ActionEvent e) {
        String directoryPath = directoryField.getText().trim();
        if (directoryPath.isEmpty()) {
            showStatus("Please select a directory", Color.RED);
            return;
        }

        File dir = new File(directoryPath);
        if (!dir.exists()) {
            showStatus("Directory does not exist. Please create it first.", Color.RED);
            return;
        }

        if (!dir.canRead() || !dir.canWrite()) {
            showStatus("Directory is not accessible. Please choose another.", Color.RED);
            return;
        }

        // Save configuration
        try {
            ClientConfigurationService configService = new ClientConfigurationService("client_config.csv");
            configService.saveSharedDirectory(user.getUsername(), directoryPath);

            selectedDirectory = directoryPath;
            setupCompleted = true;
            dispose();

        } catch (IOException ex) {
            showStatus("Failed to save configuration: " + ex.getMessage(), Color.RED);
        }
    }

    private void checkDirectoryExists() {
        String directoryPath = directoryField.getText().trim();
        if (directoryPath.isEmpty()) {
            showStatus("Enter a directory path", new Color(107, 114, 128));
            continueButton.setEnabled(false);
            return;
        }

        File dir = new File(directoryPath);
        if (dir.exists() && dir.isDirectory()) {
            if (dir.canRead() && dir.canWrite()) {
                showStatus("Directory is ready to use", new Color(34, 197, 94));
                continueButton.setEnabled(true);
            } else {
                showStatus("Directory is not accessible", Color.RED);
                continueButton.setEnabled(false);
            }
        } else {
            showStatus("Directory does not exist - click 'Create Directory'", new Color(245, 158, 11));
            continueButton.setEnabled(false);
        }
    }

    private void showStatus(String message, Color color) {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
    }

    private boolean isSetupNeeded() {
        try {
            ClientConfigurationService configService = new ClientConfigurationService("client_config.csv");
            String existingDir = configService.getSharedDirectory(user.getUsername());

            if (existingDir != null) {
                File dir = new File(existingDir);
                return !dir.exists() || !dir.canRead() || !dir.canWrite();
            }
            return true;

        } catch (Exception e) {
            return true;
        }
    }

    private void loadExistingConfiguration() {
        try {
            ClientConfigurationService configService = new ClientConfigurationService("client_config.csv");
            selectedDirectory = configService.getSharedDirectory(user.getUsername());
        } catch (Exception e) {
            selectedDirectory = null;
        }
    }

    public String getSharedDirectory() {
        return setupCompleted ? selectedDirectory : null;
    }

    public boolean isSetupCompleted() {
        return setupCompleted;
    }
}
