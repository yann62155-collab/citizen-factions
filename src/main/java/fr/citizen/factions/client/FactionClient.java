package fr.citizen.factions.client;

import fr.citizen.factions.FactionNetwork;
import net.minecraft.client.Minecraft;

public final class FactionClient {
    private FactionClient() {}

    public static void open(FactionNetwork.FactionMenuData data) {
        Minecraft.getInstance().setScreen(new FactionScreen(data));
    }
}
