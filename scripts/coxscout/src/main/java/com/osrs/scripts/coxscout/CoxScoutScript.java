package com.osrs.scripts.coxscout;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.input.Keyboard;
import org.dreambot.api.input.event.impl.keyboard.awt.Key;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.wrappers.interactive.GameObject;

import java.awt.*;

@ScriptManifest(
    name = "COX Scout",
    description = "Scouts Chambers of Xeric layouts by reloading the raid entrance.",
    author = "OSRS Bot",
    version = 2.0,
    category = Category.MINIGAME
)
public class CoxScoutScript extends AbstractScript {

    private static final long DIALOG_TIMEOUT = 5000;
    private static final long LAYOUT_DETECT_TIMEOUT = 3000;

    private ScoutState state = ScoutState.CLICK_STEPS;
    private LayoutManager layoutManager;
    private CoxScoutGUI gui;

    private String lastDetectedLayout = "";
    private String matchedLayout = "";
    private int attempts = 0;
    private long stateStartTime = 0;

    @Override
    public void onStart() {
        layoutManager = new LayoutManager();
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                gui = new CoxScoutGUI(layoutManager);
                gui.setVisible(true);
            });
        } catch (Exception e) {
            log("Failed to create GUI: " + e.getMessage());
            stop();
            return;
        }

        while (gui.isVisible() && !gui.isStarted()) {
            sleep(100);
        }

        if (!gui.isStarted()) {
            stop();
            return;
        }

        log("COX Scout v2.0 started — detecting layouts from game state (no RuneLite needed).");
        log("Using right-click 'Reload' on Steps — no manual mouse positioning needed.");
        log("Scouting for " + layoutManager.getEnabledSequences().size() + " layouts: " + layoutManager.getEnabledSequences());
        stateStartTime = System.currentTimeMillis();
    }

    @Override
    public int onLoop() {
        if (state == ScoutState.FOUND) {
            return -1;
        }

        switch (state) {
            case CLICK_STEPS:
                return handleClickSteps();
            case SKIP_DIALOG:
                return handleSkipDialog();
            case READ_CHAT:
                return handleDetectLayout();
            case MATCH_LAYOUT:
                return handleMatchLayout();
            default:
                return 600;
        }
    }

    /**
     * Left-click at current mouse position (no movement).
     */
    private int handleClickSteps() {
        lastDetectedLayout = "";

        // Right-click Steps and select "Reload" to get a new raid layout
        GameObject steps = GameObjects.closest("Steps");
        if (steps != null && steps.interact("Reload")) {
            log("[CLICK_STEPS] Reload on Steps — attempt #" + (attempts + 1));
            setState(ScoutState.SKIP_DIALOG);
            return Calculations.random(600, 1000);
        }

        log("[CLICK_STEPS] Steps not found or Reload failed, retrying...");
        return Calculations.random(1000, 2000);
    }

    /**
     * Press "1" to skip dialog. Timeout moves to layout detection.
     */
    private int handleSkipDialog() {
        if (Dialogues.inDialogue()) {
            log("[SKIP_DIALOG] In dialogue, pressing 1...");
            Keyboard.typeKey(Key.ONE);
            return Calculations.random(300, 600);
        }

        long elapsed = System.currentTimeMillis() - stateStartTime;
        if (elapsed > DIALOG_TIMEOUT) {
            log("[SKIP_DIALOG] Timeout after " + elapsed + "ms, detecting layout...");
            setState(ScoutState.READ_CHAT); // Reusing READ_CHAT state for layout detection
            return Calculations.random(200, 400);
        }

        return Calculations.random(200, 400);
    }

    /**
     * Detect layout from instance template chunks (replaces chat reading).
     */
    private int handleDetectLayout() {
        String layout = RaidLayoutDetector.detectLayout();

        if (!layout.isEmpty() && layout.length() >= 4) {
            lastDetectedLayout = layout;
            log("[DETECT] Layout detected from game state: " + layout);

            // Log detailed info on first attempt for debugging
            if (attempts == 0) {
                log(RaidLayoutDetector.detectDetailedLayout());
            }

            setState(ScoutState.MATCH_LAYOUT);
            return 100;
        }

        long elapsed = System.currentTimeMillis() - stateStartTime;
        if (elapsed > LAYOUT_DETECT_TIMEOUT) {
            log("[DETECT] No layout detected after " + elapsed + "ms, retrying...");

            // Log detailed chunk data for debugging
            log(RaidLayoutDetector.detectDetailedLayout());

            setState(ScoutState.CLICK_STEPS);
            return Calculations.random(500, 1000);
        }

        return Calculations.random(200, 400);
    }

    /**
     * Check if detected layout matches any desired sequence.
     */
    private int handleMatchLayout() {
        attempts++;

        if (layoutManager.matches(lastDetectedLayout)) {
            matchedLayout = lastDetectedLayout;
            log("*** MATCH FOUND: " + matchedLayout + " after " + attempts + " attempts! ***");
            Toolkit.getDefaultToolkit().beep();
            if (gui != null) {
                gui.onLayoutFound(matchedLayout, attempts);
            }
            state = ScoutState.FOUND;
            return -1;
        }

        log("[MATCH] " + lastDetectedLayout + " — no match. (" + attempts + " attempts)");
        if (gui != null) {
            gui.updateStatus(attempts, lastDetectedLayout);
        }
        setState(ScoutState.CLICK_STEPS);
        return Calculations.random(300, 700);
    }

    private void setState(ScoutState newState) {
        log("[STATE] " + state + " -> " + newState);
        this.state = newState;
        this.stateStartTime = System.currentTimeMillis();
    }

    @Override
    public void onExit() {
        if (gui != null) {
            gui.dispose();
        }
    }

    public ScoutState getState() { return state; }
    public int getAttempts() { return attempts; }
    public String getLastDetectedLayout() { return lastDetectedLayout; }
    public String getMatchedLayout() { return matchedLayout; }
}
