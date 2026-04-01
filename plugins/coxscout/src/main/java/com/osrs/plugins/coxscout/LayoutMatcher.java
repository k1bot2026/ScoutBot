package com.osrs.plugins.coxscout;

import java.util.ArrayList;
import java.util.List;

public class LayoutMatcher {

    private static final String[][] PRESETS = {
        {"noVespVasa", "SCPFCCSPSF"},
        {"tektonSkip", "SCFPCCSPSF"},
        {"crabsRope", "CSPFCCSPSF"},
        {"fastScout", "SCPFCCSPF"},
    };

    public static boolean matches(CoxScoutConfig config, String layout) {
        if (layout == null || layout.isEmpty()) {
            return false;
        }
        String upper = layout.toUpperCase().trim();

        for (String enabled : getEnabledLayouts(config)) {
            if (enabled.equals(upper)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> getEnabledLayouts(CoxScoutConfig config) {
        List<String> layouts = new ArrayList<>();

        if (config.noVespVasa()) layouts.add("SCPFCCSPSF");
        if (config.tektonSkip()) layouts.add("SCFPCCSPSF");
        if (config.crabsRope()) layouts.add("CSPFCCSPSF");
        if (config.fastScout()) layouts.add("SCPFCCSPF");

        String custom = config.customLayouts();
        if (custom != null && !custom.isEmpty()) {
            for (String s : custom.split(",")) {
                String trimmed = s.toUpperCase().trim();
                if (!trimmed.isEmpty()) {
                    layouts.add(trimmed);
                }
            }
        }

        return layouts;
    }
}
