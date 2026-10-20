package trc.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import trc.core.TRCCore;
import trc.core.net.RequestLimiter;
import trc.core.net.TRCCoreNetworking;
import trc.core.settings.ISettingsHolder;
import trc.core.settings.Setting;
import trc.core.settings.SettingsAudit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import static trc.core.Texts.chat;

public final class TRCServerCommand {

    private static final SuggestionProvider<CommandSourceStack> MODS = (context, builder) ->
            SharedSuggestionProvider.suggest(reachableMods(context.getSource()), builder);

    private static final SuggestionProvider<CommandSourceStack> SETTINGS = (context, builder) -> {
        List<Setting> settings = settingsOf(context.getSource(), StringArgumentType.getString(context, "mod"));
        return SharedSuggestionProvider.suggest(SettingsCommand.idsOf(settings), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> VALUES = (context, builder) -> {
        Setting setting = resolve(context.getSource(),
                StringArgumentType.getString(context, "mod"),
                StringArgumentType.getString(context, "setting"));

        return setting == null
                ? builder.buildFuture()
                : SharedSuggestionProvider.suggest(SettingsCommand.valueSuggestions(setting), builder);
    };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> build(dispatcher));
    }

    private static void build(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("trc")
                .requires(source -> mayEdit(source) || AccessCommand.maySetPermissions(source))
                .then(Commands.argument("mod", StringArgumentType.word())
                        .suggests(MODS)
                        .then(Commands.argument("setting", StringArgumentType.word()).suggests(SETTINGS)
                                .executes(TRCServerCommand::read)
                                .then(Commands.argument("value", StringArgumentType.greedyString()).suggests(VALUES)
                                        .executes(TRCServerCommand::write))));

        root.then(Commands.literal("reset").requires(TRCServerCommand::mayEdit)
                .then(Commands.argument("mod", StringArgumentType.word()).suggests(MODS)
                        .then(Commands.argument("setting", StringArgumentType.word()).suggests(SETTINGS)
                                .executes(TRCServerCommand::reset))));

        root.then(Commands.literal("list").requires(TRCServerCommand::mayEdit)
                .executes(TRCServerCommand::list));

        AccessCommand.addTo(root);

        dispatcher.register(root);
    }


    private static boolean mayEdit(@NotNull CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();

        return player == null || TRCCoreNetworking.mayEdit(source.getServer(), player);
    }

    private static boolean mayReach(@NotNull CommandSourceStack source, String modId) {
        ServerPlayer player = source.getPlayer();

        if (player == null) return false;

        return TRCCore.MOD_ID.equals(modId) ? !TRCCoreNetworking.maySetPermissionSetting(source.getServer(), player) : !TRCCoreNetworking.mayEdit(source.getServer(), player);
    }

    private static void refreshIfPermissionSetting(@NotNull CommandSourceStack source, String modId) {
        if (TRCCore.MOD_ID.equals(modId)) TRCCoreNetworking.refreshPermissions(source.getServer());
    }

    static boolean refuseIfThrottled(@NotNull CommandSourceStack source) {
        if (RequestLimiter.tryAcquire(source.getServer(), source.getPlayer())) return false;

        source.sendFailure(chat("trc-core.command.throttled", "Too many TRC-Core requests at once. Try again in a moment.")
                .withStyle(ChatFormatting.RED));
        return true;
    }

    private static @NotNull List<String> reachableMods(@NotNull CommandSourceStack source) {
        List<String> mods = new ArrayList<>();
        MinecraftServer server = source.getServer();

        for (Map.Entry<String, ISettingsHolder> entry : TRCCoreNetworking.serverHolders().entrySet()) {
            if (mayReach(source, entry.getKey())) continue;

            if (SettingsCommand.hasAnyId(TRCCoreNetworking.visibleSettings(server, entry.getValue()))) {
                mods.add(entry.getKey());
            }
        }

        return mods;
    }

    private static @Nullable List<Setting> settingsOf(@NotNull CommandSourceStack source, String modId) {
        if (mayReach(source, modId)) return null;

        ISettingsHolder holder = holderOf(modId);

        return holder == null ? null : TRCCoreNetworking.visibleSettings(source.getServer(), holder);
    }

    private static @Nullable ISettingsHolder holderOf(String modId) {
        return TRCCoreNetworking.serverHolders().get(modId);
    }

    private static @Nullable Setting resolve(@NotNull CommandSourceStack source, String modId, String settingId) {
        return SettingsCommand.byId(settingsOf(source, modId), settingId);
    }


    private static int list(@NotNull CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (refuseIfThrottled(source)) return 0;

        List<String> mods = reachableMods(source);
        mods.sort(null);

        if (mods.isEmpty()) {
            source.sendSuccess(() -> chat("trc-core.command.list.none_server", "No server mods with settings you can reach.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        source.sendSuccess(() -> SettingsCommand.listHeading(chat("trc-core.command.list.server", "Server mods:")), false);

        for (String modId : mods) {
            int count = TRCCoreNetworking.visibleSettings(source.getServer(), Objects.requireNonNull(holderOf(modId))).size();

            source.sendSuccess(() -> SettingsCommand.listEntry(modId, SettingsCommand.displayNameOf(modId), count), false);
        }

        return mods.size();
    }

    private static int read(@NotNull CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (refuseIfThrottled(source)) return 0;
        String modId = StringArgumentType.getString(context, "mod");
        String settingId = StringArgumentType.getString(context, "setting");

        refreshIfPermissionSetting(source, modId);

        if (mayReach(source, modId)) {
            source.sendFailure(denied(modId));
            return 0;
        }

        List<Setting> settings = settingsOf(source, modId);

        if (settings == null) {
            source.sendFailure(SettingsCommand.unknownMod(modId));
            return 0;
        }

        Setting setting = SettingsCommand.byId(settings, settingId);

        if (setting == null) {
            source.sendFailure(SettingsCommand.unknownSetting(modId, settingId));
            return 0;
        }

        source.sendSuccess(() -> SettingsCommand.reading(modId, setting), false);
        return 1;
    }

    private static int write(@NotNull CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (refuseIfThrottled(source)) return 0;
        String modId = StringArgumentType.getString(context, "mod");
        String settingId = StringArgumentType.getString(context, "setting");
        String raw = StringArgumentType.getString(context, "value");

        refreshIfPermissionSetting(source, modId);

        if (mayReach(source, modId)) {
            source.sendFailure(denied(modId));
            return 0;
        }

        ISettingsHolder holder = holderOf(modId);

        if (holder == null) {
            source.sendFailure(SettingsCommand.unknownMod(modId));
            return 0;
        }

        Setting setting = SettingsCommand.byId(
                TRCCoreNetworking.visibleSettings(source.getServer(), holder), settingId);

        if (setting == null) {
            source.sendFailure(SettingsCommand.unknownSetting(modId, settingId));
            return 0;
        }

        SettingsCommand.ParsedValue value = SettingsCommand.parse(setting, raw);

        if (value == null) {
            source.sendFailure(SettingsCommand.badValue(setting, raw));
            return 0;
        }

        String before = SettingsAudit.valueOf(setting);

        SettingsCommand.apply(setting, value);
        TRCCoreNetworking.commitServerSide(source.getServer(), holder);
        SettingsAudit.settingChanged(source.getServer(), source.getPlayer(), modId, setting,
                before, SettingsAudit.valueOf(setting));

        source.sendSuccess(() -> SettingsCommand.changed(modId, setting, value), false);

        CommandNotice.toOthers(source, CommandNotice.Audience.SETTINGS_EDITORS, null,
                chat("trc-core.notice.command.set", "%s set %s %s to %s (%s).",
                        CommandNotice.actor(source), modId, setting.id,
                        SettingsCommand.shownValue(modId, setting), setting.name));
        return 1;
    }

    private static int reset(@NotNull CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (refuseIfThrottled(source)) return 0;
        String modId = StringArgumentType.getString(context, "mod");
        String settingId = StringArgumentType.getString(context, "setting");

        refreshIfPermissionSetting(source, modId);

        if (mayReach(source, modId)) {
            source.sendFailure(denied(modId));
            return 0;
        }

        ISettingsHolder holder = holderOf(modId);

        if (holder == null) {
            source.sendFailure(SettingsCommand.unknownMod(modId));
            return 0;
        }

        Setting setting = SettingsCommand.byId(
                TRCCoreNetworking.visibleSettings(source.getServer(), holder), settingId);

        if (setting == null) {
            source.sendFailure(SettingsCommand.unknownSetting(modId, settingId));
            return 0;
        }

        String before = SettingsAudit.valueOf(setting);

        setting.reset();
        TRCCoreNetworking.commitServerSide(source.getServer(), holder);
        SettingsAudit.settingChanged(source.getServer(), source.getPlayer(), modId, setting,
                before, SettingsAudit.valueOf(setting));

        source.sendSuccess(() -> SettingsCommand.wasReset(modId, setting), false);

        CommandNotice.toOthers(source, CommandNotice.Audience.SETTINGS_EDITORS, null,
                chat("trc-core.notice.command.reset", "%s reset %s %s to %s (%s).",
                        CommandNotice.actor(source), modId, setting.id,
                        SettingsCommand.shownValue(modId, setting), setting.name));
        return 1;
    }

    private static @NotNull Component denied(String modId) {
        return TRCCore.MOD_ID.equals(modId)
                ? chat("trc-core.command.denied.core", "Only a permission editor may change TRC-Core's own settings.")
                : chat("trc-core.command.denied", "You are not allowed to change this server's settings.");
    }

    private TRCServerCommand() {}
}
