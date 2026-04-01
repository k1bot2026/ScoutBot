package com.osrs.scripts.coxscout;

import org.dreambot.api.wrappers.map.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects COX raid room layout by reading instance template chunks.
 * Uses the same approach as RuneLite's RaidsPlugin.
 */
public class RaidLayoutDetector {

    /**
     * Room type symbols for layout string.
     */
    private static final char COMBAT = 'C';
    private static final char PUZZLE = 'P';
    private static final char SCAVENGERS = 'S';
    private static final char FARMING = 'F';
    private static final char UNKNOWN = '?';

    /**
     * Known room templates: {baseY, plane, type}
     * All rooms share baseX = 3264 (408 in chunk coords).
     * baseY values are in world coordinates.
     */
    private static final int[][] ROOM_TEMPLATES = {
        // Plane 0
        {5216, 0, SCAVENGERS},  // Scavengers
        {5248, 0, COMBAT},      // Shamans
        {5280, 0, COMBAT},      // Vasa
        {5312, 0, COMBAT},      // Vanguards
        {5344, 0, PUZZLE},      // Ice Demon
        {5376, 0, PUZZLE},      // Thieving
        {5440, 0, FARMING},     // Farming/Prep

        // Plane 1
        {5216, 1, SCAVENGERS},  // Scavengers 2
        {5248, 1, COMBAT},      // Mystics
        {5280, 1, COMBAT},      // Tekton
        {5312, 1, COMBAT},      // Muttadiles
        {5344, 1, PUZZLE},      // Tightrope
        {5440, 1, FARMING},     // Farming/Prep 2

        // Plane 2
        {5248, 2, COMBAT},      // Guardians
        {5280, 2, COMBAT},      // Vespula
        {5344, 2, PUZZLE},      // Crabs
    };

    // Lobby/start/end templates to skip
    private static final int[] SKIP_BASE_Y = {5184, 5696, 5152};

    /**
     * Detect the current COX layout by reading instance template chunks.
     *
     * @return layout string like "SCPFCCSPSF", or empty string if not in a raid
     */
    public static String detectLayout() {
        try {
            int[][][] chunks = Region.getInstanceTemplateChunks();
            if (chunks == null) {
                return "";
            }

            List<Character> rooms = new ArrayList<>();

            // Scan the instance chunks for raid rooms
            // COX uses plane 0, scan across the scene
            for (int plane = 0; plane < chunks.length; plane++) {
                for (int x = 0; x < chunks[plane].length; x++) {
                    for (int y = 0; y < chunks[plane][x].length; y++) {
                        int chunkData = chunks[plane][x][y];
                        if (chunkData == 0) continue;

                        char roomType = identifyRoom(chunkData);
                        if (roomType != UNKNOWN && roomType != 0) {
                            rooms.add(roomType);
                        }
                    }
                }
            }

            // Build layout string
            StringBuilder sb = new StringBuilder();
            for (char c : rooms) {
                sb.append(c);
            }

            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Identify a room type from its packed chunk data.
     *
     * @param chunkData packed instance template chunk
     * @return room type character (C, P, S, F) or UNKNOWN
     */
    private static char identifyRoom(int chunkData) {
        // Extract template coordinates from packed chunk data
        // Format: plane(2 bits) | ... | x(10 bits at bit 14) | y(11 bits at bit 3)
        int templateX = (chunkData >> 14) & 0x3FF;  // chunk X coordinate
        int templateY = (chunkData >> 3) & 0x7FF;    // chunk Y coordinate
        int templatePlane = (chunkData >> 24) & 0x3;

        // Convert to world coordinates
        int worldX = templateX * 8;
        int worldY = templateY * 8;

        // All COX rooms have baseX = 3264
        if (worldX != 3264) {
            return UNKNOWN;
        }

        // Skip lobby, start, end rooms
        for (int skipY : SKIP_BASE_Y) {
            if (worldY == skipY) {
                return UNKNOWN;
            }
        }

        // Match against known room templates
        for (int[] template : ROOM_TEMPLATES) {
            if (worldY == template[0] && templatePlane == template[1]) {
                return (char) template[2];
            }
        }

        return UNKNOWN;
    }

    /**
     * Get a detailed description of detected rooms (for logging).
     */
    public static String detectDetailedLayout() {
        try {
            int[][][] chunks = Region.getInstanceTemplateChunks();
            if (chunks == null) {
                return "No chunk data available";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Instance chunks scan:\n");

            for (int plane = 0; plane < chunks.length; plane++) {
                for (int x = 0; x < chunks[plane].length; x++) {
                    for (int y = 0; y < chunks[plane][x].length; y++) {
                        int chunkData = chunks[plane][x][y];
                        if (chunkData == 0) continue;

                        int templateX = (chunkData >> 14) & 0x3FF;
                        int templateY = (chunkData >> 3) & 0x7FF;
                        int templatePlane = (chunkData >> 24) & 0x3;
                        int worldX = templateX * 8;
                        int worldY = templateY * 8;

                        char roomType = identifyRoom(chunkData);
                        sb.append(String.format("  [%d,%d,%d] -> world(%d,%d) plane=%d type=%c\n",
                            plane, x, y, worldX, worldY, templatePlane, roomType));
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
