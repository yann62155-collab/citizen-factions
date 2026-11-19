package fr.citizen.factions.client;

import fr.citizen.factions.FactionNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;

public final class FactionScreen extends Screen {
    private final FactionNetwork.FactionMenuData data;
    private final List<String> members;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public FactionScreen(FactionNetwork.FactionMenuData data) {
        super(Component.literal("CITIZEN • Factions"));
        this.data = data;
        this.members = data.roster().isBlank() ? List.of() : Arrays.asList(data.roster().split("\\n"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(500, width - 24);
        panelHeight = Math.min(330, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int buttonY = top + panelHeight - 30;

        addRenderableWidget(Button.builder(Component.literal("◀"), button -> requestPage(data.page() - 1))
            .bounds(left + 138, buttonY, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), button -> requestPage(data.page() + 1))
            .bounds(left + 170, buttonY, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Actualiser"), button -> requestPage(data.page()))
            .bounds(left + 16, buttonY, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fermer"), button -> onClose())
            .bounds(left + panelWidth - 96, buttonY, 80, 20).build());
        children().stream().filter(widget -> widget instanceof Button).map(widget -> (Button) widget).forEach(button -> {
            if (button.getMessage().getString().equals("◀")) button.active = data.page() > 0;
            if (button.getMessage().getString().equals("▶")) button.active = data.page() + 1 < data.totalPages();
        });
    }

    private void requestPage(int page) {
        if (minecraft != null && minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new FactionNetwork.OpenMenuRequest(page));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0181B25);
        graphics.fill(left, top, left + panelWidth, top + 42, 0xFF29243D);
        graphics.fill(left + 12, top + 54, left + panelWidth - 12, top + 118, 0xFF222735);
        graphics.fill(left + 12, top + 128, left + panelWidth - 12, top + panelHeight - 42, 0xFF222735);

        graphics.drawCenteredString(font, Component.literal("CITIZEN  /  FACTIONS"), width / 2, top + 14, 0xFFFFFFFF);
        graphics.drawString(font, Component.literal("Faction"), left + 24, top + 64, 0xFFAAA3C8);
        graphics.drawString(font, Component.literal(data.faction()), left + 24, top + 82, 0xFFFFFFFF);
        graphics.drawString(font, Component.literal("Grade : " + data.grade()), left + panelWidth - 170, top + 82, 0xFFE4D7A8);
        graphics.drawString(font, Component.literal(data.status()), left + 24, top + 108, 0xFFBFC6D8);

        graphics.drawString(font, Component.literal("MEMBRES"), left + 24, top + 140, 0xFFE4D7A8);
        int y = top + 158;
        if (members.isEmpty()) {
            graphics.drawString(font, Component.literal("Aucun membre à afficher."), left + 24, y, 0xFFBFC6D8);
        } else {
            for (int i = 0; i < members.size(); i++) {
                graphics.drawString(font, Component.literal(members.get(i)), left + 24, y + i * 14, 0xFFE6E8EF);
            }
        }
        graphics.drawCenteredString(font, Component.literal("Page " + (data.page() + 1) + " / " + data.totalPages()),
            left + panelWidth / 2, top + panelHeight - 48, 0xFFAAA3C8);
        graphics.drawString(font, Component.literal(data.bond()), left + 24, top + panelHeight - 62, 0xFFB9D8D0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
