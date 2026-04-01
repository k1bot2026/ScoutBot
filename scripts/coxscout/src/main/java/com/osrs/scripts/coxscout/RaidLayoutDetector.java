package com.osrs.scripts.coxscout;

import org.dreambot.api.wrappers.map.Region;

import java.util.*;

/**
 * Detects COX raid room layout by reading instance template chunks.
 * Ported from RuneLite's RaidsPlugin approach with proper deduplication.
 *
 * Each COX room occupies a 96x32 tile region (up to 12x4 chunks), so a single
 * room appears in many chunks. We deduplicate by keying on "templatePlane:baseY"
 * and track scene position to preserve the raid's top-to-bottom room order.
 */
public class RaidLayoutDetector {

    private static final char COMBAT = 'C';
    private static final char PUZZLE = 'P';
    private static final char SCAVENGERS = 'S';
    private static final char FARMING = 'F';

    /** All COX room templates share this base X coordinate. */
    private static final int COX_BASE_X = 3264;
    /** Room width in tiles. */
    private static final int ROOM_WIDTH = 96;
    /** Room height in tiles. */
    private static final int ROOM_HEIGHT = 32;

    /**
     * Holds a matched room with enough info for ordering and display.
     */
    private static class RoomInfo {
        final char type;
        final String name;
        final int sceneX;
        final int sceneY;
        final int scenePlane;
        final int templatePlane;
        final int baseY;

        RoomInfo(char type, String name, int sceneX, int sceneY, int scenePlane,
                 int templatePlane, int baseY) {
            this.type = type;
            this.name = name;
            this.sceneX = sceneX;
            this.sceneY = sceneY;
            this.scenePlane = scenePlane;
            this.templatePlane = templatePlane;
            this.baseY = baseY;
        }
    }

    /**
     * Template definition: baseY, plane, type char, display name.
     */
    private static class RoomTemplate {
        final int baseY;
        final int plane;
        final char type;
        final String name;

        RoomTemplate(int baseY, int plane, char type, String name) {
            this.baseY = baseY;
            this.plane = plane;
            this.type = type;
            this.name = name;
        }
    }

    /** Known COX room templates from RuneLite's InstanceTemplates. */
    private static final RoomTemplate[] TEMPLATES = {
        // Plane 0
        new RoomTemplate(5216, 0, SCAVENGERS, "Scavengers"),
        new RoomTemplate(5248, 0, COMBAT,     "Shamans"),
        new RoomTemplate(5280, 0, COMBAT,     "Vasa"),
        new RoomTemplate(5312, 0, COMBAT,     "Vanguards"),
        new RoomTemplate(5344, 0, PUZZLE,     "Ice Demon"),
        new RoomTemplate(5376, 0, PUZZLE,     "Thieving"),
        new RoomTemplate(5440, 0, FARMING,    "Farming"),

        // Plane 1
        new RoomTemplate(5216, 1, SCAVENGERS, "Scavengers"),
        new RoomTemplate(5248, 1, COMBAT,     "Mystics"),
        new RoomTemplate(5280, 1, COMBAT,     "Tekton"),
        new RoomTemplate(5312, 1, COMBAT,     "Muttadiles"),
        new RoomTemplate(5344, 1, PUZZLE,     "Tightrope"),
        new RoomTemplate(5440, 1, FARMING,    "Farming"),

        // Plane 2
        new RoomTemplate(5248, 2, COMBAT,     "Guardians"),
        new RoomTemplate(5280, 2, COMBAT,     "Vespula"),
        new RoomTemplate(5344, 2, PUZZLE,     "Crabs"),
    };

    /**
     * BaseY values for non-room areas (lobby, start, end) that should be skipped.
     * Lobby=5184 plane0, Start=5696 plane0, End=5152 plane0.
     */
    private static final int[] SKIP_BASE_Y = {5152, 5184, 5696};

    /**
     * Detect the current COX layout by reading instance template chunks.
     *
     * @return layout string like "SCCS PCFS" (space-separated floors), or empty if not in raid
     */
    public static String detectLayout() {
        List<RoomInfo> rooms = scanRooms();
        if (rooms.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (RoomInfo room : rooms) {
            sb.append(room.type);
        }
        return sb.toString();
    }

    /**
     * Detect layout and return a formatted string with room names.
     * Example: "[SCPFCCSPSF] Vasa > Tightrope > Tekton > Vespula > Crabs"
     * Only shows combat and puzzle rooms (skips Scavengers/Farming).
     */
    public static String detectLayoutWithNames() {
        List<RoomInfo> rooms = scanRooms();
        if (rooms.isEmpty()) {
            return "";
        }

        StringBuilder code = new StringBuilder();
        StringBuilder names = new StringBuilder();

        for (RoomInfo room : rooms) {
            code.append(room.type);
        }

        boolean first = true;
        for (RoomInfo room : rooms) {
            if (room.type == COMBAT || room.type == PUZZLE) {
                if (!first) {
                    names.append(" > ");
                }
                names.append(room.name);
                first = false;
            }
        }

        return "[" + code + "] " + names;
    }

    /**
     * Get a detailed description of all detected rooms for logging/debugging.
     * Includes chunk positions, template coordinates, and room names.
     */
    public static String detectDetailedLayout() {
        try {
            int[][][] chunks = Region.getInstanceTemplateChunks();
            if (chunks == null) {
                return "No chunk data available";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== COX Room Detection (with deduplication) ===\n\n");

            // First: raw chunk dump for debugging
            sb.append("--- Raw non-zero chunks ---\n");
            int nonZeroCount = 0;
            for (int plane = 0; plane < chunks.length; plane++) {
                for (int x = 0; x < chunks[plane].length; x++) {
                    for (int y = 0; y < chunks[plane][x].length; y++) {
                        int chunkData = chunks[plane][x][y];
                        if (chunkData == 0) continue;
                        nonZeroCount++;

                        int templatePlane = (chunkData >> 24) & 0x3;
                        int templateX = ((chunkData >> 14) & 0x3FF) * 8;
                        int templateY = ((chunkData >> 3) & 0x7FF) * 8;

                        sb.append(String.format(
                            "  scene[%d,%d,%d] -> template(%d, %d) plane=%d",
                            plane, x, y, templateX, templateY, templatePlane));

                        if (templateX >= COX_BASE_X && templateX < COX_BASE_X + ROOM_WIDTH) {
                            sb.append(" [COX X range]");
                        }
                        sb.append('\n');
                    }
                }
            }
            sb.append(String.format("Total non-zero chunks: %d\n\n", nonZeroCount));

            // Second: deduplicated room detection
            List<RoomInfo> rooms = scanRooms();
            sb.append("--- Detected rooms (deduplicated) ---\n");
            if (rooms.isEmpty()) {
                sb.append("  (none)\n");
            } else {
                for (int i = 0; i < rooms.size(); i++) {
                    RoomInfo r = rooms.get(i);
                    sb.append(String.format(
                        "  %d. [%c] %-12s (scene=%d,%d,%d  template plane=%d baseY=%d)\n",
                        i + 1, r.type, r.name, r.scenePlane, r.sceneX, r.sceneY,
                        r.templatePlane, r.baseY));
                }
            }

            sb.append("\nLayout: ").append(detectLayout()).append('\n');

            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Core scanning logic. Reads instance template chunks, matches against known
     * COX rooms, deduplicates (each room spans many chunks), and returns rooms
     * ordered by their scene position (preserving the raid's room order).
     */
    private static List<RoomInfo> scanRooms() {
        try {
            int[][][] chunks = Region.getInstanceTemplateChunks();
            if (chunks == null) {
                return Collections.emptyList();
            }

            // Deduplicate: key = "templatePlane:baseY", value = first RoomInfo seen
            LinkedHashMap<String, RoomInfo> seen = new LinkedHashMap<>();

            for (int plane = 0; plane < chunks.length; plane++) {
                for (int x = 0; x < chunks[plane].length; x++) {
                    for (int y = 0; y < chunks[plane][x].length; y++) {
                        int chunkData = chunks[plane][x][y];
                        if (chunkData == 0) continue;

                        int templatePlane = (chunkData >> 24) & 0x3;
                        int templateX = ((chunkData >> 14) & 0x3FF) * 8;
                        int templateY = ((chunkData >> 3) & 0x7FF) * 8;

                        // Check if this chunk falls within the COX base X range
                        if (templateX < COX_BASE_X || templateX >= COX_BASE_X + ROOM_WIDTH) {
                            continue;
                        }

                        // Try to match against known room templates
                        RoomTemplate matched = matchTemplate(templateY, templatePlane);
                        if (matched == null) {
                            continue;
                        }

                        // Deduplicate: same template plane + baseY = same room
                        String key = matched.plane + ":" + matched.baseY;
                        if (!seen.containsKey(key)) {
                            seen.put(key, new RoomInfo(
                                matched.type, matched.name,
                                x, y, plane,
                                matched.plane, matched.baseY));
                        }
                    }
                }
            }

            // Sort by scene position to preserve raid room order:
            // Primary: scene plane ascending, Secondary: scene X ascending,
            // Tertiary: scene Y descending (rooms go top-to-bottom in the scene)
            List<RoomInfo> rooms = new ArrayList<>(seen.values());
            rooms.sort((a, b) -> {
                if (a.scenePlane != b.scenePlane) return Integer.compare(a.scenePlane, b.scenePlane);
                if (a.sceneX != b.sceneX) return Integer.compare(a.sceneX, b.sceneX);
                return Integer.compare(b.sceneY, a.sceneY);
            });

            return rooms;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Match a template Y coordinate and plane against known COX room templates.
     * The templateY from a chunk can fall anywhere within a room's 32-tile height,
     * so we check the range [baseY, baseY + ROOM_HEIGHT).
     *
     * @param templateY world Y coordinate from the chunk
     * @param templatePlane plane from the chunk
     * @return the matched RoomTemplate, or null if no match / skip room
     */
    private static RoomTemplate matchTemplate(int templateY, int templatePlane) {
        // Check skip rooms first (lobby, start, end)
        for (int skipY : SKIP_BASE_Y) {
            if (templateY >= skipY && templateY < skipY + ROOM_HEIGHT) {
                // Could be on any plane for skip check; lobby/start/end are plane 0
                // but we skip based on Y range regardless
                return null;
            }
        }

        for (RoomTemplate t : TEMPLATES) {
            if (templatePlane == t.plane
                    && templateY >= t.baseY
                    && templateY < t.baseY + ROOM_HEIGHT) {
                return t;
            }
        }

        return null;
    }
}
