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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = CitizenFactions.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class FactionNetwork {
    private static final int MEMBERS_PER_PAGE = 8;
    private FactionNetwork() {}

    public record OpenMenuRequest(int page) implements CustomPacketPayload {
        public static final Type<OpenMenuRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenFactions.MOD_ID, "open_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenMenuRequest> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenMenuRequest::page, OpenMenuRequest::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record FactionMenuData(String faction, String grade, String roster, String bond, String status,
                                  int page, int totalPages) implements CustomPacketPayload {
        public static final Type<FactionMenuData> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenFactions.MOD_ID, "faction_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FactionMenuData> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, FactionMenuData::faction,
                ByteBufCodecs.STRING_UTF8, FactionMenuData::grade,
                ByteBufCodecs.STRING_UTF8, FactionMenuData::roster,
                ByteBufCodecs.STRING_UTF8, FactionMenuData::bond,
                ByteBufCodecs.STRING_UTF8, FactionMenuData::status,
                ByteBufCodecs.VAR_INT, FactionMenuData::page,
                ByteBufCodecs.VAR_INT, FactionMenuData::totalPages,
                FactionMenuData::new
            );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(OpenMenuRequest.TYPE, OpenMenuRequest.STREAM_CODEC, FactionNetwork::handleOpenMenu);
        registrar.playToClient(FactionMenuData.TYPE, FactionMenuData.STREAM_CODEC, FactionNetwork::handleMenuData);
    }

    private static void handleOpenMenu(OpenMenuRequest request, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) sendMenu(player, request.page());
    }

    public static void sendMenu(ServerPlayer player) {
        sendMenu(player, 0);
    }

    private static void sendMenu(ServerPlayer player, int requestedPage) {
        FactionStore.Membership membership = FactionStore.get(player);
        String faction = membership == null ? "Aucune faction" : FactionStore.factionName(membership.faction());
        String grade = membership == null ? "—" : displayGrade(membership.grade());
        String status = membership == null
            ? "Tu n'es affilié à aucune faction. Contacte un administrateur pour être recruté."
            : "Membres : " + FactionStore.members(membership.faction()).size();

        List<Map.Entry<UUID, FactionStore.Membership>> sortedMembers = membership == null ? List.of()
            : FactionStore.members(membership.faction()).entrySet().stream()
                .sorted(Comparator.comparing(entry -> player.getServer().getPlayerList().getPlayer(entry.getKey()) == null ? 1 : 0))
                .collect(Collectors.toList());

        int totalPages = Math.max(1, (int) Math.ceil(sortedMembers.size() / (double) MEMBERS_PER_PAGE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        int from = page * MEMBERS_PER_PAGE;
        String roster = sortedMembers.stream().skip(from).limit(MEMBERS_PER_PAGE).map(entry -> {
            ServerPlayer online = player.getServer().getPlayerList().getPlayer(entry.getKey());
            String name = online != null ? online.getGameProfile().getName()
                : player.getServer().getProfileCache().get(entry.getKey()).map(profile -> profile.getName()).orElse("Joueur inconnu");
            return (online != null ? "● " : "○ ") + name + " — " + displayGrade(entry.getValue().grade());
        }).collect(Collectors.joining("\n"));

        int bondValue = DragonBondHandler.getCurrentRidingBond(player);
        String bond = bondValue >= 0 ? "Lien actuel : " + bondValue + "%."
            : "Aucun dragon monté. Le lien progresse pendant que tu montes ton dragon.";
        PacketDistributor.sendToPlayer(player, new FactionMenuData(faction, grade, roster, bond, status, page, totalPages));
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
