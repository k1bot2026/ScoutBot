package com.osrs.plugins.coxscout;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
    name = "COX Scout",
    description = "Automatically scouts Chambers of Xeric layouts by reloading the raid entrance.",
    tags = {"cox", "chambers", "xeric", "scout", "raids"}
)
public class CoxScoutPlugin extends Plugin {

    // Pattern to match RuneLite's COX plugin layout output: "Layout: [SCPFCCSPSF]"
    private static final Pattern LAYOUT_PATTERN = Pattern.compile("Layout: \\[([A-Z]+)\\]");

    @Inject
    private Client client;

    @Inject
    private CoxScoutConfig config;

    @Inject
    private Notifier notifier;

    @Inject
    private KeyManager keyManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private CoxScoutOverlay overlay;

    private Robot robot;

    @Getter
    private ScoutState state = ScoutState.IDLE;

    @Getter
    private String lastDetectedLayout = "";

    @Getter
    private String matchedLayout = "";

    @Getter
    private int attempts = 0;

    private int stateStartTick = 0;
    private int currentTick = 0;
    private boolean layoutDetectedThisCycle = false;

    private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.toggleHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleScouting();
        }
    };

    @Override
    protected void startUp() {
        keyManager.registerKeyListener(hotkeyListener);
        overlayManager.add(overlay);

        try {
            robot = new Robot();
        } catch (AWTException e) {
            log.error("Failed to create Robot — scouting will not work. On macOS, grant accessibility permissions to RuneLite.", e);
        }

        log.info("COX Scout plugin started.");
    }

    @Override
    protected void shutDown() {
        keyManager.unregisterKeyListener(hotkeyListener);
        overlayManager.remove(overlay);
        state = ScoutState.IDLE;
        robot = null;
        log.info("COX Scout plugin stopped.");
    }

    @Provides
    CoxScoutConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CoxScoutConfig.class);
    }

    private void toggleScouting() {
        if (state == ScoutState.IDLE || state == ScoutState.FOUND) {
            // Start scouting
            attempts = 0;
            lastDetectedLayout = "";
            matchedLayout = "";
            setState(ScoutState.CLICK_STEPS);
            log.info("COX Scout: Scouting started. Looking for: {}", LayoutMatcher.getEnabledLayouts(config));
        } else {
            // Stop scouting
            setState(ScoutState.IDLE);
            log.info("COX Scout: Scouting stopped.");
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        currentTick++;

        switch (state) {
            case CLICK_STEPS:
                handleClickSteps();
                break;
            case SKIP_DIALOG:
                handleSkipDialog();
                break;
            case READ_CHAT:
                handleReadChat();
                break;
            case MATCH_LAYOUT:
                handleMatchLayout();
                break;
            default:
                // IDLE or FOUND — do nothing
                break;
        }
    }

    /**
     * Left-click at current mouse position (no movement).
     * Player must have their mouse hovering over the Steps before starting.
     */
    private void handleClickSteps() {
        layoutDetectedThisCycle = false;
        lastDetectedLayout = "";

        if (robot != null) {
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            log.info("[CLICK_STEPS] Clicked in place — attempt #{}", attempts + 1);
        } else {
            log.warn("[CLICK_STEPS] Robot not available, cannot click.");
        }

        setState(ScoutState.SKIP_DIALOG);
    }

    /**
     * Press "1" every tick to skip dialog.
     * Times out after configured dialog timeout.
     */
    private void handleSkipDialog() {
        if (robot != null) {
            robot.keyPress(KeyEvent.VK_1);
            robot.keyRelease(KeyEvent.VK_1);
        }

        int timeoutTicks = config.dialogTimeout() / 600;
        if (currentTick - stateStartTick >= timeoutTicks) {
            log.info("[SKIP_DIALOG] Timeout after {} ticks, moving to READ_CHAT", currentTick - stateStartTick);
            setState(ScoutState.READ_CHAT);
        }
    }

    /**
     * Wait for onChatMessage to detect a layout string.
     * Times out after configured chat read timeout.
     */
    private void handleReadChat() {
        if (layoutDetectedThisCycle) {
            log.info("[READ_CHAT] Layout detected: {}", lastDetectedLayout);
            setState(ScoutState.MATCH_LAYOUT);
            return;
        }

        int timeoutTicks = config.chatReadTimeout() / 600;
        if (currentTick - stateStartTick >= timeoutTicks) {
            log.info("[READ_CHAT] No layout detected after {} ticks, retrying...", currentTick - stateStartTick);
            setState(ScoutState.CLICK_STEPS);
        }
    }

    /**
     * Check if detected layout matches any enabled sequence.
     */
    private void handleMatchLayout() {
        attempts++;

        if (LayoutMatcher.matches(config, lastDetectedLayout)) {
            matchedLayout = lastDetectedLayout;
            log.info("*** MATCH FOUND: {} after {} attempts! ***", matchedLayout, attempts);
            Toolkit.getDefaultToolkit().beep();
            notifier.notify("COX Scout: Match found! " + matchedLayout + " (" + attempts + " attempts)");
            setState(ScoutState.FOUND);
        } else {
            log.info("[MATCH] {} — no match. ({} attempts)", lastDetectedLayout, attempts);
            setState(ScoutState.CLICK_STEPS);
        }
    }

    /**
     * ChatMessage listener — captures layout strings from the built-in COX plugin.
     * The COX plugin sends layout as FRIENDSCHATNOTIFICATION: "Layout: [SCPFCCSPSF]: Room1, Room2, ..."
     */
    @Subscribe
    public void onChatMessage(ChatMessage event) {
        // Log all game/friendschat messages when scouting for debug
        if (state != ScoutState.IDLE && state != ScoutState.FOUND) {
            log.info("[CHAT:{}] {}", event.getType(), Text.removeTags(event.getMessage()));
        }

        if (event.getType() != ChatMessageType.FRIENDSCHATNOTIFICATION
            && event.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        String msg = Text.removeTags(event.getMessage());
        Matcher matcher = LAYOUT_PATTERN.matcher(msg);
        if (matcher.find()) {
            lastDetectedLayout = matcher.group(1);
            layoutDetectedThisCycle = true;
            log.info("[CHAT] >>> Layout detected: {}", lastDetectedLayout);
        }
    }

    private void setState(ScoutState newState) {
        log.info("[STATE] {} -> {}", state, newState);
        this.state = newState;
        this.stateStartTick = currentTick;
    }
}
