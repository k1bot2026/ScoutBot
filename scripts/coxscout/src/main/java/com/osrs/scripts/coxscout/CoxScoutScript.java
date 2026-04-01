package com.osrs.scripts.coxscout;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.input.Mouse;
import org.dreambot.api.input.Keyboard;
import org.dreambot.api.input.event.impl.keyboard.awt.Key;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.listener.MenuRowListener;
import org.dreambot.api.wrappers.widgets.MenuRow;

import java.awt.*;

@ScriptManifest(
    name = "COX Scout",
    description = "Scouts Chambers of Xeric layouts by reloading the raid entrance.",
    author = "OSRS Bot",
    version = 2.2,
    category = Category.MINIGAME
)
public class CoxScoutScript extends AbstractScript implements MenuRowListener {

    private static final long DIALOG_TIMEOUT = 5000;
    private static final long LAYOUT_DETECT_TIMEOUT = 5000;

    private ScoutState state = ScoutState.CLICK_STEPS;
    private LayoutManager layoutManager;
    private CoxScoutGUI gui;

    private String lastDetectedLayout = "";
    private String matchedLayout = "";
    private int attempts = 0;
    private long stateStartTime = 0;

    // Flag to track if we swapped the menu entry this cycle
    private boolean menuSwapped = false;

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

        log("COX Scout v2.2 started. Menu entry swap active — left-click = Reload.");
        log("Position your mouse on the Steps. Mouse will NOT move.");
        log("Scouting for " + layoutManager.getEnabledSequences().size() + " layouts: " + layoutManager.getEnabledSequences());
        stateStartTime = System.currentTimeMillis();
    }

    /**
     * MenuRowListener callback — fires when a menu entry is being added.
     * If we see "Reload" on "Steps", swap it to be the top/left-click action
     * by changing its opCode to match the default action slot.
     */
    @Override
    public void onRowAdded(MenuRow row) {
        if (row == null) return;

        // Only swap when we're in CLICK_STEPS state (actively scouting)
        if (state != ScoutState.CLICK_STEPS) return;

        String action = row.getAction();
        String object = row.getObject();

        // Swap "Reload" on "Steps" to be the default left-click action
        if (action != null && object != null
                && action.equals("Reload") && object.contains("Steps")) {
            // Set opCode to 1 which is the "first option" / left-click action
            row.setOpCode(1);
            menuSwapped = true;
        }
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
     * Left-click at current mouse position.
     * MenuRowListener swaps "Reload" to be the default action,
     * so a simple left-click triggers Reload on the Steps.
     * Mouse does NOT move.
     */
    private int handleClickSteps() {
        lastDetectedLayout = "";
        guiState("RELOADING");
        menuSwapped = false;

        // Just left-click in place — the MenuRowListener makes "Reload" the default action
        Mouse.click();
        attempts++;
        log("[RELOAD] Left-click in place — attempt #" + attempts);
        guiLog("Attempt #" + attempts + " — Reloading raid...");
        setState(ScoutState.SKIP_DIALOG);
        return Calculations.random(600, 1000);
    }

    /**
     * Press "1" to skip dialog. Timeout moves to layout detection.
     */
    private int handleSkipDialog() {
        if (Dialogues.inDialogue()) {
            Keyboard.typeKey(Key.ONE);
            return Calculations.random(300, 600);
        }

        long elapsed = System.currentTimeMillis() - stateStartTime;
        if (elapsed > DIALOG_TIMEOUT) {
            guiState("DETECTING");
            guiLog("Dialog done. Scanning layout...");
            setState(ScoutState.READ_CHAT);
            return Calculations.random(200, 400);
        }

        return Calculations.random(200, 400);
    }

    /**
     * Detect layout from instance template chunks.
     */
    private int handleDetectLayout() {
        String layout = RaidLayoutDetector.detectLayout();
        String layoutWithNames = RaidLayoutDetector.detectLayoutWithNames();

        if (!layout.isEmpty() && layout.length() >= 4) {
            lastDetectedLayout = layout;
            log("[DETECT] " + layoutWithNames);

            if (gui != null) {
                gui.onLayoutDetected(layoutWithNames, attempts);
            }

            setState(ScoutState.MATCH_LAYOUT);
            return 100;
        }

        long elapsed = System.currentTimeMillis() - stateStartTime;
        if (elapsed > LAYOUT_DETECT_TIMEOUT) {
            log("[DETECT] No layout detected after " + elapsed + "ms");
            log(RaidLayoutDetector.detectDetailedLayout());

            if (gui != null) {
                gui.onDetectionFailed(attempts);
            }

            setState(ScoutState.CLICK_STEPS);
            return Calculations.random(500, 1000);
        }

        return Calculations.random(200, 400);
    }

    /**
     * Check if detected layout matches any desired sequence.
     */
    private int handleMatchLayout() {
        String layoutWithNames = RaidLayoutDetector.detectLayoutWithNames();

        if (layoutManager.matches(lastDetectedLayout)) {
            matchedLayout = lastDetectedLayout;
            log("*** MATCH FOUND: " + layoutWithNames + " after " + attempts + " attempts! ***");
            Toolkit.getDefaultToolkit().beep();

            if (gui != null) {
                gui.onLayoutFound(layoutWithNames, attempts);
            }

            state = ScoutState.FOUND;
            guiState("FOUND");
            return -1;
        }

        log("[NO MATCH] " + layoutWithNames + " (" + attempts + " attempts)");

        if (gui != null) {
            gui.onLayoutNoMatch(layoutWithNames, attempts);
        }

        setState(ScoutState.CLICK_STEPS);
        return Calculations.random(300, 700);
    }

    private void setState(ScoutState newState) {
        this.state = newState;
        this.stateStartTime = System.currentTimeMillis();
    }

    private void guiState(String stateName) {
        if (gui != null) {
            gui.updateState(stateName);
        }
    }

    private void guiLog(String message) {
        if (gui != null) {
            gui.appendLog(message);
        }
    }

    @Override
    public void onExit() {
        if (gui != null) {
            gui.dispose();
        }
    }
}
