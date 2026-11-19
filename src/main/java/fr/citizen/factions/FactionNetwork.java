package fr.citizen.factions;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = CitizenFactions.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class FactionNetwork {
    private FactionNetwork() {}

    public record OpenMenuRequest() implements CustomPacketPayload {
        public static final Type<OpenMenuRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenFactions.MOD_ID, "open_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenMenuRequest> STREAM_CODEC =
            StreamCodec.unit(new OpenMenuRequest());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record FactionMenuData(String faction, String grade, String roster, String bond, String status)
        implements CustomPacketPayload {
        public static final Type<FactionMenuData> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenFactions.MOD_ID, "faction_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FactionMenuData> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, FactionMenuData::faction,
                ByteBufCodecs.STRING_UTF8, FactionMenuData::grade,
                ByteBufCodecs.STRING_UTF8, FactionMenuData::roster,
                ByteBufCodecs.STRING_UTF8, FactionMenuData::bond,
                ByteBufCodecs.STRING_UTF8, FactionMenuData::status,
                FactionMenuData::new
            );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(OpenMenuRequest.TYPE, OpenMenuRequest.STREAM_CODEC, FactionNetwork::handleOpenMenu);
        registrar.playToClient(FactionMenuData.TYPE, FactionMenuData.STREAM_CODEC, FactionNetwork::handleMenuData);
    }

    private static void handleOpenMenu(OpenMenuRequest request, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) sendMenu(player);
    }

    public static void sendMenu(ServerPlayer player) {
        FactionStore.Membership membership = FactionStore.get(player);
        String faction = membership == null ? "Aucune faction" : FactionStore.factionName(membership.faction());
        String grade = membership == null ? "—" : displayGrade(membership.grade());
        String status = membership == null
            ? "Tu n'es affilié à aucune faction. Contacte un administrateur pour être recruté."
            : "Membres : " + FactionStore.members(membership.faction()).size();

        String roster = "";
        if (membership != null) {
            Map<UUID, FactionStore.Membership> members = FactionStore.members(membership.faction());
            roster = members.entrySet().stream()
                .sorted(Comparator.comparing(entry -> player.getServer().getPlayerList().getPlayer(entry.getKey()) == null ? 1 : 0))
                .limit(40)
                .map(entry -> {
                    ServerPlayer online = player.getServer().getPlayerList().getPlayer(entry.getKey());
                    String name = online != null ? online.getGameProfile().getName()
                        : player.getServer().getProfileCache().get(entry.getKey()).map(profile -> profile.getName()).orElse("Joueur inconnu");
                    return (online != null ? "● " : "○ ") + name + " — " + displayGrade(entry.getValue().grade());
                }).collect(Collectors.joining("\n"));
        }
        int bondValue = DragonBondHandler.getCurrentRidingBond(player);
        String bond = bondValue >= 0 ? "Lien actuel : " + bondValue + "%." :
            "Aucun dragon monté. Le lien progresse pendant que tu montes ton dragon.";
        PacketDistributor.sendToPlayer(player, new FactionMenuData(faction, grade, roster, bond, status));
    }

    private static void handleMenuData(FactionMenuData data, IPayloadContext context) {
        try {
            Class<?> client = Class.forName("fr.citizen.factions.client.FactionClient");
            client.getMethod("open", FactionMenuData.class).invoke(null, data);
        } catch (ReflectiveOperationException exception) {
            System.err.println("[CITIZEN Factions] Impossible d'ouvrir le menu joueur : " + exception.getMessage());
        }
    }

    private static String displayGrade(String grade) {
        return switch (grade) {
            case "primodiau" -> "Primodiau";
            case "alpha" -> "Alpha";
            case "confirme" -> "Confirmé";
            default -> "Petit";
        };
    }
}
