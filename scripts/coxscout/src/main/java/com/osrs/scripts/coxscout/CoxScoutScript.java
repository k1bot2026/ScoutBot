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

    private static final Pattern LAYOUT_PATTERN = Pattern.compile("([SCPFMVT]{8,12})");
    private static final String STEPS_NAME = "Steps";
    private static final long DIALOG_TIMEOUT = 5000;
    private static final long CHAT_READ_TIMEOUT = 3000;

    private ScoutState state = ScoutState.CLICK_STEPS;
    private LayoutManager layoutManager;
    private CoxScoutGUI gui;

    private String lastDetectedLayout = "";
    private String matchedLayout = "";
    private int attempts = 0;
    private long stateStartTime = 0;
    private boolean layoutDetectedThisCycle = false;

    @Override
    public void onStart() {
        layoutManager = new LayoutManager();
        gui = new CoxScoutGUI(layoutManager);
        gui.setVisible(true);

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
            return -1;
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

    private int handleSkipDialog() {
        if (Dialogues.inDialogue()) {
            Keyboard.typeKey(Key.ONE);
            return Calculations.random(300, 600);
        }

        if (System.currentTimeMillis() - stateStartTime > DIALOG_TIMEOUT) {
            setState(ScoutState.READ_CHAT);
            return Calculations.random(200, 400);
        }

        return Calculations.random(200, 400);
    }

    private int handleReadChat() {
        if (layoutDetectedThisCycle) {
            setState(ScoutState.MATCH_LAYOUT);
            return 100;
        }

        if (System.currentTimeMillis() - stateStartTime > CHAT_READ_TIMEOUT) {
            log("No layout detected in chat, retrying...");
            setState(ScoutState.CLICK_STEPS);
            return Calculations.random(500, 1000);
        }

        return Calculations.random(200, 400);
    }

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
        if (gui != null) {
            gui.updateStatus(attempts, lastDetectedLayout);
        }
        setState(ScoutState.CLICK_STEPS);
        return Calculations.random(300, 700);
    }

    private void onLayoutFound() {
        Toolkit.getDefaultToolkit().beep();
        if (gui != null) {
            gui.onLayoutFound(matchedLayout, attempts);
        }
    }

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
    public void onPlayerMessage(Message message) {}

    @Override
    public void onTradeMessage(Message message) {}

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

    public ScoutState getState() { return state; }
    public int getAttempts() { return attempts; }
    public String getLastDetectedLayout() { return lastDetectedLayout; }
    public String getMatchedLayout() { return matchedLayout; }
}
