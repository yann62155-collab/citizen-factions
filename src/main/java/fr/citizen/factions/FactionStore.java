package fr.citizen.factions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FactionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, String> FACTIONS = new HashMap<>();
    private static final Map<UUID, Membership> MEMBERS = new HashMap<>();
    private static Path file;

    private FactionStore() {}

    public static synchronized void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("citizen_factions.json");
        FACTIONS.clear();
        MEMBERS.clear();
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) return;
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null) return;
            if (root.has("factions") && root.get("factions").isJsonObject()) {
                root.getAsJsonObject("factions").entrySet().forEach(e ->
                    FACTIONS.put(e.getKey(), e.getValue().getAsString()));
            }
            if (root.has("members") && root.get("members").isJsonObject()) {
                root.getAsJsonObject("members").entrySet().forEach(e -> {
                    try {
                        JsonObject member = e.getValue().getAsJsonObject();
                        String faction = member.get("faction").getAsString();
                        String grade = member.get("grade").getAsString();
                        if (FACTIONS.containsKey(faction) && FactionCommands.isValidGrade(grade)) {
                            MEMBERS.put(UUID.fromString(e.getKey()), new Membership(faction, grade));
                        }
                    } catch (RuntimeException ignored) {
                        System.err.println("[CITIZEN Factions] Entrée membre ignorée : " + e.getKey());
                    }
                });
            }
        } catch (Exception ex) {
            System.err.println("[CITIZEN Factions] Impossible de lire les données : " + ex.getMessage());
        }
    }

    public static synchronized void save(MinecraftServer server) {
        if (file == null) file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("citizen_factions.json");
        JsonObject root = new JsonObject();
        JsonObject factions = new JsonObject();
        JsonObject members = new JsonObject();
        FACTIONS.forEach(factions::addProperty);
        MEMBERS.forEach((id, membership) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("faction", membership.faction());
            entry.addProperty("grade", membership.grade());
            members.add(id.toString(), entry);
        });
        root.add("factions", factions);
        root.add("members", members);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, GSON.toJson(root));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveNotSupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            System.err.println("[CITIZEN Factions] Impossible de sauvegarder : " + ex.getMessage());
        }
    }

    public static synchronized boolean create(String id, String name) {
        if (FACTIONS.containsKey(id)) return false;
        FACTIONS.put(id, name);
        return true;
    }

    public static synchronized boolean exists(String id) { return FACTIONS.containsKey(id); }
    public static synchronized String factionName(String id) { return FACTIONS.getOrDefault(id, id); }
    public static synchronized Membership get(Player player) { return MEMBERS.get(player.getUUID()); }

    public static synchronized boolean add(ServerPlayer player, String faction, String grade) {
        if (!FACTIONS.containsKey(faction) || !FactionCommands.isValidGrade(grade)
                || MEMBERS.containsKey(player.getUUID())) return false;
        MEMBERS.put(player.getUUID(), new Membership(faction, grade));
        return true;
    }

    public static synchronized Map<UUID, Membership> members(String faction) {
        Map<UUID, Membership> result = new HashMap<>();
        MEMBERS.forEach((id, membership) -> {
            if (membership.faction().equals(faction)) result.put(id, membership);
        });
        return Map.copyOf(result);
    }

    public static synchronized boolean transfer(ServerPlayer player, String faction) {
        Membership old = MEMBERS.get(player.getUUID());
        if (old == null || !FACTIONS.containsKey(faction)) return false;
        MEMBERS.put(player.getUUID(), new Membership(faction, old.grade()));
        return true;
    }

    public static synchronized boolean remove(Player player) { return MEMBERS.remove(player.getUUID()) != null; }

    public static synchronized boolean setGrade(Player player, String grade) {
        Membership old = MEMBERS.get(player.getUUID());
        if (old == null || !FactionCommands.isValidGrade(grade)) return false;
        MEMBERS.put(player.getUUID(), new Membership(old.faction(), grade));
        return true;
    }

    public static synchronized Map<String, String> factions() { return Map.copyOf(FACTIONS); }

    public record Membership(String faction, String grade) {}
}
