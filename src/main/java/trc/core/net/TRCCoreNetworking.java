package trc.core.net;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import trc.core.CoreLanguage;
import trc.core.TRCLog;
import trc.core.Texts;
import trc.core.TRCCoreSettings;
import trc.core.command.CommandNotice;
import trc.core.command.SettingsCommand;
import trc.core.settings.BoolSetting;
import trc.core.settings.ChoiceSetting;
import trc.core.settings.ISettingsHolder;
import trc.core.settings.SettingsAccessList;
import trc.core.settings.SettingsAudit;
import trc.core.settings.IntSetting;
import trc.core.settings.Setting;
import trc.core.settings.SettingsManager;
import trc.core.settings.StringSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

public final class TRCCoreNetworking {
    private static final String CLIENT_SUFFIX = "_client";

    private static final int PERMISSION_CHECK_INTERVAL = 20;

    private record Rights(boolean edit, boolean manage, String language) {}

    private static final Map<UUID, Rights> lastRights = new HashMap<>();

    private static int tickCounter;

    private static String lastStructure = "";

    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().registerLarge(
                SettingsSyncPayload.TYPE, SettingsSyncPayload.CODEC, SettingsSyncPayload.MAX_JSON_LENGTH);
        PayloadTypeRegistry.serverboundPlay().register(
                SettingsEditPayload.TYPE, SettingsEditPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                SettingsRefreshPayload.TYPE, SettingsRefreshPayload.CODEC);
    }

    public static void registerServerHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(SettingsEditPayload.TYPE, (payload, context) -> {
            if (payload == SettingsEditPayload.INVALID) return;

            RequestLimiter.submit(context.server(), context.player(),
                    "edit\n" + payload.modId() + "\n" + payload.settingId(),
                    () -> applyEdit(context.server(), context.player(), payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(SettingsRefreshPayload.TYPE,
                (_, context) -> RequestLimiter.submit(context.server(), context.player(),
                        "refresh", () -> refresh(context.server(), context.player())));

        ServerPlayConnectionEvents.JOIN.register((listener, _, server) -> {
            SettingsAccessList.reconcile(listener.getPlayer().nameAndId());

            sendTo(server, listener.getPlayer());

            refreshRights(server);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((listener, _) -> {
            lastRights.remove(listener.getPlayer().getUUID());
            RequestLimiter.forget(listener.getPlayer().getUUID());
        });

        ServerTickEvents.END_SERVER_TICK.register(RequestLimiter::drain);
        ServerTickEvents.END_SERVER_TICK.register(TRCCoreNetworking::checkForChanges);
    }

    private static void refresh(@NotNull MinecraftServer server, @NotNull ServerPlayer player) {
        SettingsAccessList.reconcile(player.nameAndId());

        sendTo(server, player);
    }


    public static boolean mayEdit(@NotNull MinecraftServer server, @Nullable ServerPlayer player) {
        if (player == null) return false;
        if (server.isSingleplayerOwner(player.nameAndId())) return true;

        if (SettingsAccessList.isPermissionEditor(player.nameAndId())) return true;

        switch (SettingsAccessList.of(player.nameAndId())) {
            case BLACKLISTED -> { return false; }
            case WHITELISTED -> { return true; }
            case DEFAULT -> {}
        }

        if (!server.isDedicatedServer()) return TRCCoreSettings.ALLOW_LAN_EDITS.value;

        return permissionLevel(server, player) >= TRCCoreSettings.REQUIRED_OP_LEVEL.value;
    }

    public static boolean maySetPermissionSetting(@NotNull MinecraftServer server, @Nullable ServerPlayer player) {
        if (player == null) return false;
        if (server.isSingleplayerOwner(player.nameAndId())) return true;

        return SettingsAccessList.isPermissionEditor(player.nameAndId());
    }

    private static int permissionLevel(@NotNull MinecraftServer server, @NotNull ServerPlayer player) {
        return server.getProfilePermissions(player.nameAndId()).level().id();
    }

    private static void checkForChanges(@NotNull MinecraftServer server) {
        if (++tickCounter < PERMISSION_CHECK_INTERVAL) return;
        tickCounter = 0;

        String structure = structureSignature(server);

        if (!structure.equals(lastStructure)) {
            lastStructure = structure;

            broadcast(server);
            return;
        }

        refreshRights(server);
    }

    public static void refreshPermissions(@NotNull MinecraftServer server) {
        refreshRights(server);
    }

    private static void refreshRights(@NotNull MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Rights now = new Rights(mayEdit(server, player), maySetPermissionSetting(server, player),
                    player.clientInformation().language());

            if (!now.equals(lastRights.get(player.getUUID()))) {
                sendTo(server, player);
            }
        }
    }

    private static @NotNull String structureSignature(@NotNull MinecraftServer server) {
        StringBuilder signature = new StringBuilder()
                .append(server.isDedicatedServer()).append('|')
                .append(server.isPublished()).append('|');

        for (Map.Entry<String, ISettingsHolder> entry : SettingsManager.getSettingsManager().getRegisteredMods().entrySet()) {
            if (entry.getKey().endsWith(CLIENT_SUFFIX)) continue;

            List<Setting> settings = visibleSettings(server, entry.getValue());
            if (settings.isEmpty()) continue;

            signature.append(entry.getKey()).append(',').append(settings.size()).append(';');
        }

        return signature.toString();
    }


    private static @NotNull List<SettingsSnapshot.Section> buildSnapshot(@NotNull MinecraftServer server, ServerPlayer player) {
        List<SettingsSnapshot.Section> sections = new ArrayList<>();
        SettingsSnapshot.Section core = null;

        if (!mayEdit(server, player)) return sections;

        for (Map.Entry<String, ISettingsHolder> entry : SettingsManager.getSettingsManager().getRegisteredMods().entrySet()) {
            if (entry.getKey().endsWith(CLIENT_SUFFIX)) continue;

            List<Setting> settings = visibleSettings(server, entry.getValue());
            if (settings.isEmpty()) continue;

            String name = displayName(entry.getKey());

            if (entry.getValue() == TRCCoreSettings.INSTANCE) {
                String language = player.clientInformation().language();

                name = CoreLanguage.get(language, "trc-core.section.name", name);
                settings = SettingsSnapshot.renamed(settings,
                        setting -> CoreLanguage.get(language, "trc-core.setting." + setting.id, setting.name));
            }

            SettingsSnapshot.Section section = SettingsSnapshot.Section.forClient(entry.getKey(), name, settings);

            if (entry.getValue() == TRCCoreSettings.INSTANCE) {
                if (maySetPermissionSetting(server, player)) core = section;
            } else {
                sections.add(section);
            }
        }

        if (sections.isEmpty()) return sections;
        if (core != null) sections.add(core);

        return sections;
    }

    private static boolean hasAddonServerSettings(@NotNull MinecraftServer server) {
        for (Map.Entry<String, ISettingsHolder> entry : SettingsManager.getSettingsManager().getRegisteredMods().entrySet()) {
            if (entry.getKey().endsWith(CLIENT_SUFFIX)) continue;
            if (entry.getValue() == TRCCoreSettings.INSTANCE) continue;

            if (!visibleSettings(server, entry.getValue()).isEmpty()) return true;
        }

        return false;
    }

    public static @NotNull List<Setting> visibleSettings(@NotNull MinecraftServer server, @NotNull ISettingsHolder holder) {
        if (holder != TRCCoreSettings.INSTANCE) return holder.getSettings();
        if (server.isDedicatedServer()) return List.of(TRCCoreSettings.REQUIRED_OP_LEVEL);

        return server.isPublished() ? List.of(TRCCoreSettings.ALLOW_LAN_EDITS) : List.of();
    }

    private static @NotNull String displayName(String modId) {
        return SettingsCommand.displayNameOf(modId);
    }

    public static void sendTo(@NotNull MinecraftServer server, @Nullable ServerPlayer player) {
        if (player == null) return;

        boolean allowed = mayEdit(server, player);
        boolean manage = maySetPermissionSetting(server, player);
        Rights now = new Rights(allowed, manage, player.clientInformation().language());
        Rights previous = lastRights.put(player.getUUID(), now);

        if (previous == null || previous.edit() != allowed || previous.manage() != manage) {
            server.getCommands().sendCommands(player);
        }

        if (!ServerPlayNetworking.canSend(player, SettingsSyncPayload.TYPE)) return;

        ServerPlayNetworking.send(player, new SettingsSyncPayload(allowed,
                manage,
                SettingsSnapshot.encode(buildSnapshot(server, player))));
    }

    public static void broadcast(@NotNull MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(server, player);
        }
    }

    public static @NotNull Map<String, ISettingsHolder> serverHolders() {
        Map<String, ISettingsHolder> holders = new LinkedHashMap<>();

        for (Map.Entry<String, ISettingsHolder> entry : SettingsManager.getSettingsManager().getRegisteredMods().entrySet()) {
            if (entry.getKey().endsWith(CLIENT_SUFFIX)) continue;

            holders.put(entry.getKey(), entry.getValue());
        }

        return holders;
    }

    public static void commitServerSide(@NotNull MinecraftServer server, @NotNull ISettingsHolder holder) {
        holder.onSettingsChanged();
        SettingsManager.getSettingsManager().markDirty(holder);
        broadcast(server);
    }


    private static void applyEdit(@NotNull MinecraftServer server, @NotNull ServerPlayer player, @NotNull SettingsEditPayload payload) {
        String who = SettingsAudit.clean(player.getName().getString());
        String modId = SettingsAudit.clean(payload.modId());
        String settingId = SettingsAudit.clean(payload.settingId());

        if (!mayEdit(server, player)) {
            TRCLog.CORE.warn("Rejected settings edit from {} (no permission): {}/{}",
                    who, modId, settingId);
            sendTo(server, player);
            return;
        }

        ISettingsHolder holder = SettingsManager.getSettingsManager().getRegisteredMods().get(payload.modId());

        if (holder == null || payload.modId().endsWith(CLIENT_SUFFIX)) {
            TRCLog.CORE.warn("Rejected settings edit from {}: unknown server holder {}",
                    who, modId);
            return;
        }

        if (holder == TRCCoreSettings.INSTANCE) {
            refreshPermissions(server);

            if (!hasAddonServerSettings(server)) {
                TRCLog.CORE.warn("Rejected settings edit from {}: no server settings to gate", who);
                return;
            }

            if (!maySetPermissionSetting(server, player)) {
                TRCLog.CORE.warn("Rejected settings edit from {}: not a permission editor", who);
                return;
            }
        }

        Setting target = SettingsCommand.byId(visibleSettings(server, holder), payload.settingId());

        if (target == null) {
            TRCLog.CORE.warn("Rejected settings edit from {}: unknown setting {}/{}",
                    who, modId, settingId);
            return;
        }

        String before = SettingsAudit.valueOf(target);

        if (!applyValue(target, payload.value())) {
            TRCLog.CORE.warn("Rejected settings edit from {}: bad value '{}' for {}/{}",
                    who, SettingsAudit.isSensitive(target) ? SettingsAudit.HIDDEN : SettingsAudit.clean(payload.value()),
                    modId, settingId);
            return;
        }

        holder.onSettingsChanged();

        if (payload.preview() && server.isSingleplayerOwner(player.nameAndId())) return;

        SettingsAudit.settingChanged(server, player, payload.modId(), target,
                before, SettingsAudit.valueOf(target));

        CommandNotice.toOthers(server, player, CommandNotice.Audience.SETTINGS_EDITORS, null,
                Texts.chat("trc-core.notice.ui.set", "%s set %s %s to %s (%s).",
                        CommandNotice.actor(player), payload.modId(), target.id,
                        SettingsCommand.shownValue(payload.modId(), target), target.name));

        SettingsManager.getSettingsManager().markDirty(holder);

        broadcast(server);
    }

    private static boolean applyValue(@NotNull Setting setting, String raw) {
        switch (setting) {
            case BoolSetting bool -> {
                if (!"true".equals(raw) && !"false".equals(raw)) return false;
                bool.value = Boolean.parseBoolean(raw);
                return true;
            }
            case IntSetting number -> {
                try {
                    number.value = Math.clamp(Integer.parseInt(raw.trim()), number.minValue, number.maxValue);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            case ChoiceSetting choice -> {
                if (choice.values.isEmpty()) return false;
                try {
                    choice.index = Math.floorMod(Integer.parseInt(raw.trim()), choice.values.size());
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            case StringSetting text -> {
                text.value = raw.length() > text.maxLength ? raw.substring(0, text.maxLength) : raw;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private TRCCoreNetworking() {}
}
