# COX Scouting Bot — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a standalone DreamBot script that automatically scouts COX raid layouts by reloading the entrance, skipping dialog, reading the room sequence from chat, and alerting when a desired layout is found.

**Architecture:** Standalone DreamBot script with a simple state machine (CLICK_STEPS → SKIP_DIALOG → READ_CHAT → MATCH_LAYOUT → loop or FOUND). Uses `ChatListener` to capture layout strings, `Keyboard` for dialog skipping, `GameObjects` for stairs interaction. Swing GUI for layout selection with presets.

**Tech Stack:** Java 11 (Temurin), Gradle, DreamBot API (client.jar), Java Swing (GUI)

---

## Phase 1: Project Setup

### Task 1.1: Add coxscout module to Gradle build

**Files:**
- Modify: `settings.gradle` (add coxscout include)
- Create: `scripts/coxscout/build.gradle`

**Step 1: Update settings.gradle**

Add the coxscout module. The file should become:

```groovy
rootProject.name = 'osrs-bots'

include 'core'
include 'scripts:moneymaker'
include 'scripts:coxscout'
```

**Step 2: Create scripts/coxscout/build.gradle**

```groovy
jar {
    destinationDirectory = file("${System.getProperty('user.home')}/DreamBot/Scripts")
    archiveBaseName = 'CoxScout'
}
```

No core dependency — this script is fully standalone.

**Step 3: Create directory structure**

```bash
mkdir -p scripts/coxscout/src/main/java/com/osrs/scripts/coxscout
```

**Step 4: Verify build compiles**

```bash
./gradlew :scripts:coxscout:build
```

**Step 5: Commit**

```bash
git add settings.gradle scripts/coxscout/
git commit -m "chore: add coxscout module to gradle build"
```

---

## Phase 2: State Machine & Core Logic

### Task 2.1: Create ScoutState enum

**Files:**
- Create: `scripts/coxscout/src/main/java/com/osrs/scripts/coxscout/ScoutState.java`

**Step 1: Write ScoutState.java**

```java
package com.osrs.scripts.coxscout;

public enum ScoutState {
    CLICK_STEPS,
    SKIP_DIALOG,
    READ_CHAT,
    MATCH_LAYOUT,
    FOUND
}
```

**Step 2: Commit**

```bash
git add scripts/coxscout/
git commit -m "feat(coxscout): add ScoutState enum"
```

---

### Task 2.2: Create LayoutManager

**Files:**
- Create: `scripts/coxscout/src/main/java/com/osrs/scripts/coxscout/LayoutManager.java`

**Step 1: Write LayoutManager.java**

This class manages the list of desired layouts (presets + custom). It stores which sequences are enabled and provides a `matches()` method.

```java
package com.osrs.scripts.coxscout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LayoutManager {

    // Preset layouts: name → sequence
    private static final Map<String, String> PRESETS = new LinkedHashMap<>();
    static {
        PRESETS.put("No Vespula/Vasa (3c2p)", "SCPFCCSPSF");
        PRESETS.put("Tekton Skip (3c2p)", "SCFPCCSPSF");
        PRESETS.put("Crabs + Rope (3c2p)", "CSPFCCSPSF");
        PRESETS.put("Fast Scout (2c2p)", "SCPFCCSPF");
    }

    // All sequences (preset + custom) with enabled flag
    private final Map<String, Boolean> sequences = new LinkedHashMap<>();

    public LayoutManager() {
        // Add all presets as enabled by default
        for (Map.Entry<String, String> entry : PRESETS.entrySet()) {
            sequences.put(entry.getValue(), true);
        }
    }

    public Map<String, String> getPresets() {
        return new LinkedHashMap<>(PRESETS);
    }

    public Map<String, Boolean> getSequences() {
        return new LinkedHashMap<>(sequences);
    }

    public void setEnabled(String sequence, boolean enabled) {
        sequences.put(sequence, enabled);
    }

    public void addCustomSequence(String sequence) {
        String upper = sequence.toUpperCase().trim();
        if (!upper.isEmpty() && !sequences.containsKey(upper)) {
            sequences.put(upper, true);
        }
    }

    public void removeSequence(String sequence) {
        // Only remove non-preset sequences
        if (!PRESETS.containsValue(sequence)) {
            sequences.remove(sequence);
        }
    }

    public boolean matches(String chatLayout) {
        if (chatLayout == null || chatLayout.isEmpty()) {
            return false;
        }
        String upper = chatLayout.toUpperCase().trim();
        for (Map.Entry<String, Boolean> entry : sequences.entrySet()) {
            if (entry.getValue() && entry.getKey().equals(upper)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getEnabledSequences() {
        List<String> enabled = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : sequences.entrySet()) {
            if (entry.getValue()) {
                enabled.add(entry.getKey());
            }
        }
        return enabled;
    }
}
```

**Step 2: Commit**

```bash
git add scripts/coxscout/
git commit -m "feat(coxscout): add LayoutManager with preset sequences"
```

---

### Task 2.3: Create CoxScoutScript (main script)

**Files:**
- Create: `scripts/coxscout/src/main/java/com/osrs/scripts/coxscout/CoxScoutScript.java`

**Step 1: Write CoxScoutScript.java**

This is the main DreamBot script. It extends `AbstractScript`, implements `ChatListener` to capture layout messages, and runs a state machine in `onLoop()`.

```java
package com.osrs.scripts.coxscout;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.input.Keyboard;
import org.dreambot.api.input.event.impl.keyboard.awt.Key;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.listener.ChatListener;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.widgets.message.Message;
import org.dreambot.api.wrappers.widgets.message.MessageType;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ScriptManifest(
    name = "COX Scout",
    description = "Scouts Chambers of Xeric layouts by reloading the raid entrance.",
    author = "OSRS Bot",
    version = 1.0,
    category = Category.MINIGAME
)
public class CoxScoutScript extends AbstractScript implements ChatListener {

    // Regex to match COX layout strings in chat (8-12 uppercase letters from the set SCPFMVT)
    private static final Pattern LAYOUT_PATTERN = Pattern.compile("([SCPFMVT]{8,12})");

    // The name of the Steps game object at COX entrance
    private static final String STEPS_NAME = "Steps";

    private ScoutState state = ScoutState.CLICK_STEPS;
    private LayoutManager layoutManager;
    private CoxScoutGUI gui;

    private String lastDetectedLayout = "";
    private String matchedLayout = "";
    private int attempts = 0;
    private long stateStartTime = 0;
    private boolean layoutDetectedThisCycle = false;

    // Timeout for waiting in a state before retrying (ms)
    private static final long DIALOG_TIMEOUT = 5000;
    private static final long CHAT_READ_TIMEOUT = 3000;

    @Override
    public void onStart() {
        layoutManager = new LayoutManager();

        // Show GUI and wait for user to press start
        gui = new CoxScoutGUI(layoutManager);
        gui.setVisible(true);

        // Block until GUI is started or closed
        while (gui.isVisible() && !gui.isStarted()) {
            sleep(100);
        }

        if (!gui.isStarted()) {
            stop();
            return;
        }

        log("COX Scout started. Scouting for " + layoutManager.getEnabledSequences().size() + " layouts.");
        log("Desired layouts: " + layoutManager.getEnabledSequences());
        stateStartTime = System.currentTimeMillis();
    }

    @Override
    public int onLoop() {
        if (state == ScoutState.FOUND) {
            return -1; // Stop the script
        }

        switch (state) {
            case CLICK_STEPS:
                return handleClickSteps();
            case SKIP_DIALOG:
                return handleSkipDialog();
            case READ_CHAT:
                return handleReadChat();
            case MATCH_LAYOUT:
                return handleMatchLayout();
            default:
                return 600;
        }
    }

    /**
     * State: CLICK_STEPS
     * Left-click the Steps object to start/reload the raid.
     */
    private int handleClickSteps() {
        layoutDetectedThisCycle = false;
        lastDetectedLayout = "";

        GameObject steps = GameObjects.closest(STEPS_NAME);
        if (steps != null && steps.interact()) {
            log("Clicked Steps (attempt #" + (attempts + 1) + ")");
            setState(ScoutState.SKIP_DIALOG);
            return Calculations.random(600, 1000);
        }

        log("Steps not found, retrying...");
        return Calculations.random(1000, 2000);
    }

    /**
     * State: SKIP_DIALOG
     * Hold "1" key to skip through the dialog.
     */
    private int handleSkipDialog() {
        if (Dialogues.inDialogue()) {
            // Press "1" to pick the first option / skip dialog
            Keyboard.typeKey(Key.ONE);
            return Calculations.random(300, 600);
        }

        // If we're no longer in dialog, check if we timed out or dialog was skipped
        if (System.currentTimeMillis() - stateStartTime > DIALOG_TIMEOUT) {
            // Dialog is done or timed out, move to reading chat
            setState(ScoutState.READ_CHAT);
            return Calculations.random(200, 400);
        }

        // Wait for dialog to appear
        return Calculations.random(200, 400);
    }

    /**
     * State: READ_CHAT
     * Wait for the layout string to appear in chat (captured by ChatListener).
     */
    private int handleReadChat() {
        if (layoutDetectedThisCycle) {
            setState(ScoutState.MATCH_LAYOUT);
            return 100;
        }

        // Timeout: if no layout detected, try again
        if (System.currentTimeMillis() - stateStartTime > CHAT_READ_TIMEOUT) {
            log("No layout detected in chat, retrying...");
            setState(ScoutState.CLICK_STEPS);
            return Calculations.random(500, 1000);
        }

        return Calculations.random(200, 400);
    }

    /**
     * State: MATCH_LAYOUT
     * Compare detected layout against desired sequences.
     */
    private int handleMatchLayout() {
        attempts++;

        if (layoutManager.matches(lastDetectedLayout)) {
            matchedLayout = lastDetectedLayout;
            log("*** MATCH FOUND: " + matchedLayout + " after " + attempts + " attempts! ***");
            onLayoutFound();
            state = ScoutState.FOUND;
            return -1;
        }

        log("Layout " + lastDetectedLayout + " does not match. Reloading... (" + attempts + " attempts)");
        setState(ScoutState.CLICK_STEPS);
        return Calculations.random(300, 700);
    }

    /**
     * Called when a matching layout is found.
     */
    private void onLayoutFound() {
        // Play system beep
        Toolkit.getDefaultToolkit().beep();

        // Update GUI
        if (gui != null) {
            gui.onLayoutFound(matchedLayout, attempts);
        }
    }

    /**
     * ChatListener callback — captures game messages and checks for layout strings.
     */
    @Override
    public void onGameMessage(Message message) {
        if (message == null || message.getMessage() == null) {
            return;
        }

        String text = message.getMessage();
        Matcher matcher = LAYOUT_PATTERN.matcher(text);
        if (matcher.find()) {
            lastDetectedLayout = matcher.group(1);
            layoutDetectedThisCycle = true;
            log("Detected layout in chat: " + lastDetectedLayout);
        }
    }

    @Override
    public void onPlayerMessage(Message message) {
        // Not used
    }

    @Override
    public void onTradeMessage(Message message) {
        // Not used
    }

    private void setState(ScoutState newState) {
        this.state = newState;
        this.stateStartTime = System.currentTimeMillis();
    }

    @Override
    public void onExit() {
        if (gui != null) {
            gui.dispose();
        }
    }

    // Accessors for GUI
    public ScoutState getState() { return state; }
    public int getAttempts() { return attempts; }
    public String getLastDetectedLayout() { return lastDetectedLayout; }
    public String getMatchedLayout() { return matchedLayout; }
}
```

**Step 2: Commit**

```bash
git add scripts/coxscout/
git commit -m "feat(coxscout): add main CoxScoutScript with state machine and chat listener"
```

---

## Phase 3: GUI

### Task 3.1: Create CoxScoutGUI

**Files:**
- Create: `scripts/coxscout/src/main/java/com/osrs/scripts/coxscout/CoxScoutGUI.java`

**Step 1: Write CoxScoutGUI.java**

Simple Swing GUI with checkboxes for preset layouts, custom input field, start button, and status area.

```java
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

        // Add checkbox if not already present
        if (!checkboxes.containsKey(text)) {
            // We can't easily add to the presets panel after construction,
            // so just log it and clear the field
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
```

**Step 2: Commit**

```bash
git add scripts/coxscout/
git commit -m "feat(coxscout): add Swing GUI with preset layouts and custom input"
```

---

## Phase 4: Integration & Polish

### Task 4.1: Wire GUI status updates into the script loop

**Files:**
- Modify: `scripts/coxscout/src/main/java/com/osrs/scripts/coxscout/CoxScoutScript.java`

**Step 1: Add GUI update call in handleMatchLayout()**

In `handleMatchLayout()`, after logging the non-match, add a GUI status update:

```java
// Add this line after the log("Layout ... does not match") line in handleMatchLayout():
if (gui != null) {
    gui.updateStatus(attempts, lastDetectedLayout);
}
```

**Step 2: Commit**

```bash
git add scripts/coxscout/
git commit -m "feat(coxscout): wire GUI status updates during scouting loop"
```

---

### Task 4.2: Build and verify JAR output

**Step 1: Run the full build**

```bash
./gradlew :scripts:coxscout:build
```

Expected: BUILD SUCCESSFUL, JAR at `~/DreamBot/Scripts/CoxScout.jar`

**Step 2: Verify JAR contents**

```bash
jar tf ~/DreamBot/Scripts/CoxScout.jar | head -20
```

Expected: Should list `com/osrs/scripts/coxscout/CoxScoutScript.class` and all other classes.

**Step 3: Commit if any fixes were needed**

---

### Task 4.3: Final commit with all files

**Step 1: Verify everything is committed**

```bash
git status
git add -A
git commit -m "feat: complete COX scouting bot with layout matching and GUI"
```

---

## Summary of Files

```
scripts/coxscout/
├── build.gradle
└── src/main/java/com/osrs/scripts/coxscout/
    ├── CoxScoutScript.java    → Main script: state machine, ChatListener, loop
    ├── ScoutState.java        → Enum: CLICK_STEPS, SKIP_DIALOG, READ_CHAT, MATCH_LAYOUT, FOUND
    ├── LayoutManager.java     → Manages desired sequences with presets + custom
    └── CoxScoutGUI.java       → Swing GUI: checkboxes, custom input, status display
```

## How It Works

1. Script starts → GUI shows with preset layouts (checkboxes) and custom input
2. User selects desired layouts and clicks "Start Scouting"
3. Bot left-clicks the "Steps" object at COX entrance
4. Bot presses "1" key to skip dialog when dialog appears
5. `ChatListener` captures the layout string from game chat (regex: `[SCPFMVT]{8,12}`)
6. If layout matches any enabled sequence → beep alert, update GUI, stop
7. If no match → loop back to step 3

## Notes

- The `LAYOUT_PATTERN` regex (`[SCPFMVT]{8,12}`) may need adjustment depending on exact chat format. Test in-game and adjust if layout appears with prefix text or different letter set.
- The `STEPS_NAME` constant ("Steps") should be verified against the actual game object name at the COX entrance. Update if needed.
- Dialog timing may need tuning — adjust `DIALOG_TIMEOUT` and `CHAT_READ_TIMEOUT` based on in-game behavior.
