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
import org.dreambot.api.wrappers.widgets.Menu;
import org.dreambot.api.wrappers.widgets.MenuRow;
import org.dreambot.api.wrappers.widgets.builder.MenuRowBuilder;

import java.awt.*;

@ScriptManifest(
    name = "COX Scout",
    description = "Scouts Chambers of Xeric layouts by reloading the raid entrance.",
    author = "OSRS Bot",
    version = 2.1,
    category = Category.MINIGAME
)
public class CoxScoutScript extends AbstractScript {

    private static final long DIALOG_TIMEOUT = 5000;
    private static final long LAYOUT_DETECT_TIMEOUT = 5000; // Increased to 5s for chunk loading

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

        log("COX Scout v2.1 started.");
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
     * Inject "Reload" action on Steps directly — no mouse movement, no right-click menu.
     * Works like RuneLite's Menu Entry Swapper: sends the action packet directly.
     */
    private int handleClickSteps() {
        lastDetectedLayout = "";
        guiState("RELOADING");

        GameObject steps = GameObjects.closest("Steps");
        if (steps != null) {
            // Build a MenuRow for "Reload" on the Steps object and inject it directly
            // This sends the action without moving the mouse or opening the menu
            MenuRow reloadAction = MenuRowBuilder.buildFromObject(steps, "Reload");
            if (reloadAction != null && Menu.inject(reloadAction)) {
                attempts++;
                log("[RELOAD] Injected Reload action — attempt #" + attempts);
                guiLog("Attempt #" + attempts + " — Reloading raid (menu inject)...");
                setState(ScoutState.SKIP_DIALOG);
                return Calculations.random(600, 1000);
            }

            // Fallback: regular interact if injection fails
            log("[RELOAD] Menu inject failed, falling back to regular interact...");
            if (steps.interact("Reload")) {
                attempts++;
                log("[RELOAD] Fallback interact — attempt #" + attempts);
                guiLog("Attempt #" + attempts + " — Reloading raid (fallback)...");
                setState(ScoutState.SKIP_DIALOG);
                return Calculations.random(600, 1000);
            }
        }

        log("[RELOAD] Steps not found, retrying...");
        guiLog("Steps not found, retrying...");
        return Calculations.random(1000, 2000);
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
