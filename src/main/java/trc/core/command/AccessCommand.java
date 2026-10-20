package trc.core.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import trc.core.Texts.Text;
import trc.core.net.TRCCoreNetworking;
import trc.core.settings.SettingsAccessList;
import trc.core.settings.SettingsAudit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import static trc.core.Texts.text;
import static trc.core.Texts.chat;

public final class AccessCommand {

    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    context.getSource().getServer().getPlayerList().getPlayers().stream()
                            .map(player -> player.getName().getString()),
                    builder);

    private record Action(Text other, Text you, Text self, Text target) {}

    private record ListSpec(String literal, SettingsAccessList.Kind kind, CommandNotice.Audience audience,
                            Action add, Action remove, Text alreadyOn, Text notOn, Text heading, Text empty) {}

    private static final List<ListSpec> LISTS = List.of(
            new ListSpec("whitelist", SettingsAccessList.Kind.WHITELIST, CommandNotice.Audience.SETTINGS_EDITORS,
                    new Action(
                            text("trc-core.access.whitelist.add.other", "%1$s granted %2$s access to the TRC server settings."),
                            text("trc-core.access.whitelist.add.you", "You granted %s access to the TRC server settings."),
                            text("trc-core.access.whitelist.add.self", "You granted yourself access to the TRC server settings."),
                            text("trc-core.access.whitelist.add.target", "%s granted you access to the TRC server settings.")),
                    new Action(
                            text("trc-core.access.whitelist.remove.other", "%1$s revoked %2$s's granted access to the TRC server settings."),
                            text("trc-core.access.whitelist.remove.you", "You revoked %s's granted access to the TRC server settings."),
                            text("trc-core.access.whitelist.remove.self", "You revoked your own granted access to the TRC server settings."),
                            text("trc-core.access.whitelist.remove.target", "%s revoked your granted access to the TRC server settings.")),
                    text("trc-core.access.whitelist.already", "%s already has granted access to the TRC server settings."),
                    text("trc-core.access.whitelist.not_on", "%s has no granted access to the TRC server settings."),
                    text("trc-core.access.whitelist.heading", "Granted access to the TRC server settings:"),
                    text("trc-core.access.whitelist.empty", "Nobody has granted access to the TRC server settings.")),
            new ListSpec("blacklist", SettingsAccessList.Kind.BLACKLIST, CommandNotice.Audience.SETTINGS_EDITORS,
                    new Action(
                            text("trc-core.access.blacklist.add.other", "%1$s blocked %2$s from the TRC server settings."),
                            text("trc-core.access.blacklist.add.you", "You blocked %s from the TRC server settings."),
                            text("trc-core.access.blacklist.add.self", "You blocked yourself from the TRC server settings."),
                            text("trc-core.access.blacklist.add.target", "%s blocked you from the TRC server settings.")),
                    new Action(
                            text("trc-core.access.blacklist.remove.other", "%1$s unblocked %2$s from the TRC server settings."),
                            text("trc-core.access.blacklist.remove.you", "You unblocked %s from the TRC server settings."),
                            text("trc-core.access.blacklist.remove.self", "You unblocked yourself from the TRC server settings."),
                            text("trc-core.access.blacklist.remove.target", "%s unblocked you from the TRC server settings.")),
                    text("trc-core.access.blacklist.already", "%s is already blocked from the TRC server settings."),
                    text("trc-core.access.blacklist.not_on", "%s is not blocked from the TRC server settings."),
                    text("trc-core.access.blacklist.heading", "Blocked from the TRC server settings:"),
                    text("trc-core.access.blacklist.empty", "Nobody is blocked from the TRC server settings.")),
            new ListSpec("editors", SettingsAccessList.Kind.PERMISSION_EDITORS, CommandNotice.Audience.PERMISSION_ADMINS,
                    new Action(
                            text("trc-core.access.editors.add.other", "%1$s made %2$s an administrator of TRC permissions."),
                            text("trc-core.access.editors.add.you", "You made %s an administrator of TRC permissions."),
                            text("trc-core.access.editors.add.self", "You made yourself an administrator of TRC permissions."),
                            text("trc-core.access.editors.add.target", "%s made you an administrator of TRC permissions.")),
                    new Action(
                            text("trc-core.access.editors.remove.other", "%1$s removed %2$s as an administrator of TRC permissions."),
                            text("trc-core.access.editors.remove.you", "You removed %s as an administrator of TRC permissions."),
                            text("trc-core.access.editors.remove.self", "You removed yourself as an administrator of TRC permissions."),
                            text("trc-core.access.editors.remove.target", "%s removed you as an administrator of TRC permissions.")),
                    text("trc-core.access.editors.already", "%s is already an administrator of TRC permissions."),
                    text("trc-core.access.editors.not_on", "%s is not an administrator of TRC permissions."),
                    text("trc-core.access.editors.heading", "Administrators of TRC permissions:"),
                    text("trc-core.access.editors.empty", "Nobody is an administrator of TRC permissions."))
    );


    public static boolean maySetPermissions(@NotNull CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();

        return player == null || TRCCoreNetworking.maySetPermissionSetting(source.getServer(), player);
    }

    private static boolean refuseWithoutPermission(@NotNull CommandSourceStack source) {
        TRCCoreNetworking.refreshPermissions(source.getServer());

        if (maySetPermissions(source)) return false;

        source.sendFailure(chat("trc-core.access.denied", "You are not allowed to manage TRC permissions.")
                .withStyle(ChatFormatting.RED));
        return true;
    }


    public static void addTo(@NotNull LiteralArgumentBuilder<CommandSourceStack> root) {
        for (ListSpec spec : LISTS) {
            root.then(Commands.literal(spec.literal()).requires(AccessCommand::maySetPermissions)
                    .then(Commands.literal("add")
                            .then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE_PLAYERS)
                                    .executes(context -> add(context, spec))))
                    .then(Commands.literal("remove")
                            .then(Commands.argument("player", StringArgumentType.word()).suggests(entriesOf(spec))
                                    .executes(context -> remove(context, spec))))
                    .then(Commands.literal("list")
                            .executes(context -> list(context, spec))));
        }
    }

    private static @NotNull SuggestionProvider<CommandSourceStack> entriesOf(@NotNull ListSpec spec) {
        return (_, builder) -> {
            List<String> names = new ArrayList<>();

            for (SettingsAccessList.Entry entry : SettingsAccessList.entries(spec.kind())) {
                names.add(entry.name != null ? entry.name : entry.uuid);
            }

            return SharedSuggestionProvider.suggest(names, builder);
        };
    }


    private static int add(@NotNull CommandContext<CommandSourceStack> context, @NotNull ListSpec spec) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "player");

        if (TRCServerCommand.refuseIfThrottled(source)) return 0;
        if (refuseWithoutPermission(source)) return 0;

        ServerPlayer online = onlinePlayer(source.getServer(), name);
        String target = online != null ? online.getName().getString() : name;

        boolean added = online != null
                ? SettingsAccessList.add(spec.kind(), online.nameAndId())
                : SettingsAccessList.add(spec.kind(), name);

        if (!added) {
            source.sendFailure(spec.alreadyOn().get(target).withStyle(ChatFormatting.RED));
            return 0;
        }

        SettingsAudit.action(source.getServer(), source.getPlayer(), spec.literal() + " add", name);

        TRCCoreNetworking.broadcast(source.getServer());

        announce(source, spec, spec.add(), target, online);
        return 1;
    }

    private static int remove(@NotNull CommandContext<CommandSourceStack> context, @NotNull ListSpec spec) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "player");

        if (TRCServerCommand.refuseIfThrottled(source)) return 0;
        if (refuseWithoutPermission(source)) return 0;

        ServerPlayer online = onlinePlayer(source.getServer(), name);
        String target = online != null ? online.getName().getString() : name;

        if (!SettingsAccessList.remove(spec.kind(), name)) {
            source.sendFailure(spec.notOn().get(target).withStyle(ChatFormatting.RED));
            return 0;
        }

        SettingsAudit.action(source.getServer(), source.getPlayer(), spec.literal() + " remove", name);

        TRCCoreNetworking.broadcast(source.getServer());

        announce(source, spec, spec.remove(), target, online);
        return 1;
    }

    private static void announce(@NotNull CommandSourceStack source, @NotNull ListSpec spec, @NotNull Action action,
                                 @NotNull String target, @Nullable ServerPlayer affected) {
        boolean self = affected != null && affected == source.getPlayer();
        Component actor = CommandNotice.actor(source);

        source.sendSuccess(() -> self ? action.self().get() : action.you().get(target), false);

        if (affected != null && !self) {
            affected.sendSystemMessage(action.target().get(actor).withStyle(ChatFormatting.YELLOW));
        }

        CommandNotice.toOthers(source, spec.audience(), affected, action.other().get(actor, target));
    }

    private static int list(@NotNull CommandContext<CommandSourceStack> context, @NotNull ListSpec spec) {
        CommandSourceStack source = context.getSource();

        if (TRCServerCommand.refuseIfThrottled(source)) return 0;
        if (refuseWithoutPermission(source)) return 0;

        List<SettingsAccessList.Entry> entries = SettingsAccessList.entries(spec.kind());

        if (entries.isEmpty()) {
            source.sendSuccess(() -> spec.empty().get().withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        source.sendSuccess(() -> spec.heading().get().withStyle(ChatFormatting.YELLOW), false);

        for (SettingsAccessList.Entry entry : entries) {
            source.sendSuccess(() -> Component.literal("  ").append(describe(entry)), false);
        }

        return entries.size();
    }

    private static @NotNull Component describe(@NotNull SettingsAccessList.Entry entry) {
        if (entry.name != null && entry.uuid != null) return Component.literal(entry.name + " (" + entry.uuid + ")");
        if (entry.name != null) return chat("trc-core.access.no_id", "%s (no id yet)", entry.name);

        return Component.literal(String.valueOf(entry.uuid));
    }

    private static @Nullable ServerPlayer onlinePlayer(@NotNull MinecraftServer server, @NotNull String raw) {
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException e) {
            return server.getPlayerList().getPlayerByName(raw.trim());
        }
    }

    private AccessCommand() {}
}
