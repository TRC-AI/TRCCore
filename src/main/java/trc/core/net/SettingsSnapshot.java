package trc.core.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import trc.core.settings.Setting;
import trc.core.settings.SettingsManager;
import trc.core.settings.StringSetting;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

public final class SettingsSnapshot {
    private static final Type LIST_TYPE = new TypeToken<List<Section>>() {}.getType();

    public static class Section {
        public String modId;
        public String displayName;
        public List<Setting> settings;

        public List<String> hiddenChanged = new ArrayList<>();

        public Section(String modId, String displayName, List<Setting> settings) {
            this.modId = modId;
            this.displayName = displayName;
            this.settings = settings;
        }

        public static @NotNull Section forClient(String modId, String displayName, @NotNull List<Setting> settings) {
            List<Setting> shown = new ArrayList<>(settings.size());
            List<String> changed = new ArrayList<>();

            for (Setting setting : settings) {
                if (setting instanceof StringSetting text && text.sensitive) {
                    StringSetting masked = new StringSetting(text.id, text.translationComponent, text.defaultValue, text.maxLength, text.name).sensitive();
                    masked.value = "";
                    shown.add(masked);

                    if (!text.isDefault()) changed.add(text.id);
                } else {
                    shown.add(setting);
                }
            }

            Section section = new Section(modId, displayName, shown);
            section.hiddenChanged = changed;

            return section;
        }

        public boolean hasHiddenChange(String id) {
            return hiddenChanged != null && hiddenChanged.contains(id);
        }
    }

    public static @NotNull List<Setting> renamed(@NotNull List<Setting> settings, @NotNull Function<Setting, String> nameOf) {
        List<Setting> copies = new ArrayList<>(settings.size());

        for (Setting setting : settings) {
            Setting copy = SettingsManager.gson().fromJson(SettingsManager.gson().toJsonTree(setting, Setting.class), Setting.class);
            copy.name = nameOf.apply(setting);
            copies.add(copy);
        }

        return copies;
    }

    public static @NotNull String encode(List<Section> sections) {
        return SettingsManager.gson().toJson(sections, LIST_TYPE);
    }

    public static List<Section> decode(String json) {
        List<Section> sections = new ArrayList<>();
        JsonElement root;

        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return sections;
        }

        if (root == null || !root.isJsonArray()) return sections;

        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;

            JsonObject object = element.getAsJsonObject();
            String modId = stringOf(object, "modId");

            if (modId == null) continue;

            String displayName = stringOf(object, "displayName");
            List<Setting> settings = new ArrayList<>();
            JsonElement entries = object.get("settings");

            if (entries != null && entries.isJsonArray()) {
                for (JsonElement entry : entries.getAsJsonArray()) {
                    try {
                        Setting setting = SettingsManager.gson().fromJson(entry, Setting.class);

                        if (setting != null) settings.add(setting);
                    } catch (RuntimeException _) {
                    }
                }
            }

            if (settings.isEmpty()) continue;

            Section section = new Section(modId, displayName == null ? modId : displayName, settings);
            JsonElement changed = object.get("hiddenChanged");

            if (changed != null && changed.isJsonArray()) {
                for (JsonElement id : changed.getAsJsonArray()) {
                    if (id.isJsonPrimitive()) section.hiddenChanged.add(id.getAsString());
                }
            }

            sections.add(section);
        }

        return sections;
    }

    private static @Nullable String stringOf(@NotNull JsonObject object, @NotNull String key) {
        JsonElement value = object.get(key);

        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private SettingsSnapshot() {}
}
