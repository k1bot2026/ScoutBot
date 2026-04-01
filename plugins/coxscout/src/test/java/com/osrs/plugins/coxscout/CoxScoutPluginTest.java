package com.osrs.plugins.coxscout;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CoxScoutPluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(CoxScoutPlugin.class);
        RuneLite.main(args);
    }
}
