package trc.core.settings;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import trc.core.TRCLog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

public final class SettingsAudit {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final String HIDDEN = "(hidden)";

    public static @NotNull String authorityOf(@Nullable MinecraftServer server, @Nullable ServerPlayer player) {
        if (player == null) return "console";
        if (server == null) return "unknown";

        if (server.isSingleplayerOwner(player.nameAndId())) return "host";

        return switch (SettingsAccessList.of(player.nameAndId())) {
            case WHITELISTED -> "whitelist";
            case BLACKLISTED -> "blacklist";
            case DEFAULT -> server.isDedicatedServer()
                    ? "op:" + server.getProfilePermissions(player.nameAndId()).level().id()
                    : "lan";
        };
    }

    public static @NotNull String actorOf(@Nullable ServerPlayer player) {
        return player == null
                ? "server console"
                : player.getName().getString() + " (" + player.getUUID() + ")";
    }

    public static void settingChanged(@Nullable MinecraftServer server, @Nullable ServerPlayer player,
                                      String modId, @NotNull Setting setting, String from, String to) {
        TRCLog.CORE.info("[audit] {} | {} [{}] | {}/{} ({}) | '{}' -> '{}'",
                now(), clean(actorOf(player)), authorityOf(server, player),
                clean(modId), setting.id == null ? "-" : clean(setting.id), clean(setting.name),
                clean(from), clean(to));
    }

    private static final Map<Setting, String> committed = Collections.synchronizedMap(new IdentityHashMap<>());

    public static void remember(@NotNull ISettingsHolder holder) {
        for (Setting setting : holder.getSettings()) {
            committed.put(setting, valueOf(setting));
        }
    }

    public static void localChanged(@NotNull ISettingsHolder holder) {
        for (Setting setting : holder.getSettings()) {
            String after = valueOf(setting);
            String before = committed.put(setting, after);

            if (before == null || before.equals(after)) continue;

            TRCLog.CLIENT.info("[audit] {} | this client [client] | {}/{} ({}) | '{}' -> '{}'",
                    now(), clean(holder.getModId()), clean(setting.id), clean(setting.name),
                    clean(before), clean(after));
        }
    }

    public static boolean isSensitive(@NotNull Setting setting) {
        return setting instanceof StringSetting text && text.sensitive;
    }

    public static void action(@Nullable MinecraftServer server, @Nullable ServerPlayer player,
                              String what, String detail) {
        TRCLog.CORE.info("[audit] {} | {} [{}] | {} | {}",
                now(), clean(actorOf(player)), authorityOf(server, player), clean(what), clean(detail));
    }

    public static @NotNull String clean(@Nullable String text) {
        if (text == null) return "null";

        StringBuilder out = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (isControl(c)) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }

        return out.toString();
    }

    private static boolean isControl(char c) {
        return switch (Character.getType(c)) {
            case Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
            default -> false;
        };
    }

    public static @NotNull String valueOf(@NotNull Setting setting) {
        return switch (setting) {
            case BoolSetting bool -> String.valueOf(bool.value);
            case IntSetting number -> String.valueOf(number.value);
            case ChoiceSetting choice -> choice.values == null
                    || choice.index < 0
                    || choice.index >= choice.values.size()
                    ? "" : choice.values.get(choice.index);
            case StringSetting text -> text.value;
            case KeySetting key -> key.value;
            default -> "";
        };
    }

    private static @NotNull String now() { return LocalDateTime.now().format(TIMESTAMP); }

    private SettingsAudit() {}
}