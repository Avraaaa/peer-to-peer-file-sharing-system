import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.util.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import javax.swing.ImageIcon;


public class MainApplicationFrame extends JFrame {
    private User currentUser;
    private Transport serverTransport;
    private PeerClient peerClient;

    // UI Components
    private JPanel mainContentPanel;
    private CardLayout cardLayout;
    private JPanel sideNavPanel;
    private JLabel userProfileLabel;
    private JLabel connectionStatusLabel;

    // Panel instances
    private DashboardPanel dashboardPanel;
    private SearchPanel searchPanel;
    private PeerBrowserPanel peerBrowserPanel;
    private FileManagerPanel fileManagerPanel;
    private StatisticsPanel statisticsPanel;
    private SettingsPanel settingsPanel;
    private AdminPanel adminPanel;

    // UI Integration
    Map<String, JPanel> panelMap = new HashMap<>();

    // Navigation buttons
    private JButton[] navButtons;
    private String[] navLabels;
    private int activeNavIndex = 0;

    public MainApplicationFrame(User user, Transport transport, PeerClient client) {
        this.currentUser = user;
        this.serverTransport = transport;
        this.peerClient = client;

        initializeFrame();
        setupNavigationLabels();
        createComponents();
        setupLayout();
        setupEventHandlers();

        // Show dashboard by default
        showPanel("Dashboard");
        updateUserProfile();

        setVisible(true);
    }

    private void initializeFrame() {
        setTitle("P2P File Sharing - " + currentUser.getUsername());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setMinimumSize(new Dimension(1000, 700));
        setLocationRelativeTo(null);

        // Modern Look and Feel
        try {
            //UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel());
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Custom colors
        getContentPane().setBackground(new Color(240, 242, 245));
    }

    private void setupNavigationLabels() {
        if (currentUser.isAdmin()) {
            navLabels = new String[]{"Dashboard", "Search Files", "Browse Peers",
                    "My Files", "Statistics", "Admin Panel", "Settings"};
        } else {
            navLabels = new String[]{"Dashboard", "Search Files", "Browse Peers",
                    "My Files", "Statistics", "Settings"};
        }
        navButtons = new JButton[navLabels.length];
    }

    private void createComponents() {
        // Create main layout
        cardLayout = new CardLayout();
        mainContentPanel = new JPanel(cardLayout);
        mainContentPanel.setBackground(Color.WHITE);

        // Create navigation panel
        createNavigationPanel();

        // Create content panels
        createContentPanels();

        // Create status bar
        createStatusBar();
    }

    private void createNavigationPanel() {
        sideNavPanel = new JPanel();
        sideNavPanel.setLayout(new BoxLayout(sideNavPanel, BoxLayout.Y_AXIS));
        sideNavPanel.setBackground(new Color(45, 55, 72));
        sideNavPanel.setBorder(new EmptyBorder(20, 15, 20, 15));
        sideNavPanel.setPreferredSize(new Dimension(250, 800));

        // User profile section
        createUserProfileSection();

        // Navigation buttons
        createNavigationButtons();

        // Logout button at bottom
        createLogoutButton();
    }

    private void createUserProfileSection() {
        JPanel profilePanel = new JPanel(new BorderLayout());
        profilePanel.setBackground(new Color(45, 55, 72));
        profilePanel.setBorder(new EmptyBorder(0, 0, 30, 0));

        // Profile icon
        JLabel avatarLabel = new JLabel(createAvatarIcon());
        avatarLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // User info
        JPanel userInfoPanel = new JPanel();
        userInfoPanel.setLayout(new BoxLayout(userInfoPanel, BoxLayout.Y_AXIS));
        userInfoPanel.setBackground(new Color(45, 55, 72));

        userProfileLabel = new JLabel(currentUser.getUsername());
        userProfileLabel.setForeground(Color.WHITE);
        userProfileLabel.setFont(new Font("Arial", Font.BOLD, 16));
        userProfileLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel roleLabel = new JLabel(currentUser.isAdmin() ? "Administrator" : "User");
        roleLabel.setForeground(new Color(160, 174, 192));
        roleLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        roleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        userInfoPanel.add(userProfileLabel);
        userInfoPanel.add(Box.createVerticalStrut(5));
        userInfoPanel.add(roleLabel);

        profilePanel.add(avatarLabel, BorderLayout.NORTH);
        profilePanel.add(userInfoPanel, BorderLayout.CENTER);

        sideNavPanel.add(profilePanel);
    }

    private void createNavigationButtons() {
        for (int i = 0; i < navLabels.length; i++) {
            final int index = i;
            final String label = navLabels[i];

            navButtons[i] = createStyledNavButton(label, getNavIcon(label));
            navButtons[i].addActionListener(e -> {
                setActiveNavButton(index);
                showPanel(label);
            });

            sideNavPanel.add(navButtons[i]);
            sideNavPanel.add(Box.createVerticalStrut(5));
        }

        // Set first button as active
        setActiveNavButton(0);
    }

    private JButton createStyledNavButton(String text, ImageIcon icon) {
        JButton button = new JButton(text, icon);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBackground(new Color(45, 55, 72));
        button.setForeground(new Color(160, 174, 192));
        button.setBorder(new EmptyBorder(12, 16, 12, 16));
        button.setFont(new Font("Arial", Font.PLAIN, 14));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setMaximumSize(new Dimension(220, 45));

        // Hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                if (button.getBackground().equals(new Color(45, 55, 72))) {
                    button.setBackground(new Color(55, 65, 81));
                }
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                if (button.getBackground().equals(new Color(55, 65, 81))) {
                    button.setBackground(new Color(45, 55, 72));
                }
            }
        });

        return button;
    }

    private void createLogoutButton() {
        sideNavPanel.add(Box.createVerticalGlue());

        JButton logoutButton = createStyledNavButton("Logout", null);
        logoutButton.setBackground(new Color(220, 38, 38));
        logoutButton.setForeground(Color.WHITE);
        logoutButton.addActionListener(this::handleLogout);

        sideNavPanel.add(logoutButton);
    }

    private void createContentPanels() {
        // Initialize panels with proper constructors
        dashboardPanel = new DashboardPanel(currentUser, peerClient);
        //searchPanel = new SearchPanel(currentUser, serverTransport, peerClient);
        searchPanel = new SearchPanel(currentUser, serverTransport, peerClient);
        peerBrowserPanel = new PeerBrowserPanel(currentUser, serverTransport, peerClient);
        fileManagerPanel = new FileManagerPanel(getSharedDirectoryFromUser());
        statisticsPanel = new StatisticsPanel(currentUser);
        settingsPanel = new SettingsPanel(currentUser);

        // panels to card layout and map
        mainContentPanel.add(dashboardPanel, "Dashboard");
        panelMap.put("Dashboard", dashboardPanel);

        mainContentPanel.add(searchPanel, "Search Files");
        panelMap.put("Search Files", searchPanel);

        mainContentPanel.add(peerBrowserPanel, "Browse Peers");
        panelMap.put("Browse Peers", peerBrowserPanel);

        mainContentPanel.add(fileManagerPanel, "My Files");
        panelMap.put("My Files", fileManagerPanel);

        mainContentPanel.add(statisticsPanel, "Statistics");
        panelMap.put("Statistics", statisticsPanel);

        mainContentPanel.add(settingsPanel, "Settings");
        panelMap.put("Settings", settingsPanel);

        if (currentUser.isAdmin()) {
            adminPanel = new AdminPanel((AdminUser) currentUser, serverTransport);
            mainContentPanel.add(adminPanel, "Admin Panel");
            panelMap.put("Admin Panel", adminPanel);
        }
    }

    private String getSharedDirectoryFromUser() {
        try {
            ClientConfigurationService configService = new ClientConfigurationService("client_config.csv");
            String sharedDir = configService.getSharedDirectory(currentUser.getUsername());
            return sharedDir != null ? sharedDir : System.getProperty("user.home") + "/SharedFiles";
        } catch (Exception e) {
            return System.getProperty("user.home") + "/SharedFiles";
        }
    }

    // Public methods for UI integration
    public void setUser(User user) {
        this.currentUser = user;
        updateUserProfile();
    }

    public void addPanel(String name, JPanel panel) {
        panelMap.put(name, panel);
        mainContentPanel.add(panel, name);
    }
    private void refreshSearchPanel() {
        // This would trigger a peer list refresh in the search panel
        SwingUtilities.invokeLater(() -> {
            try {
                serverTransport.sendLine("LIST_PEERS");
                String response = serverTransport.readLine();
            } catch (Exception e) {
                System.err.println("Could not refresh peer list: " + e.getMessage());
            }
        });
    }
    public void showPanel(String panelName) {
        cardLayout.show(mainContentPanel, panelName);

        // Update panel data if needed
        switch (panelName) {
            case "Dashboard":
                if (dashboardPanel != null) dashboardPanel.refreshData();
                break;
            case "Search Files":
                if (searchPanel != null) {
                    refreshSearchPanel();
                }
                break;
            case "Browse Peers":
                if (peerBrowserPanel != null) peerBrowserPanel.refreshPeerList();
                break;
            case "Statistics":
                if (statisticsPanel != null) statisticsPanel.updateStatistics();
                break;
            case "Admin Panel":
                if (adminPanel != null) adminPanel.refreshData();
                break;
            case "My Files":
                if (fileManagerPanel != null) fileManagerPanel.updateFileList();
                break;
        }
    }


    private void createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(new Color(229, 231, 235));
        statusBar.setBorder(new EmptyBorder(8, 15, 8, 15));
        statusBar.setPreferredSize(new Dimension(0, 35));

        connectionStatusLabel = new JLabel("Connected to server");
        connectionStatusLabel.setForeground(new Color(34, 197, 94));
        connectionStatusLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        JLabel versionLabel = new JLabel("P2P File Sharing v1.0");
        versionLabel.setForeground(new Color(107, 114, 128));
        versionLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        statusBar.add(connectionStatusLabel, BorderLayout.WEST);
        statusBar.add(versionLabel, BorderLayout.EAST);

        add(statusBar, BorderLayout.SOUTH);
    }

    private void setupLayout() {
        setLayout(new BorderLayout());
        add(sideNavPanel, BorderLayout.WEST);
        add(mainContentPanel, BorderLayout.CENTER);
    }

    private void setupEventHandlers() {
        // Window closing handler
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                handleLogout(null);
            }
        });
    }

    private void setActiveNavButton(int index) {
        // Reset all buttons
        for (JButton button : navButtons) {
            button.setBackground(new Color(45, 55, 72));
            button.setForeground(new Color(160, 174, 192));
        }

        // Set active button
        navButtons[index].setBackground(new Color(59, 130, 246));
        navButtons[index].setForeground(Color.WHITE);
        activeNavIndex = index;
    }




    private void updateUserProfile() {
        userProfileLabel.setText(currentUser.getUsername());
        repaint();
    }

    private ImageIcon createAvatarIcon() {
        // Create a simple circular avatar
        int size = 60;
        BufferedImage avatar = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = avatar.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(99, 102, 241));
        g2d.fillOval(0, 0, size, size);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics fm = g2d.getFontMetrics();
        String initial = currentUser.getUsername().substring(0, 1).toUpperCase();
        int x = (size - fm.stringWidth(initial)) / 2;
        int y = ((size - fm.getHeight()) / 2) + fm.getAscent();
        g2d.drawString(initial, x, y);

        g2d.dispose();
        return new ImageIcon(avatar);
    }

    private ImageIcon getNavIcon(String label) {
        return createSimpleIcon(16, 16, new Color(160, 174, 192));
    }

    private ImageIcon createSimpleIcon(int width, int height, Color color) {
        BufferedImage icon = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = icon.createGraphics();
        g2d.setColor(color);
        g2d.fillRect(2, 2, width-4, height-4);
        g2d.dispose();
        return new ImageIcon(icon);
    }

    private void handleLogout(ActionEvent e) {
        int option = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to logout?",
                "Confirm Logout",
                JOptionPane.YES_NO_OPTION
        );

        if (option == JOptionPane.YES_OPTION) {
            try {
                if (serverTransport != null) {
                    serverTransport.sendLine("UNREGISTER");
                    serverTransport.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            dispose();
            new WelcomeFrame().setVisible(true);
        }
    }

    public void updateConnectionStatus(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            if (connected) {
                connectionStatusLabel.setText("Connected to server");
                connectionStatusLabel.setForeground(new Color(34, 197, 94));
            } else {
                connectionStatusLabel.setText("Disconnected from server");
                connectionStatusLabel.setForeground(new Color(239, 68, 68));
            }
        });
    }
}