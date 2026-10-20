package trc.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

import trc.core.Texts;
import trc.core.net.ClientSettingsCache;
import trc.core.settings.ISettingsHolder;
import trc.core.settings.Setting;
import trc.core.settings.SettingsAudit;
import trc.core.settings.SettingsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

public final class TRCClientCommand {
    private static final String CLIENT_SUFFIX = "_client";

    private static final SuggestionProvider<FabricClientCommandSource> MODS = (_, builder) ->
            SharedSuggestionProvider.suggest(clientMods(), builder);

    private static final SuggestionProvider<FabricClientCommandSource> SETTINGS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    SettingsCommand.idsOf(clientSettingsOf(StringArgumentType.getString(context, "mod"))), builder);

    private static final SuggestionProvider<FabricClientCommandSource> VALUES = (context, builder) -> {
        Setting setting = SettingsCommand.byId(
                clientSettingsOf(StringArgumentType.getString(context, "mod")),
                StringArgumentType.getString(context, "setting"));

        return setting == null
                ? builder.buildFuture()
                : SharedSuggestionProvider.suggest(SettingsCommand.valueSuggestions(setting), builder);
    };

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> build(dispatcher));
    }

    private static void build(@NotNull CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("trc")
                .then(ClientCommands.argument("mod", StringArgumentType.word()).suggests(MODS)
                        .then(ClientCommands.argument("setting", StringArgumentType.word()).suggests(SETTINGS)
                                .executes(TRCClientCommand::read)
                                .then(ClientCommands.argument("value", StringArgumentType.greedyString()).suggests(VALUES)
                                        .executes(TRCClientCommand::write))));

        root.then(ClientCommands.literal("reset")
                .then(ClientCommands.argument("mod", StringArgumentType.word()).suggests(MODS)
                        .then(ClientCommands.argument("setting", StringArgumentType.word()).suggests(SETTINGS)
                                .executes(TRCClientCommand::reset))));

        root.then(ClientCommands.literal("list").executes(TRCClientCommand::list));

        dispatcher.register(root);
    }


    public static void graft(@NotNull CommandDispatcher<ClientSuggestionProvider> commands,
                             @NotNull FabricClientCommandSource source) {
        CommandNode<ClientSuggestionProvider> target = commands.getRoot().getChild("trc");

        if (target == null) return;

        CommandNode<FabricClientCommandSource> mine = ownTree().getRoot().getChild("trc");

        if (mine != null) copyInto(mine, target, source);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void copyInto(@NotNull CommandNode<FabricClientCommandSource> from, @NotNull CommandNode into,
                                 @NotNull FabricClientCommandSource source) {
        for (CommandNode<FabricClientCommandSource> child : from.getChildren()) {
            CommandNode existing = into.getChild(child.getName());

            if (existing != null) {
                copyInto(child, existing, source);
                continue;
            }

            if (!child.canUse(source)) continue;

            ArgumentBuilder<FabricClientCommandSource, ?> builder = child.createBuilder();
            builder.requires(ignored -> true);

            if (builder.getCommand() != null) builder.executes(_ -> 0);

            CommandNode<FabricClientCommandSource> copy = builder.build();

            copyInto(child, copy, source);
            into.addChild(copy);
        }
    }

    private static @Nullable CommandDispatcher<FabricClientCommandSource> ownTree;

    private static @NotNull CommandDispatcher<FabricClientCommandSource> ownTree() {
        if (ownTree == null) {
            CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();
            build(dispatcher);
            ownTree = dispatcher;
        }

        return ownTree;
    }

    public static @Nullable CompletableFuture<Suggestions> withOwnSuggestions(@NotNull String input,
                                                                            @NotNull FabricClientCommandSource source,
                                                                            @NotNull CompletableFuture<Suggestions> fromServer) {
        StringReader reader = new StringReader(input);

        if (reader.canRead() && reader.peek() == '/') reader.skip();

        if (!input.startsWith("trc ", reader.getCursor())) return null;

        CommandDispatcher<FabricClientCommandSource> own = ownTree();
        CompletableFuture<Suggestions> fromClient = own.getCompletionSuggestions(own.parse(reader, source));

        return fromServer.thenCombine(fromClient, (server, client) -> Suggestions.merge(input, List.of(server, client)));
    }


    private static int toServer(@NotNull CommandContext<FabricClientCommandSource> context, @NotNull String modId)
            throws CommandSyntaxException {
        if (!ClientSettingsCache.serverHasCore()) {
            context.getSource().sendError(SettingsCommand.unknownMod(modId));
            return 0;
        }

        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
    }

    private static void forward(@NotNull CommandContext<FabricClientCommandSource> context, @NotNull String line) {
        ClientPacketListener connection = context.getSource().getClient().getConnection();

        if (connection != null) connection.send(new ServerboundChatCommandPacket(line));
    }


    private static int list(@NotNull CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        List<String> mods = clientMods();

        mods.sort(null);

        if (mods.isEmpty()) {
            source.sendFeedback(Texts.chat("trc-core.command.list.none_client", "No client mods with settings.")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            source.sendFeedback(SettingsCommand.listHeading(Texts.chat("trc-core.command.list.client", "Client mods:")));

            for (String modId : mods) {
                source.sendFeedback(SettingsCommand.listEntry(modId, SettingsCommand.displayNameOf(modId),
                        Objects.requireNonNull(clientHolder(modId)).getSettings().size()));
            }
        }

        if (ClientSettingsCache.serverHasCore() && !ClientSettingsCache.sections().isEmpty()) {
            forward(context, "trc list");
        }

        return mods.size();
    }

    private static int read(@NotNull CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String modId = StringArgumentType.getString(context, "mod");
        String settingId = StringArgumentType.getString(context, "setting");

        ISettingsHolder holder = clientHolder(modId);

        if (holder == null) return toServer(context, modId);

        Setting setting = SettingsCommand.byId(holder.getSettings(), settingId);

        if (setting == null) {
            context.getSource().sendError(SettingsCommand.unknownSetting(modId, settingId));
            return 0;
        }

        context.getSource().sendFeedback(SettingsCommand.reading(modId, setting));
        return 1;
    }

    private static int reset(@NotNull CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String modId = StringArgumentType.getString(context, "mod");
        String settingId = StringArgumentType.getString(context, "setting");

        ISettingsHolder holder = clientHolder(modId);

        if (holder == null) return toServer(context, modId);

        Setting setting = SettingsCommand.byId(holder.getSettings(), settingId);

        if (setting == null) {
            context.getSource().sendError(SettingsCommand.unknownSetting(modId, settingId));
            return 0;
        }

        setting.reset();
        commit(holder);

        context.getSource().sendFeedback(SettingsCommand.wasReset(modId, setting));
        return 1;
    }

    private static int write(@NotNull CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        String modId = StringArgumentType.getString(context, "mod");
        String settingId = StringArgumentType.getString(context, "setting");
        String raw = StringArgumentType.getString(context, "value");

        ISettingsHolder holder = clientHolder(modId);

        if (holder == null) return toServer(context, modId);

        Setting setting = SettingsCommand.byId(holder.getSettings(), settingId);

        if (setting == null) {
            context.getSource().sendError(SettingsCommand.unknownSetting(modId, settingId));
            return 0;
        }

        SettingsCommand.ParsedValue value = SettingsCommand.parse(setting, raw);

        if (value == null) {
            context.getSource().sendError(SettingsCommand.badValue(setting, raw));
            return 0;
        }

        SettingsCommand.apply(setting, value);
        commit(holder);

        context.getSource().sendFeedback(SettingsCommand.changed(modId, setting, value));
        return 1;
    }

    private static void commit(@NotNull ISettingsHolder holder) {
        holder.onSettingsChanged();
        SettingsAudit.localChanged(holder);
        SettingsManager.getSettingsManager().markDirty(holder);
    }


    private static @Nullable ISettingsHolder clientHolder(@NotNull String modId) {
        if (!modId.endsWith(CLIENT_SUFFIX)) return null;

        return SettingsManager.getSettingsManager().getRegisteredMods().get(modId);
    }

    private static @Nullable List<Setting> clientSettingsOf(@NotNull String modId) {
        ISettingsHolder holder = clientHolder(modId);

        return holder == null ? null : holder.getSettings();
    }

    private static @NotNull List<String> clientMods() {
        List<String> mods = new ArrayList<>();

        for (Map.Entry<String, ISettingsHolder> entry : SettingsManager.getSettingsManager().getRegisteredMods().entrySet()) {
            if (!entry.getKey().endsWith(CLIENT_SUFFIX)) continue;

            if (SettingsCommand.hasAnyId(entry.getValue().getSettings())) mods.add(entry.getKey());
        }

        return mods;
    }

    private TRCClientCommand() {}
}
