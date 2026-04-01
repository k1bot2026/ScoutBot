package com.osrs.scripts.coxscout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class CoxScoutGUI extends JFrame {

    private final LayoutManager layoutManager;
    private final Map<String, JCheckBox> checkboxes = new LinkedHashMap<>();
    private final JTextField customInput;
    private final JLabel statusLabel;
    private final JLabel attemptsLabel;
    private final JLabel lastLayoutLabel;
    private final JButton startButton;
    private boolean started = false;

    public CoxScoutGUI(LayoutManager layoutManager) {
        this.layoutManager = layoutManager;

        setTitle("COX Scout");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout(10, 10));

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
        mainPanel.add(Box.createVerticalStrut(10));

        // Custom layout input
        JPanel customPanel = new JPanel(new BorderLayout(5, 0));
        customPanel.setBorder(new TitledBorder("Add Custom Layout"));
        customInput = new JTextField();
        JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> addCustomLayout());
        customPanel.add(customInput, BorderLayout.CENTER);
        customPanel.add(addButton, BorderLayout.EAST);

        mainPanel.add(customPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Status panel
        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
        statusPanel.setBorder(new TitledBorder("Status"));

        statusLabel = new JLabel("Status: Waiting to start");
        attemptsLabel = new JLabel("Attempts: 0");
        lastLayoutLabel = new JLabel("Last layout: -");

        statusPanel.add(statusLabel);
        statusPanel.add(attemptsLabel);
        statusPanel.add(lastLayoutLabel);

        mainPanel.add(statusPanel);
        mainPanel.add(Box.createVerticalStrut(10));

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
            JOptionPane.showMessageDialog(this,
                "Added custom layout: " + text,
                "Layout Added", JOptionPane.INFORMATION_MESSAGE);
        }

        customInput.setText("");
    }

    public void onLayoutFound(String layout, int attempts) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Status: MATCH FOUND!");
            statusLabel.setForeground(Color.GREEN.darker());
            attemptsLabel.setText("Attempts: " + attempts);
            lastLayoutLabel.setText("Matched: " + layout);
            startButton.setText("Found: " + layout);
            Toolkit.getDefaultToolkit().beep();
        });
    }

    public void updateStatus(int attempts, String lastLayout) {
        SwingUtilities.invokeLater(() -> {
            attemptsLabel.setText("Attempts: " + attempts);
            if (lastLayout != null && !lastLayout.isEmpty()) {
                lastLayoutLabel.setText("Last layout: " + lastLayout);
            }
        });
    }

    public boolean isStarted() {
        return started;
    }
}
