package fr.citizen.factions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Set;

public final class FactionCommands {
    private static final Set<String> GRADES = Set.of("primodiau", "alpha", "confirme", "petit");
    private static final Set<String> FACTION_IDS = Set.of("dragonnier", "chasseur_de_dragon");

    private FactionCommands() {}

    public static boolean isValidGrade(String grade) {
        return grade != null && GRADES.contains(grade.toLowerCase(Locale.ROOT));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("faction")
            .then(Commands.literal("create").requires(source -> source.hasPermission(2))
                .then(Commands.argument("faction", StringArgumentType.word())
                    .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(FACTION_IDS, builder))
                    .executes(context -> {
                        String id = normalizeFaction(StringArgumentType.getString(context, "faction"));
                        if (id == null) {
                            context.getSource().sendFailure(Component.literal("Faction inconnue. Choix : dragonnier ou chasseur_de_dragon."));
                            return 0;
                        }
                        if (!FactionStore.create(id, displayFaction(id))) {
                            context.getSource().sendFailure(Component.literal("Cette faction existe déjà."));
                            return 0;
                        }
                        FactionStore.save(context.getSource().getServer());
                        context.getSource().sendSuccess(() -> Component.literal("Faction « " + displayFaction(id) + " » créée."), true);
                        return 1;
                    })))
            .then(Commands.literal("add").requires(source -> source.hasPermission(2))
                .then(Commands.argument("joueur", EntityArgument.player())
                    .then(Commands.argument("faction", StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(FACTION_IDS, builder))
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "joueur");
                            String id = normalizeFaction(StringArgumentType.getString(context, "faction"));
                            if (id == null) {
                                context.getSource().sendFailure(Component.literal("Faction inconnue."));
                                return 0;
                            }
                            if (!FactionStore.exists(id)) {
                                context.getSource().sendFailure(Component.literal("Cette faction n'existe pas. Crée-la avec /faction create."));
                                return 0;
                            }
                            if (FactionStore.get(target) != null || !FactionStore.add(target, id, "petit")) {
                                context.getSource().sendFailure(Component.literal("Ce joueur appartient déjà à une faction. Retire-le d'abord avec /faction remove."));
                                return 0;
                            }
                            FactionStore.save(context.getSource().getServer());
                            target.sendSystemMessage(Component.literal("Tu as rejoint la faction « " + displayFaction(id) + " » avec le grade Petit."));
                            context.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " a rejoint « " + displayFaction(id) + " »."), true);
                            return 1;
                        }))))
            .then(Commands.literal("transfer").requires(source -> source.hasPermission(2))
                .then(Commands.argument("joueur", EntityArgument.player())
                    .then(Commands.argument("faction", StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(FACTION_IDS, builder))
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "joueur");
                            String id = normalizeFaction(StringArgumentType.getString(context, "faction"));
                            if (id == null || !FactionStore.exists(id)) {
                                context.getSource().sendFailure(Component.literal("Faction inconnue ou non créée."));
                                return 0;
                            }
                            FactionStore.Membership previous = FactionStore.get(target);
                            if (previous == null) {
                                context.getSource().sendFailure(Component.literal("Ce joueur n'appartient à aucune faction. Utilise /faction add."));
                                return 0;
                            }
                            if (previous.faction().equals(id)) {
                                context.getSource().sendFailure(Component.literal("Ce joueur appartient déjà à cette faction."));
                                return 0;
                            }
                            if (!FactionStore.transfer(target, id)) {
                                context.getSource().sendFailure(Component.literal("Transfert impossible."));
                                return 0;
                            }
                            FactionStore.save(context.getSource().getServer());
                            target.sendSystemMessage(Component.literal("Tu as été transféré dans « " + displayFaction(id) + " ». Ton grade est conservé : " + displayGrade(previous.grade()) + "."));
                            context.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " a été transféré vers « " + displayFaction(id) + " »."), true);
                            return 1;
                        }))))
            .then(Commands.literal("remove").requires(source -> source.hasPermission(2))
                .then(Commands.argument("joueur", EntityArgument.player()).executes(context -> {
                    ServerPlayer target = EntityArgument.getPlayer(context, "joueur");
                    if (!FactionStore.remove(target)) {
                        context.getSource().sendFailure(Component.literal("Ce joueur n'appartient à aucune faction."));
                        return 0;
                    }
                    FactionStore.save(context.getSource().getServer());
                    target.sendSystemMessage(Component.literal("Tu as été retiré de ta faction."));
                    context.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " a été retiré de sa faction."), true);
                    return 1;
                })))
            .then(Commands.literal("grade").requires(source -> source.hasPermission(2))
                .then(Commands.argument("joueur", EntityArgument.player())
                    .then(Commands.argument("grade", StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(GRADES, builder))
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "joueur");
                            String grade = StringArgumentType.getString(context, "grade").toLowerCase(Locale.ROOT);
                            if (!isValidGrade(grade)) {
                                context.getSource().sendFailure(Component.literal("Grades : primodiau, alpha, confirme, petit."));
                                return 0;
                            }
                            if (!FactionStore.setGrade(target, grade)) {
                                context.getSource().sendFailure(Component.literal("Ce joueur n'appartient à aucune faction."));
                                return 0;
                            }
                            FactionStore.save(context.getSource().getServer());
                            target.sendSystemMessage(Component.literal("Ton grade est maintenant : " + displayGrade(grade) + "."));
                            context.getSource().sendSuccess(() -> Component.literal("Grade de " + target.getGameProfile().getName() + " modifié : " + displayGrade(grade) + "."), true);
                            return 1;
                        }))))
            .then(Commands.literal("list").requires(source -> source.hasPermission(2)).executes(context -> {
                if (FactionStore.factions().isEmpty()) {
                    context.getSource().sendSuccess(() -> Component.literal("Aucune faction créée. Utilise /faction create."), false);
                } else {
                    FactionStore.factions().forEach((id, name) ->
                        context.getSource().sendSuccess(() -> Component.literal(id + " : " + name), false));
                }
                return 1;
            }))
            .then(Commands.literal("menu").executes(context -> {
                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                showPlayerMenu(player);
                return 1;
            }))
            .then(Commands.literal("info")
                .executes(context -> {
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                    showPlayerMenu(player);
                    return 1;
                })
                .then(Commands.argument("joueur", EntityArgument.player())
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> {
                        ServerPlayer target = EntityArgument.getPlayer(context, "joueur");
                        FactionStore.Membership membership = FactionStore.get(target);
                        if (membership == null) {
                            context.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " n'appartient à aucune faction."), false);
                        } else {
                            context.getSource().sendSuccess(() -> Component.literal(
                                target.getGameProfile().getName() + " — " + FactionStore.factionName(membership.faction())
                                    + " — Grade : " + displayGrade(membership.grade())), false);
                        }
                        return 1;
                    })))
            .then(Commands.literal("members").requires(source -> source.hasPermission(2))
                .then(Commands.argument("faction", StringArgumentType.word())
                    .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(FACTION_IDS, builder))
                    .executes(context -> {
                        String id = normalizeFaction(StringArgumentType.getString(context, "faction"));
                        if (id == null || !FactionStore.exists(id)) {
                            context.getSource().sendFailure(Component.literal("Faction inconnue ou non créée."));
                            return 0;
                        }
                        var members = FactionStore.members(id);
                        context.getSource().sendSuccess(() -> Component.literal(
                            "Membres de " + displayFaction(id) + " : " + members.size()), false);
                        if (members.isEmpty()) {
                            context.getSource().sendSuccess(() -> Component.literal("Aucun membre."), false);
                        } else {
                            members.forEach((uuid, membership) -> {
                                ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(uuid);
                                String name = online != null ? online.getGameProfile().getName() : uuid.toString();
                                context.getSource().sendSuccess(() -> Component.literal(
                                    "• " + name + " — " + displayGrade(membership.grade())), false);
                            });
                        }
                        return 1;
                    })))
        );
    }

    private static void showPlayerMenu(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("━━━━━━━━━━ CITIZEN • FACTIONS ━━━━━━━━━━"));
        FactionStore.Membership membership = FactionStore.get(player);
        if (membership == null) {
            player.sendSystemMessage(Component.literal("Statut : Sans faction"));
            player.sendSystemMessage(Component.literal("Contacte un administrateur pour rejoindre les Dragonniers ou les Chasseurs de dragons."));
        } else {
            String faction = FactionStore.factionName(membership.faction());
            player.sendSystemMessage(Component.literal("Faction : " + faction));
            player.sendSystemMessage(Component.literal("Grade : " + displayGrade(membership.grade())));
            var members = FactionStore.members(membership.faction());
            long online = members.keySet().stream()
                .filter(id -> player.getServer().getPlayerList().getPlayer(id) != null).count();
            player.sendSystemMessage(Component.literal("Effectif : " + members.size() + " membre(s), dont " + online + " connecté(s)"));
            player.sendSystemMessage(Component.literal("── Membres ──"));
            members.forEach((uuid, data) -> {
                ServerPlayer onlinePlayer = player.getServer().getPlayerList().getPlayer(uuid);
                String name = onlinePlayer != null ? onlinePlayer.getGameProfile().getName() : player.getServer().getProfileCache()
                    .get(uuid).map(profile -> profile.getName()).orElse("Joueur inconnu");
                player.sendSystemMessage(Component.literal((onlinePlayer != null ? "● " : "○ ") + name
                    + " — " + displayGrade(data.grade())));
            });
        }
        int bond = DragonBondHandler.getCurrentRidingBond(player);
        player.sendSystemMessage(Component.literal("── Lien draconique ──"));
        player.sendSystemMessage(Component.literal(bond >= 0
            ? "Lien avec ta monture actuelle : " + bond + "%."
            : "Aucun dragon monté actuellement. Le lien progresse pendant que tu montes ton dragon."));
        player.sendSystemMessage(Component.literal("Commandes : /faction menu • /faction info"));
        player.sendSystemMessage(Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private static String normalizeFaction(String raw) {
        String value = raw.toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "dragonnier" -> "dragonnier";
            case "chasseur_de_dragon", "chasseur" -> "chasseur_de_dragon";
            default -> null;
        };
    }

    private static String displayFaction(String id) {
        return id.equals("dragonnier") ? "Dragonnier" : "Chasseur de dragon";
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
