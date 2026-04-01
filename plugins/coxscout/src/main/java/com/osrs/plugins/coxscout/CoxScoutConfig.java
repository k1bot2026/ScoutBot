package com.osrs.plugins.coxscout;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("coxscout")
public interface CoxScoutConfig extends Config {

    @ConfigItem(keyName = "toggleHotkey", name = "Toggle Scouting", description = "Hotkey to start/stop scouting", position = 0)
    default Keybind toggleHotkey() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(keyName = "noVespVasa", name = "No Vespula/Vasa (SCPFCCSPSF)", description = "Scout for layout SCPFCCSPSF", position = 1)
    default boolean noVespVasa() {
        return true;
    }

    @ConfigItem(keyName = "tektonSkip", name = "Tekton Skip (SCFPCCSPSF)", description = "Scout for layout SCFPCCSPSF", position = 2)
    default boolean tektonSkip() {
        return true;
    }

    @ConfigItem(keyName = "crabsRope", name = "Crabs + Rope (CSPFCCSPSF)", description = "Scout for layout CSPFCCSPSF", position = 3)
    default boolean crabsRope() {
        return true;
    }

    @ConfigItem(keyName = "fastScout", name = "Fast Scout (SCPFCCSPF)", description = "Scout for layout SCPFCCSPF", position = 4)
    default boolean fastScout() {
        return true;
    }

    @ConfigItem(keyName = "customLayouts", name = "Custom Layouts", description = "Comma-separated custom layout strings (e.g. SCPFCCSPSF,SCFPCCSPSF)", position = 5)
    default String customLayouts() {
        return "";
    }

    @ConfigItem(keyName = "dialogTimeout", name = "Dialog Timeout (ms)", description = "How long to press 1 before moving on", position = 6)
    default int dialogTimeout() {
        return 5000;
    }

    @ConfigItem(keyName = "chatReadTimeout", name = "Chat Read Timeout (ms)", description = "How long to wait for layout in chat", position = 7)
    default int chatReadTimeout() {
        return 3000;
    }
}
