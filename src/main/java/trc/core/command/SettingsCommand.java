package trc.core.command;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import trc.core.TRCCoreSettings;
import trc.core.settings.BoolSetting;
import trc.core.settings.ChoiceSetting;
import trc.core.settings.IntSetting;
import trc.core.settings.KeySetting;
import trc.core.settings.Setting;
import trc.core.settings.SettingsAudit;
import trc.core.settings.StringSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import static trc.core.Texts.chat;

public final class SettingsCommand {

    public static @Nullable Setting byId(@Nullable List<Setting> settings, String id) {
        if (settings == null) return null;

        String wanted = id.trim().toLowerCase(Locale.ROOT);
        for (Setting setting : settings) if (setting.id != null && setting.id.equals(wanted)) return setting;

        return null;
    }

    public static @NotNull List<String> idsOf(@Nullable List<Setting> settings) {
        List<String> ids = new ArrayList<>();
        if (settings == null) return ids;

        for (Setting setting : settings) if (setting.id != null) ids.add(setting.id);

        return ids;
    }

    public static boolean hasAnyId(@Nullable List<Setting> settings) {
        return !idsOf(settings).isEmpty();
    }


    public static @NotNull List<String> valueSuggestions(@NotNull Setting setting) {
        return switch (setting) {
            case BoolSetting ignored -> List.of("true", "false");
            case ChoiceSetting choice -> choice.values == null ? List.of() : List.copyOf(choice.values);
            case KeySetting key -> List.of(key.value, KeySetting.UNBOUND);
            default -> List.of();
        };
    }

    public static @NotNull MutableComponent shownValue(String modId, @NotNull Setting setting) {
        if (SettingsAudit.isSensitive(setting)) return hidden();

        return withLabel(modId, setting, SettingsAudit.valueOf(setting));
    }

    private static @NotNull MutableComponent hidden() {
        return chat("trc-core.value.hidden", "(hidden)");
    }

    private static @NotNull MutableComponent withLabel(String modId, @NotNull Setting setting, @NotNull String value) {
        MutableComponent shown = Component.literal(value);
        Component label = TRCCoreSettings.valueLabel(modId, setting);

        return label == null ? shown : shown.append(" (").append(label).append(")");
    }

    public static @NotNull MutableComponent describeRange(@NotNull Setting setting) {
        return switch (setting) {
            case BoolSetting ignored -> chat("trc-core.command.range.bool", "true or false");
            case IntSetting number -> chat("trc-core.command.range.int", "a whole number between %s and %s",
                    number.minValue, number.maxValue);
            case ChoiceSetting choice -> choice.values == null || choice.values.isEmpty()
                    ? chat("trc-core.command.range.choice_empty", "nothing -- this setting has no values")
                    : chat("trc-core.command.range.choice", "one of: %s", String.join(", ", choice.values));
            case StringSetting text -> chat("trc-core.command.range.text", "any text, at most %s characters",
                    text.maxLength);
            case KeySetting ignored -> chat("trc-core.command.range.key", "a key name such as key.keyboard.k, or %s to unbind it",
                    KeySetting.UNBOUND);
            default -> Component.empty();
        };
    }

    public record ParsedValue(String wire, String display) {}

    public static @Nullable ParsedValue parse(@NotNull Setting setting, @NotNull String raw) {
        String trimmed = raw.trim();

        switch (setting) {
            case BoolSetting ignored -> {
                if (trimmed.equalsIgnoreCase("true")) return new ParsedValue("true", "true");
                if (trimmed.equalsIgnoreCase("false")) return new ParsedValue("false", "false");
                return null;
            }
            case IntSetting number -> {
                try {
                    int parsed = Integer.parseInt(trimmed);

                    if (parsed < number.minValue || parsed > number.maxValue) return null;

                    return new ParsedValue(String.valueOf(parsed), String.valueOf(parsed));
                } catch (NumberFormatException e) { return null; }
            }
            case ChoiceSetting choice -> {
                if (choice.values == null) return null;

                for (int i = 0; i < choice.values.size(); i++) {
                    if (choice.values.get(i).equalsIgnoreCase(trimmed)) {
                        return new ParsedValue(String.valueOf(i), choice.values.get(i));
                    }
                }

                return null;
            }
            case StringSetting text -> {
                if (raw.length() > text.maxLength) return null;

                return new ParsedValue(raw, raw);
            }
            case KeySetting ignored -> {
                if (!trimmed.startsWith("key.")) return null;

                return new ParsedValue(trimmed, trimmed);
            }
            default -> { return null; }
        }
    }

    public static void apply(@NotNull Setting setting, @NotNull ParsedValue value) {
        switch (setting) {
            case BoolSetting bool -> bool.value = Boolean.parseBoolean(value.wire());
            case IntSetting number -> number.value = Integer.parseInt(value.wire());
            case ChoiceSetting choice -> choice.index = Integer.parseInt(value.wire());
            case StringSetting text -> text.value = value.wire();
            case KeySetting key -> key.value = value.wire();
            default -> {}
        }
    }


    private static final String CLIENT_SUFFIX = "_client";

    public static @NotNull String displayNameOf(@NotNull String modId) {
        String owner = modId.endsWith(CLIENT_SUFFIX)
                ? modId.substring(0, modId.length() - CLIENT_SUFFIX.length())
                : modId;

        return FabricLoader.getInstance()
                .getModContainer(owner)
                .map(container -> container.getMetadata().getName())
                .orElse(owner);
    }

    public static @NotNull Component listHeading(@NotNull MutableComponent text) {
        return text.withStyle(ChatFormatting.YELLOW);
    }

    public static @NotNull Component listEntry(String modId, String displayName, int settings) {
        MutableComponent count = settings == 1
                ? chat("trc-core.command.list.entry.one", "%s (%s setting)", displayName, settings)
                : chat("trc-core.command.list.entry.many", "%s (%s settings)", displayName, settings);

        return Component.literal("  " + modId + "  ").append(count.withStyle(ChatFormatting.GRAY));
    }


    public static @NotNull Component reading(String modId, @NotNull Setting setting) {
        return Component.literal(modId + " " + setting.id + " = ")
                .append(shownValue(modId, setting).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" (" + setting.name + ")").withStyle(ChatFormatting.GRAY));
    }

    public static @NotNull Component changed(String modId, @NotNull Setting setting, @NotNull ParsedValue value) {
        MutableComponent shown = SettingsAudit.isSensitive(setting)
                ? hidden()
                : withLabel(modId, setting, value.display());

        return Component.literal(modId + " " + setting.id + " -> ")
                .append(shown.withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" (" + setting.name + ")").withStyle(ChatFormatting.GRAY));
    }

    public static @NotNull Component wasReset(String modId, @NotNull Setting setting) {
        return chat("trc-core.command.reset", "%s %s reset to %s (%s)",
                modId, setting.id,
                shownValue(modId, setting).withStyle(ChatFormatting.GREEN),
                Component.literal(setting.name).withStyle(ChatFormatting.GRAY));
    }

    public static @NotNull Component unknownMod(String modId) {
        return chat("trc-core.command.unknown_mod", "No settings you can reach under '%s'.", modId)
                .withStyle(ChatFormatting.RED);
    }

    public static @NotNull Component unknownSetting(String modId, String id) {
        return chat("trc-core.command.unknown_setting",
                "'%s' has no setting with the id '%s'. Settings without an id cannot be changed from here.",
                modId, id)
                .withStyle(ChatFormatting.RED);
    }

    public static @NotNull Component badValue(@NotNull Setting setting, String raw) {
        return chat("trc-core.command.bad_value", "'%s' is not valid for %s. Expected %s.",
                raw, setting.id, describeRange(setting))
                .withStyle(ChatFormatting.RED);
    }

    private SettingsCommand() {}
}