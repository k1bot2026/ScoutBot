package com.osrs.scripts.coxscout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class CoxScoutGUI extends JFrame {

    private final LayoutManager layoutManager;
    private final Map<String, JCheckBox> checkboxes = new LinkedHashMap<>();
    private final JTextField customInput;
    private final JLabel statusLabel;
    private final JLabel stateLabel;
    private final JLabel attemptsLabel;
    private final JLabel lastLayoutLabel;
    private final JLabel currentLayoutLabel;
    private final JLabel matchResultLabel;
    private final JTextArea logArea;
    private final JButton startButton;
    private boolean started = false;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public CoxScoutGUI(LayoutManager layoutManager) {
        this.layoutManager = layoutManager;

        setTitle("COX Scout v2.0");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(true);
        setLayout(new BorderLayout(10, 10));
        setPreferredSize(new Dimension(450, 600));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Preset layouts panel
        JPanel presetsPanel = new JPanel();
        presetsPanel.setLayout(new BoxLayout(presetsPanel, BoxLayout.Y_AXIS));
        presetsPanel.setBorder(new TitledBorder("Desired Layouts"));

        Map<String, String> presets = layoutManager.getPresets();
        for (Map.Entry<String, String> entry : presets.entrySet()) {
            String label = entry.getValue() + "  —  " + entry.getKey();
            JCheckBox cb = new JCheckBox(label, true);
            String sequence = entry.getValue();
            cb.addActionListener(e -> layoutManager.setEnabled(sequence, cb.isSelected()));
            checkboxes.put(sequence, cb);
            presetsPanel.add(cb);
        }

        mainPanel.add(presetsPanel);
        mainPanel.add(Box.createVerticalStrut(5));

        // Custom layout input
        JPanel customPanel = new JPanel(new BorderLayout(5, 0));
        customPanel.setBorder(new TitledBorder("Add Custom Layout"));
        customInput = new JTextField();
        JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> addCustomLayout());
        customPanel.add(customInput, BorderLayout.CENTER);
        customPanel.add(addButton, BorderLayout.EAST);

        mainPanel.add(customPanel);
        mainPanel.add(Box.createVerticalStrut(5));

        // Live status panel
        JPanel statusPanel = new JPanel(new GridLayout(0, 1, 2, 2));
        statusPanel.setBorder(new TitledBorder("Live Status"));

        stateLabel = new JLabel("State: IDLE");
        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.BOLD));
        statusLabel = new JLabel("Status: Waiting to start");
        attemptsLabel = new JLabel("Attempts: 0");
        currentLayoutLabel = new JLabel("Current layout: -");
        currentLayoutLabel.setFont(currentLayoutLabel.getFont().deriveFont(Font.BOLD, 13f));
        lastLayoutLabel = new JLabel("Last layout: -");
        matchResultLabel = new JLabel("Match: -");
        matchResultLabel.setFont(matchResultLabel.getFont().deriveFont(Font.BOLD, 14f));

        statusPanel.add(stateLabel);
        statusPanel.add(statusLabel);
        statusPanel.add(attemptsLabel);
        statusPanel.add(currentLayoutLabel);
        statusPanel.add(lastLayoutLabel);
        statusPanel.add(matchResultLabel);

        mainPanel.add(statusPanel);
        mainPanel.add(Box.createVerticalStrut(5));

        // Log area
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(new TitledBorder("Scout Log"));
        logArea = new JTextArea(8, 35);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        logPanel.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(logPanel);
        mainPanel.add(Box.createVerticalStrut(5));

        // Start button
        startButton = new JButton("Start Scouting");
        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD, 14f));
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        startButton.addActionListener(e -> {
            if (layoutManager.getEnabledSequences().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Select at least one layout to scout for.", "No Layouts", JOptionPane.WARNING_MESSAGE);
                return;
            }
            started = true;
            startButton.setEnabled(false);
            startButton.setText("Scouting...");
            statusLabel.setText("Status: Scouting...");
            appendLog("Scouting started for: " + layoutManager.getEnabledSequences());
        });

        mainPanel.add(startButton);

        add(mainPanel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
    }

    private void addCustomLayout() {
        String text = customInput.getText().toUpperCase().trim();
        if (text.isEmpty()) return;

        if (!text.matches("[SCPFMVT]{4,16}")) {
            JOptionPane.showMessageDialog(this,
                "Layout must be 4-16 characters using letters: S, C, P, F, M, V, T",
                "Invalid Layout", JOptionPane.WARNING_MESSAGE);
            return;
        }

        layoutManager.addCustomSequence(text);

        if (!checkboxes.containsKey(text)) {
            // Add checkbox dynamically
            JCheckBox cb = new JCheckBox(text + "  —  Custom", true);
            cb.addActionListener(e -> layoutManager.setEnabled(text, cb.isSelected()));
            checkboxes.put(text, cb);
            // Can't easily add to presets panel after construction, show message
            appendLog("Added custom layout: " + text);
        }

        customInput.setText("");
    }

    /**
     * Update state display from the script loop.
     */
    public void updateState(String stateName) {
        SwingUtilities.invokeLater(() -> {
            stateLabel.setText("State: " + stateName);
        });
    }

    /**
     * Called when a layout is detected (before matching).
     */
    public void onLayoutDetected(String layout, int attempt) {
        SwingUtilities.invokeLater(() -> {
            currentLayoutLabel.setText("Current layout: " + layout);
            attemptsLabel.setText("Attempts: " + attempt);
            appendLog("#" + attempt + "  Layout: " + layout);
        });
    }

    /**
     * Called when layout does not match.
     */
    public void onLayoutNoMatch(String layout, int attempts) {
        SwingUtilities.invokeLater(() -> {
            lastLayoutLabel.setText("Last layout: " + layout);
            matchResultLabel.setText("Match: NO MATCH");
            matchResultLabel.setForeground(Color.RED);
            attemptsLabel.setText("Attempts: " + attempts);
            statusLabel.setText("Status: Reloading...");
            appendLog("#" + attempts + "  " + layout + " — NO MATCH");
        });
    }

    /**
     * Called when a matching layout is found.
     */
    public void onLayoutFound(String layout, int attempts) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Status: MATCH FOUND!");
            statusLabel.setForeground(new Color(0, 150, 0));
            stateLabel.setText("State: FOUND");
            currentLayoutLabel.setText("Current layout: " + layout);
            lastLayoutLabel.setText("Last layout: " + layout);
            matchResultLabel.setText("MATCH: " + layout);
            matchResultLabel.setForeground(new Color(0, 150, 0));
            attemptsLabel.setText("Attempts: " + attempts);
            startButton.setText("FOUND: " + layout);
            appendLog("========================================");
            appendLog("MATCH FOUND: " + layout + " (after " + attempts + " attempts)");
            appendLog("========================================");
            Toolkit.getDefaultToolkit().beep();
        });
    }

    /**
     * Called when no layout could be detected (chunks empty).
     */
    public void onDetectionFailed(int attempt) {
        SwingUtilities.invokeLater(() -> {
            currentLayoutLabel.setText("Current layout: (detection failed)");
            statusLabel.setText("Status: Detection failed, retrying...");
            appendLog("#" + attempt + "  Detection failed — chunks empty, retrying...");
        });
    }

    /**
     * General status update.
     */
    public void updateStatus(int attempts, String lastLayout) {
        SwingUtilities.invokeLater(() -> {
            attemptsLabel.setText("Attempts: " + attempts);
            if (lastLayout != null && !lastLayout.isEmpty()) {
                lastLayoutLabel.setText("Last layout: " + lastLayout);
            }
        });
    }

    /**
     * Append a timestamped line to the log area.
     */
    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = timeFormat.format(new Date());
            logArea.append("[" + timestamp + "] " + message + "\n");
            // Auto-scroll to bottom
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public boolean isStarted() {
        return started;
    }
}
