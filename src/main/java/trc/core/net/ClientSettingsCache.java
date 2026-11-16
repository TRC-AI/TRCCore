package trc.core.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

import trc.core.settings.BoolSetting;
import trc.core.settings.ChoiceSetting;
import trc.core.settings.IntSetting;
import trc.core.settings.Setting;
import trc.core.settings.StringSetting;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

public final class ClientSettingsCache {
    public interface Listener {
        void onSettingsSynced(boolean structureChanged);
    }

    private static List<SettingsSnapshot.Section> sections = new ArrayList<>();
    private static @Nullable Listener listener;


    public static @NotNull List<SettingsSnapshot.Section> sections() { return sections; }

    public static boolean serverHasCore() {
        try { return ClientPlayNetworking.canSend(SettingsEditPayload.TYPE); }
        catch (IllegalStateException e) { return false; }
    }

    public static void setListener(@Nullable Listener screen) { listener = screen; }


    public static void accept(@NotNull SettingsSyncPayload payload) {
        List<SettingsSnapshot.Section> incoming = payload.allowed()
                ? SettingsSnapshot.decode(payload.json())
                : new ArrayList<>();

        boolean structureChanged = !structureKey(incoming).equals(structureKey(sections));

        if (structureChanged) sections = incoming;
        else copyValues(incoming, sections);

        if (listener != null) listener.onSettingsSynced(structureChanged);
    }

    public static void clear() {
        boolean wasEmpty = sections.isEmpty();
        sections = new ArrayList<>();

        if (listener != null && !wasEmpty) listener.onSettingsSynced(true);
    }


    public static void edit(String modId, @NotNull Setting setting) { send(modId, setting, false); }

    public static void preview(String modId, @NotNull Setting setting) {
        Minecraft client = Minecraft.getInstance();

        if (!client.hasSingleplayerServer()) return;

        send(modId, setting, true);
    }

    public static void requestRefresh() {
        if (!serverHasCore()) return;

        ClientPlayNetworking.send(SettingsRefreshPayload.INSTANCE);
    }

    private static void send(String modId, @NotNull Setting setting, boolean preview) {
        String value = switch (setting) {
            case BoolSetting bool -> String.valueOf(bool.value);
            case IntSetting number -> String.valueOf(number.value);
            case ChoiceSetting choice -> String.valueOf(choice.index);
            case StringSetting text -> text.value;
            default -> null;
        };

        if (value == null || !serverHasCore()) return;

        ClientPlayNetworking.send(new SettingsEditPayload(modId, setting.id, value, preview));
    }


    private static @NotNull String structureKey(@NotNull List<SettingsSnapshot.Section> snapshot) {
        StringBuilder key = new StringBuilder();

        for (SettingsSnapshot.Section section : snapshot) {
            key.append(section.modId).append('\n').append(section.displayName).append('\n');

            if (section.settings == null) continue;

            for (Setting setting : section.settings) {
                key.append(setting.id).append('\n')
                        .append(setting.name).append('\n')
                        .append(setting.getClass().getSimpleName());

                switch (setting) {
                    case IntSetting number -> key.append('\n')
                            .append(number.minValue).append(',')
                            .append(number.maxValue).append(',')
                            .append(number.milliStep).append(',')
                            .append(number.isInputField);
                    case ChoiceSetting choice -> key.append('\n')
                            .append(choice.values == null ? "" : String.join(",", choice.values));
                    case StringSetting text -> key.append('\n').append(text.maxLength)
                            .append(',').append(text.sensitive);
                    default -> {}
                }

                key.append('\n');
            }
        }

        return key.toString();
    }

    private static void copyValues(@NotNull List<SettingsSnapshot.Section> from, @NotNull List<SettingsSnapshot.Section> into) {
        for (int s = 0; s < from.size() && s < into.size(); s++) {
            into.get(s).hiddenChanged = from.get(s).hiddenChanged;

            List<Setting> source = from.get(s).settings;
            List<Setting> target = into.get(s).settings;

            if (source == null || target == null) continue;

            for (int i = 0; i < source.size() && i < target.size(); i++) {
                Setting incoming = source.get(i);

                switch (target.get(i)) {
                    case BoolSetting bool when incoming instanceof BoolSetting value -> bool.value = value.value;
                    case IntSetting number when incoming instanceof IntSetting value -> number.value = value.value;
                    case ChoiceSetting choice when incoming instanceof ChoiceSetting value -> choice.index = value.index;
                    case StringSetting text when incoming instanceof StringSetting value -> text.value = value.value;
                    default -> {}
                }
            }
        }
    }

    private ClientSettingsCache() {}
}