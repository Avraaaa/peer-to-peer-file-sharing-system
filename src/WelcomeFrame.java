import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;

public class WelcomeFrame extends JFrame {
    private JPanel mainPanel;
    private JTabbedPane tabbedPane;
    private JPanel loginPanel;
    private JPanel signupPanel;

    // Login components
    private JTextField loginUsernameField;
    private JPasswordField loginPasswordField;
    private JButton loginButton;
    private JCheckBox rememberMeCheck;

    // Signup components
    private JTextField signupUsernameField;
    private JPasswordField signupPasswordField;
    private JPasswordField confirmPasswordField;
    private JButton signupButton;
    private JProgressBar passwordStrengthBar;
    private JLabel passwordStrengthLabel;

    // Status components
    private JLabel statusLabel;
    private JProgressBar connectionProgress;

    private Transport serverTransport;

    public WelcomeFrame() {
        initializeFrame();
        createComponents();
        setupLayout();
        setupEventHandlers();
        connectToServer();
    }

    private void initializeFrame() {
        setTitle("P2P File Sharing - Welcome");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(450, 600);
        setLocationRelativeTo(null);
        setResizable(false);

        // Modern Look and Feel
        try {
           // UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel());
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createComponents() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBackground(new Color(248, 250, 252));

        createWelcomeHeader();
        createAuthenticationTabs();
        createStatusArea();
    }

    private void createWelcomeHeader() {
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(new Color(248, 250, 252));
        headerPanel.setBorder(new EmptyBorder(40, 40, 30, 40));

        // Logo/Title
        JLabel titleLabel = new JLabel("P2P File Sharing");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(new Color(30, 41, 59));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Share files seamlessly across the network");
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        subtitleLabel.setForeground(new Color(100, 116, 139));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(8));
        headerPanel.add(subtitleLabel);

        mainPanel.add(headerPanel, BorderLayout.NORTH);
    }

    private void createAuthenticationTabs() {
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(Color.WHITE);
        tabbedPane.setFont(new Font("Arial", Font.PLAIN, 14));

        createLoginPanel();
        createSignupPanel();

        tabbedPane.addTab("Login", loginPanel);
        tabbedPane.addTab("Sign Up", signupPanel);

        JPanel tabContainer = new JPanel(new BorderLayout());
        tabContainer.setBorder(new EmptyBorder(0, 40, 20, 40));
        tabContainer.setBackground(new Color(248, 250, 252));
        tabContainer.add(tabbedPane, BorderLayout.CENTER);

        mainPanel.add(tabContainer, BorderLayout.CENTER);
    }

    private void createLoginPanel() {
        loginPanel = new JPanel();
        loginPanel.setLayout(new BoxLayout(loginPanel, BoxLayout.Y_AXIS));
        loginPanel.setBackground(Color.WHITE);
        loginPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Username field
        JLabel usernameLabel = createStyledLabel("Username");
        loginUsernameField = createStyledTextField();
        loginUsernameField.setToolTipText("Enter your username");

        // Password field
        JLabel passwordLabel = createStyledLabel("Password");
        loginPasswordField = createStyledPasswordField();
        loginPasswordField.setToolTipText("Enter your password");

        // Remember me checkbox
        rememberMeCheck = new JCheckBox("Remember me");
        rememberMeCheck.setBackground(Color.WHITE);
        rememberMeCheck.setFont(new Font("Arial", Font.PLAIN, 12));
        rememberMeCheck.setForeground(new Color(100, 116, 139));

        // Login button
        loginButton = createStyledButton("Login", new Color(59, 130, 246));
        loginButton.setEnabled(false);

        // Forgot password link
        JLabel forgotPasswordLabel = new JLabel("<html><u>Forgot your password?</u></html>");
        forgotPasswordLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        forgotPasswordLabel.setForeground(new Color(59, 130, 246));
        forgotPasswordLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        forgotPasswordLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Add components
        loginPanel.add(usernameLabel);
        loginPanel.add(Box.createVerticalStrut(5));
        loginPanel.add(loginUsernameField);
        loginPanel.add(Box.createVerticalStrut(15));
        loginPanel.add(passwordLabel);
        loginPanel.add(Box.createVerticalStrut(5));
        loginPanel.add(loginPasswordField);
        loginPanel.add(Box.createVerticalStrut(15));
        loginPanel.add(rememberMeCheck);
        loginPanel.add(Box.createVerticalStrut(20));
        loginPanel.add(loginButton);
        loginPanel.add(Box.createVerticalStrut(15));
        loginPanel.add(forgotPasswordLabel);
    }

    private void createSignupPanel() {
        signupPanel = new JPanel();
        signupPanel.setLayout(new BoxLayout(signupPanel, BoxLayout.Y_AXIS));
        signupPanel.setBackground(Color.WHITE);
        signupPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Username field
        JLabel usernameLabel = createStyledLabel("Choose Username");
        signupUsernameField = createStyledTextField();
        signupUsernameField.setToolTipText("Choose a unique username");

        // Password field
        JLabel passwordLabel = createStyledLabel("Create Password");
        signupPasswordField = createStyledPasswordField();
        signupPasswordField.setToolTipText("Create a strong password");

        // Password strength indicator
        passwordStrengthBar = new JProgressBar(0, 100);
        passwordStrengthBar.setStringPainted(false);
        passwordStrengthBar.setPreferredSize(new Dimension(0, 6));

        passwordStrengthLabel = new JLabel("Password strength: Weak");
        passwordStrengthLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        passwordStrengthLabel.setForeground(new Color(220, 38, 38));

        // Confirm password field
        JLabel confirmPasswordLabel = createStyledLabel("Confirm Password");
        confirmPasswordField = createStyledPasswordField();
        confirmPasswordField.setToolTipText("Re-enter your password");

        // Signup button
        signupButton = createStyledButton("Create Account", new Color(34, 197, 94));
        signupButton.setEnabled(false);

        // Terms of service
        JLabel termsLabel = new JLabel("<html><center>By signing up, you agree to our<br><u>Terms of Service</u> and <u>Privacy Policy</u></center></html>");
        termsLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        termsLabel.setForeground(new Color(100, 116, 139));
        termsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Add components
        signupPanel.add(usernameLabel);
        signupPanel.add(Box.createVerticalStrut(5));
        signupPanel.add(signupUsernameField);
        signupPanel.add(Box.createVerticalStrut(15));
        signupPanel.add(passwordLabel);
        signupPanel.add(Box.createVerticalStrut(5));
        signupPanel.add(signupPasswordField);
        signupPanel.add(Box.createVerticalStrut(8));
        signupPanel.add(passwordStrengthBar);
        signupPanel.add(Box.createVerticalStrut(3));
        signupPanel.add(passwordStrengthLabel);
        signupPanel.add(Box.createVerticalStrut(15));
        signupPanel.add(confirmPasswordLabel);
        signupPanel.add(Box.createVerticalStrut(5));
        signupPanel.add(confirmPasswordField);
        signupPanel.add(Box.createVerticalStrut(20));
        signupPanel.add(signupButton);
        signupPanel.add(Box.createVerticalStrut(15));
        signupPanel.add(termsLabel);
    }

    private void createStatusArea() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(new Color(248, 250, 252));
        statusPanel.setBorder(new EmptyBorder(0, 40, 30, 40));

        statusLabel = new JLabel("Connecting to server...");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(100, 116, 139));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        connectionProgress = new JProgressBar();
        connectionProgress.setIndeterminate(true);
        connectionProgress.setPreferredSize(new Dimension(0, 4));
        connectionProgress.setVisible(true);

        statusPanel.add(statusLabel, BorderLayout.NORTH);
        statusPanel.add(Box.createVerticalStrut(10), BorderLayout.CENTER);
        statusPanel.add(connectionProgress, BorderLayout.SOUTH);

        mainPanel.add(statusPanel, BorderLayout.SOUTH);
    }

    private JLabel createStyledLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", Font.BOLD, 13));
        label.setForeground(new Color(30, 41, 59));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JTextField createStyledTextField() {
        JTextField field = new JTextField();
        field.setPreferredSize(new Dimension(0, 40));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        field.setFont(new Font("Arial", Font.PLAIN, 14));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(209, 213, 219), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        return field;
    }

    private JPasswordField createStyledPasswordField() {
        JPasswordField field = new JPasswordField();
        field.setPreferredSize(new Dimension(0, 40));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        field.setFont(new Font("Arial", Font.PLAIN, 14));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(209, 213, 219), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        return field;
    }

    private JButton createStyledButton(String text, Color backgroundColor) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(0, 45));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(backgroundColor);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            Color originalColor = backgroundColor;

            public void mouseEntered(java.awt.event.MouseEvent evt) {
                if (button.isEnabled()) {
                    button.setBackground(originalColor.darker());
                }
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                if (button.isEnabled()) {
                    button.setBackground(originalColor);
                }
            }
        });

        return button;
    }

    private void setupLayout() {
        setContentPane(mainPanel);
    }

    private void setupEventHandlers() {
        // Login form validation
        KeyListener loginFormValidator = new KeyListener() {
            public void keyTyped(KeyEvent e) {}
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && loginButton.isEnabled()) {
                    handleLogin();
                }
            }
            public void keyReleased(KeyEvent e) {
                validateLoginForm();
            }
        };

        loginUsernameField.addKeyListener(loginFormValidator);
        loginPasswordField.addKeyListener(loginFormValidator);

        // Signup form validation
        KeyListener signupFormValidator = new KeyListener() {
            public void keyTyped(KeyEvent e) {}
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && signupButton.isEnabled()) {
                    handleSignup();
                }
            }
            public void keyReleased(KeyEvent e) {
                validateSignupForm();
                if (e.getSource() == signupPasswordField) {
                    updatePasswordStrength();
                }
            }
        };

        signupUsernameField.addKeyListener(signupFormValidator);
        signupPasswordField.addKeyListener(signupFormValidator);
        confirmPasswordField.addKeyListener(signupFormValidator);

        // Button actions
        loginButton.addActionListener(e -> handleLogin());
        signupButton.addActionListener(e -> handleSignup());
    }

    private void validateLoginForm() {
        boolean valid = !loginUsernameField.getText().trim().isEmpty() &&
                loginPasswordField.getPassword().length > 0;
        loginButton.setEnabled(valid);
    }

    private void validateSignupForm() {
        String username = signupUsernameField.getText().trim();
        String password = new String(signupPasswordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());

        boolean valid = !username.isEmpty() &&
                password.length() >= 4 &&
                password.equals(confirmPassword);
        signupButton.setEnabled(valid);
    }

    private void updatePasswordStrength() {
        String password = new String(signupPasswordField.getPassword());
        int strength = calculatePasswordStrength(password);

        passwordStrengthBar.setValue(strength);

        if (strength < 30) {
            passwordStrengthBar.setForeground(new Color(220, 38, 38));
            passwordStrengthLabel.setText("Password strength: Weak");
            passwordStrengthLabel.setForeground(new Color(220, 38, 38));
        } else if (strength < 70) {
            passwordStrengthBar.setForeground(new Color(245, 158, 11));
            passwordStrengthLabel.setText("Password strength: Medium");
            passwordStrengthLabel.setForeground(new Color(245, 158, 11));
        } else {
            passwordStrengthBar.setForeground(new Color(34, 197, 94));
            passwordStrengthLabel.setText("Password strength: Strong");
            passwordStrengthLabel.setForeground(new Color(34, 197, 94));
        }
    }

    private int calculatePasswordStrength(String password) {
        int strength = 0;

        if (password.length() >= 8) strength += 25;
        if (password.matches(".*[a-z].*")) strength += 25;
        if (password.matches(".*[A-Z].*")) strength += 25;
        if (password.matches(".*[0-9].*")) strength += 25;

        return Math.min(100, strength);
    }

    private void connectToServer() {
        SwingWorker<Boolean, Void> connectionWorker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    serverTransport = new TCPTransport("localhost", 9090);
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    boolean connected = get();
                    connectionProgress.setVisible(false);

                    if (connected) {
                        statusLabel.setText("Connected to server");
                        statusLabel.setForeground(new Color(34, 197, 94));
                        enableForms(true);
                    } else {
                        statusLabel.setText("Failed to connect to server");
                        statusLabel.setForeground(new Color(220, 38, 38));
                        enableForms(false);
                        showRetryOption();
                    }
                } catch (Exception e) {
                    statusLabel.setText("Connection error");
                    statusLabel.setForeground(new Color(220, 38, 38));
                    enableForms(false);
                }
            }
        };

        connectionWorker.execute();
    }

    private void enableForms(boolean enabled) {
        loginUsernameField.setEnabled(enabled);
        loginPasswordField.setEnabled(enabled);
        signupUsernameField.setEnabled(enabled);
        signupPasswordField.setEnabled(enabled);
        confirmPasswordField.setEnabled(enabled);

        if (enabled) {
            validateLoginForm();
            validateSignupForm();
        } else {
            loginButton.setEnabled(false);
            signupButton.setEnabled(false);
        }
    }

    private void showRetryOption() {
        int option = JOptionPane.showConfirmDialog(
                this,
                "Failed to connect to the server. Would you like to retry?",
                "Connection Failed",
                JOptionPane.YES_NO_OPTION
        );

        if (option == JOptionPane.YES_OPTION) {
            statusLabel.setText("Connecting to server...");
            connectionProgress.setVisible(true);
            connectToServer();
        } else {
            System.exit(0);
        }
    }

    private void handleLogin() {
        if (serverTransport == null) {
            showError("Not connected to server");
            return;
        }

        String username = loginUsernameField.getText().trim();
        String password = new String(loginPasswordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password");
            return;
        }

        loginButton.setEnabled(false);
        loginButton.setText("Logging in...");

        SwingWorker<User, Void> loginWorker = new SwingWorker<User, Void>() {
            @Override
            protected User doInBackground() throws Exception {
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

                        if (isAdmin) {
                            return new AdminUser("", dStats, uStats);
                        } else {
                            return new RegularUser(u, "", dStats, uStats);
                        }
                    } else {
                        throw new IOException(response != null ?
                                response.replace("LOGIN_FAIL ", "") : "No response from server");
                    }
                } catch (IOException e) {
                    throw e;
                }
            }

            @Override
            protected void done() {
                try {
                    User user = get();

                    // Show first-time setup if needed
                    FirstTimeSetupDialog setupDialog = new FirstTimeSetupDialog(WelcomeFrame.this, user);
                    String sharedDirectory = setupDialog.getSharedDirectory();

                    if (sharedDirectory != null) {
                        // Initialize PeerClient and show main application
                        initializePeerClient(user, sharedDirectory);
                    }

                } catch (Exception e) {
                    showError("Login failed: " + e.getMessage());
                } finally {
                    loginButton.setEnabled(true);
                    loginButton.setText("Login");
                }
            }
        };

        loginWorker.execute();
    }

    private void handleSignup() {
        if (serverTransport == null) {
            showError("Not connected to server");
            return;
        }

        String username = signupUsernameField.getText().trim();
        String password = new String(signupPasswordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            return;
        }

        if (password.length() < 4) {
            showError("Password must be at least 4 characters long");
            return;
        }

        signupButton.setEnabled(false);
        signupButton.setText("Creating account...");

        SwingWorker<Boolean, Void> signupWorker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    serverTransport.sendLine("SIGNUP " + username + " " + password);
                    String response = serverTransport.readLine();

                    if ("SIGNUP_SUCCESS".equals(response)) {
                        return true;
                    } else {
                        throw new IOException(response != null ?
                                response.replace("SIGNUP_FAIL ", "") : "No response from server");
                    }
                } catch (IOException e) {
                    throw e;
                }
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        JOptionPane.showMessageDialog(
                                WelcomeFrame.this,
                                "Account created successfully! Please log in.",
                                "Account Created",
                                JOptionPane.INFORMATION_MESSAGE
                        );

                        // Switch to login tab and clear signup fields
                        tabbedPane.setSelectedIndex(0);
                        signupUsernameField.setText("");
                        signupPasswordField.setText("");
                        confirmPasswordField.setText("");
                        loginUsernameField.setText(username);
                        loginUsernameField.requestFocus();
                    }
                } catch (Exception e) {
                    showError("Signup failed: " + e.getMessage());
                } finally {
                    signupButton.setEnabled(true);
                    signupButton.setText("Create Account");
                }
            }
        };

        signupWorker.execute();
    }

    private void initializePeerClient(User user, String sharedDirectory) {
        SwingWorker<Void, Void> initWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                SwingUtilities.invokeLater(() -> {
                    dispose(); // Close welcome frame
                    UIMainLauncher.launchMainApplication(user, serverTransport, sharedDirectory);
                });
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        showError("Failed to initialize application: " + e.getMessage());
                        setVisible(true); // Show welcome frame again
                    });
                }
            }
        };

        initWorker.execute();
    }

    private int findAvailablePortPair() {
        for (int port = 10000; port <= 11000; port++) {
            if (arePortsAvailable(port, port + 1)) {
                return port;
            }
        }
        return -1;
    }

    private boolean arePortsAvailable(int tcpPort, int udpPort) {
        try (java.net.ServerSocket tcpSocket = new java.net.ServerSocket(tcpPort);
             java.net.DatagramSocket udpSocket = new java.net.DatagramSocket(udpPort)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
    }
}