package com.osrs.scripts.coxscout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LayoutManager {

    private static final Map<String, String> PRESETS = new LinkedHashMap<>();
    static {
        // VTV rotation (Vasa > Tekton > Tightrope > Vespula > Crabs)
        PRESETS.put("VTV — FSCCSPCPSF", "FSCCSPCPSF");
        PRESETS.put("VTV — SFCCSPCPSF", "SFCCSPCPSF");
        PRESETS.put("VTV — SCPFCCSPSF", "SCPFCCSPSF");
        PRESETS.put("VTV — SCSPFCCSPF", "SCSPFCCSPF");

        // VSV rotation (Vasa > Shamans > Tightrope > Vespula > Crabs)
        PRESETS.put("VSV — SCSPFCCSPF", "SCSPFCCSPF");
        PRESETS.put("VSV — SCPFCCSPSF", "SCPFCCSPSF");
        PRESETS.put("VSV — SFCCSPCPSF", "SFCCSPCPSF");
        PRESETS.put("VSV — FSCCSPCPSF", "FSCCSPCPSF");
    }

    private final Map<String, Boolean> sequences = new LinkedHashMap<>();

    public LayoutManager() {
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
