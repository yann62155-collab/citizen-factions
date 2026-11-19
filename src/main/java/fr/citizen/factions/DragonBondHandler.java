package fr.citizen.factions;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative faction mount rules and per-player/per-dragon bond.
 * Ice and Fire is detected by class name so its JAR is not bundled in this project.
 */
public final class DragonBondHandler {
    private static final String DATA_KEY = "citizenfactions_bonds";
    private static final String DRAGON_BASE_CLASS = "com.github.alexthe666.iceandfire.entity.EntityDragonBase";
    private static final Map<UUID, ServerBossEvent> BARS = new ConcurrentHashMap<>();
    private static final long GAIN_INTERVAL = 1_200L;
    private static final long DECAY_INTERVAL = 12_000L;

    private DragonBondHandler() {}

    public static void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity dragon = event.getEntityBeingMounted();
        if (!isDragon(dragon)) return;
        if (event.isMounting()) {
            FactionStore.Membership member = FactionStore.get(player);
            if (member == null || !isAllowed(member.faction(), dragon)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal(member == null
                    ? "Tu dois appartenir à une faction pour monter un dragon."
                    : "Ta faction ne peut pas monter cette variante de dragon."));
                return;
            }
            applyAbandonDecay(player, dragon);
            showBar(player, getBond(player, dragon));
        } else {
            hideBar(player);
            markAbandoned(player, dragon);
        }
    }

    public static void onPlayerTick(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide || player.tickCount % 20 != 0) return;
        Entity dragon = player.getVehicle();
        if (!isDragon(dragon)) {
            hideBar(serverPlayer);
            return;
        }

        FactionStore.Membership member = FactionStore.get(player);
        if (member == null || !isAllowed(member.faction(), dragon)) {
            serverPlayer.stopRiding();
            hideBar(serverPlayer);
            serverPlayer.sendSystemMessage(Component.literal("Tu ne peux plus monter ce dragon avec ta faction actuelle."));
            return;
        }

        long now = dragon.level().getGameTime();
        CompoundTag entry = getEntry(dragon, player.getUUID());
        int bond = entry.getInt("bond");
        long lastGain = entry.getLong("last_gain");
        if (lastGain <= 0L) lastGain = now;
        long elapsed = Math.max(0L, now - lastGain);
        if (elapsed >= GAIN_INTERVAL) {
            int gained = (int) (elapsed / GAIN_INTERVAL);
            bond = Math.min(100, bond + gained);
            entry.putInt("bond", bond);
            entry.putLong("last_gain", now);
            entry.putLong("last_active", now);
            putEntry(dragon, player.getUUID(), entry);
        }

        showBar(serverPlayer, bond);
        applyResistance(dragon, serverPlayer, bond, now);
    }

    private static void applyResistance(Entity dragon, ServerPlayer rider, int bond, long now) {
        if (bond < 75 && now % 40L == 0L && getControllingPassenger(dragon) == rider) {
            float strength = (75 - bond) * 0.012F;
            dragon.setYRot(dragon.getYRot() + (dragon.getRandom().nextBoolean() ? strength : -strength));
        }
    }

    private static void applyAbandonDecay(ServerPlayer player, Entity dragon) {
        CompoundTag entry = getEntry(dragon, player.getUUID());
        long lastActive = entry.getLong("last_active");
        long now = dragon.level().getGameTime();
        if (lastActive > 0L && now > lastActive) {
            long elapsed = now - lastActive;
            int loss = (int) (elapsed / DECAY_INTERVAL);
            if (loss > 0) {
                entry.putInt("bond", Math.max(0, entry.getInt("bond") - loss));
                lastActive += (long) loss * DECAY_INTERVAL;
            }
        }
        entry.putLong("last_gain", now);
        entry.putLong("last_active", lastActive > 0L ? lastActive : now);
        putEntry(dragon, player.getUUID(), entry);
    }

    private static void markAbandoned(ServerPlayer player, Entity dragon) {
        CompoundTag entry = getEntry(dragon, player.getUUID());
        long now = dragon.level().getGameTime();
        entry.putLong("last_active", now);
        entry.putLong("last_gain", now);
        putEntry(dragon, player.getUUID(), entry);
    }

    private static CompoundTag getEntry(Entity dragon, UUID playerId) {
        CompoundTag allBonds = dragon.getPersistentData().getCompound(DATA_KEY);
        return allBonds.getCompound(playerId.toString()).copy();
    }

    private static void putEntry(Entity dragon, UUID playerId, CompoundTag entry) {
        CompoundTag allBonds = dragon.getPersistentData().getCompound(DATA_KEY);
        allBonds.put(playerId.toString(), entry);
        dragon.getPersistentData().put(DATA_KEY, allBonds);
    }

    private static int getBond(Player player, Entity dragon) {
        return getEntry(dragon, player.getUUID()).getInt("bond");
    }

    private static boolean isDragon(Entity entity) {
        if (entity == null) return false;
        for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass()) {
            if (DRAGON_BASE_CLASS.equals(type.getName())) return true;
        }
        return false;
    }

    private static boolean isAllowed(String faction, Entity dragon) {
        String variant = getVariantName(dragon).toLowerCase(java.util.Locale.ROOT);
        boolean corrupted = variant.contains("black") || variant.contains("gray")
            || variant.contains("grey") || variant.contains("copper");
        if (faction.equals("chasseur_de_dragon")) return corrupted;
        if (faction.equals("dragonnier")) return !corrupted;
        return false;
    }

    private static String getVariantName(Entity dragon) {
        try {
            Method getVariant = dragon.getClass().getMethod("getVariant");
            Object variant = getVariant.invoke(dragon);
            if (!(variant instanceof Number number)) return "";
            Method getVariantName = dragon.getClass().getMethod("getVariantName", int.class);
            Object name = getVariantName.invoke(dragon, number.intValue());
            return name == null ? "" : name.toString();
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private static Entity getControllingPassenger(Entity dragon) {
        try {
            Method method = dragon.getClass().getMethod("getControllingPassenger");
            Object value = method.invoke(dragon);
            return value instanceof Entity entity ? entity : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void showBar(ServerPlayer player, int bond) {
        ServerBossEvent bar = BARS.computeIfAbsent(player.getUUID(), id -> new ServerBossEvent(
            Component.literal("Lien avec le dragon"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS));
        String level = bond < 25 ? "Faible" : bond < 60 ? "Moyen" : bond < 90 ? "Fort" : "Maîtrisé";
        bar.setName(Component.literal("Lien avec le dragon : " + bond + "% — " + level));
        bar.setProgress(Math.max(0.0F, Math.min(1.0F, bond / 100.0F)));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
    }

    public static void hideBar(ServerPlayer player) {
        ServerBossEvent bar = BARS.remove(player.getUUID());
        if (bar != null) bar.removePlayer(player);
    }
}
