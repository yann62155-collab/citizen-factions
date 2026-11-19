package fr.citizen.factions.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.citizen.factions.CitizenFactions;
import fr.citizen.factions.FactionNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CitizenFactions.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FactionClientKeys {
    private static final KeyMapping OPEN_MENU = new KeyMapping(
        "key.citizenfactions.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.misc"
    );

    private FactionClientKeys() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
        NeoForge.EVENT_BUS.addListener(FactionClientKeys::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_MENU.consumeClick()) {
            if (Minecraft.getInstance().getConnection() != null) {
                PacketDistributor.sendToServer(new FactionNetwork.OpenMenuRequest());
            }
        }
    }
}
