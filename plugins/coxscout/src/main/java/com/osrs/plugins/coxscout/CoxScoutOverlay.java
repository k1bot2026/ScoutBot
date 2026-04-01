package com.osrs.plugins.coxscout;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class CoxScoutOverlay extends Overlay {

    private final CoxScoutPlugin plugin;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public CoxScoutOverlay(CoxScoutPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        ScoutState state = plugin.getState();

        // Only show overlay when scouting or found
        if (state == ScoutState.IDLE) {
            return null;
        }

        panelComponent.getChildren().clear();

        // Title
        Color titleColor = state == ScoutState.FOUND ? Color.GREEN : Color.YELLOW;
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("COX Scout")
            .color(titleColor)
            .build());

        // State
        panelComponent.getChildren().add(LineComponent.builder()
            .left("State:")
            .right(state.name())
            .build());

        // Attempts
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Attempts:")
            .right(String.valueOf(plugin.getAttempts()))
            .build());

        // Last layout
        String lastLayout = plugin.getLastDetectedLayout();
        if (lastLayout != null && !lastLayout.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Last layout:")
                .right(lastLayout)
                .build());
        }

        // Match found
        if (state == ScoutState.FOUND) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("MATCHED:")
                .leftColor(Color.GREEN)
                .right(plugin.getMatchedLayout())
                .rightColor(Color.GREEN)
                .build());
        }

        return panelComponent.render(graphics);
    }
}
