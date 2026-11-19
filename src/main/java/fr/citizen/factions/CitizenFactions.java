package fr.citizen.factions;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(CitizenFactions.MOD_ID)
public final class CitizenFactions {
    public static final String MOD_ID = "citizenfactions";

    public CitizenFactions() {
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onMount);
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        FactionCommands.register(event.getDispatcher());
    }

    private void onMount(EntityMountEvent event) {
        DragonBondHandler.onMount(event);
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        DragonBondHandler.onPlayerTick(event.getEntity());
    }

    private void onServerStarted(ServerStartedEvent event) {
        FactionStore.load(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        FactionStore.save(event.getServer());
    }
}
