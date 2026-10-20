package trc.core.settings;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.network.chat.Component;
import trc.core.TRCLog;

import com.google.gson.*;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

public class SettingsManager {

    private static final int FLUSH_INTERVAL = 20;
    private final Map<String, ISettingsHolder> registeredMods = new HashMap<>();
    private final Map<String, List<JsonElement>> unreadable = new HashMap<>();
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private final Map<String, String> lastWritten = new ConcurrentHashMap<>();
    private final Set<Side> prepared = ConcurrentHashMap.newKeySet();

    private int commonTicks;
    private int clientTicks;

    private final Path configFolder;
    private static SettingsManager settingsManager;

    private SettingsManager() {
        this.configFolder = FabricLoader.getInstance().getGameDir().resolve("config/trc-settings");

        try {
            Files.createDirectories(configFolder);
        } catch (IOException e) {
            TRCLog.CORE.error("Could not create the settings folder {}. Settings will not be saved.",
                    e, configFolder);
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Setting.class, new SettingAdapter())
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    return Component.class.isAssignableFrom(f.getDeclaredClass());
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) { return false; }
            })
            .setPrettyPrinting()
            .create();

    private static final String CLIENT_SUFFIX = "_client";

    public enum Side {
        COMMON,
        CLIENT;

        public boolean owns(@NotNull String modId) {
            return (this == CLIENT) == modId.endsWith(CLIENT_SUFFIX);
        }

        static @NotNull Side of(@NotNull String modId) {
            return CLIENT.owns(modId) ? CLIENT : COMMON;
        }
    }

    public void register(ISettingsHolder holder) {
        rejectServerSideKeys(holder);

        ISettingsHolder registered = withoutDuplicateIds(holder);

        registeredMods.put(holder.getModId(), registered);

        if (prepared.contains(Side.of(holder.getModId()))) {
            load(registered);
            SettingsAudit.remember(registered);

            dirty.remove(holder.getModId());
            save(registered);
        }
    }

    private static void rejectServerSideKeys(@NotNull ISettingsHolder holder) {
        if (holder.getModId().endsWith(CLIENT_SUFFIX)) return;

        for (Setting setting : holder.getSettings()) {
            if (setting instanceof KeySetting) {
                throw new IllegalArgumentException("Setting '" + setting.id + "' of '" + holder.getModId()
                        + "' is a KeySetting, which only exists client-side. Move it to the mod's"
                        + " TRCAddonClient holder.");
            }
        }
    }

    private static @NotNull ISettingsHolder withoutDuplicateIds(@NotNull ISettingsHolder holder) {
        List<Setting> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean dropped = false;

        for (Setting setting : holder.getSettings()) {
            if (seen.add(setting.id)) {
                kept.add(setting);
                continue;
            }

            dropped = true;
            TRCLog.CORE.warn("Setting '{}' of '{}' uses the id '{}', which an earlier setting of the same mod"
                            + " already has. It is ignored: not loaded, saved, shown or reachable from /trc.",
                    setting.name, holder.getModId(), setting.id);
        }

        return dropped ? new DeduplicatedHolder(holder, List.copyOf(kept)) : holder;
    }

    private record DeduplicatedHolder(ISettingsHolder holder, List<Setting> settings) implements ISettingsHolder {
        @Override
        public String getModId() { return holder.getModId(); }

        @Override
        public List<Setting> getSettings() { return settings; }

        @Override
        public void onSettingsChanged() { holder.onSettingsChanged(); }
    }

    public boolean unregister(String modId) {
        unreadable.remove(modId);
        dirty.remove(modId);
        lastWritten.remove(modId);
        return registeredMods.remove(modId) != null;
    }


    public void markDirty(@NotNull ISettingsHolder holder) {
        dirty.add(holder.getModId());
    }

    public void tick(@NotNull Side side) {
        int ticks = side == Side.CLIENT ? ++clientTicks : ++commonTicks;

        if (ticks % FLUSH_INTERVAL == 0) flush(side);
    }

    public void flush(@NotNull Side side) {
        for (String modId : List.copyOf(dirty)) {
            if (!side.owns(modId) || !dirty.remove(modId)) continue;

            ISettingsHolder holder = registeredMods.get(modId);

            if (holder != null) save(holder);
        }
    }

    public void save(@NotNull ISettingsHolder holder) {
        JsonArray entries = new JsonArray();

        for (Setting setting : holder.getSettings()) {
            entries.add(GSON.toJsonTree(setting, Setting.class));
        }

        for (JsonElement kept : unreadable.getOrDefault(holder.getModId(), List.of())) {
            entries.add(kept);
        }

        String json = GSON.toJson(entries);

        if (json.equals(lastWritten.get(holder.getModId()))) return;

        AtomicWrite.write(configFolder.resolve(holder.getModId() + ".json"),
                writer -> writer.write(json),
                "settings of " + holder.getModId());

        lastWritten.put(holder.getModId(), json);
    }


    public void prepareForStart(@NotNull Side side) {
        for (ISettingsHolder holder : holdersOf(side)) {
            load(holder);
            SettingsAudit.remember(holder);

            dirty.remove(holder.getModId());
            save(holder);
        }

        prepared.add(side);
    }

    private @NotNull List<ISettingsHolder> holdersOf(@NotNull Side side) {
        List<ISettingsHolder> holders = new ArrayList<>();

        for (Map.Entry<String, ISettingsHolder> entry : registeredMods.entrySet()) {
            if (side.owns(entry.getKey())) holders.add(entry.getValue());
        }

        return holders;
    }

    private boolean load(@NotNull ISettingsHolder holder) {
        String modId = holder.getModId();
        File file = new File(configFolder.toFile(), modId + ".json");

        if (!file.exists()) {
            unreadable.remove(modId);
            return false;
        }

        List<Setting> loadedSettings = new ArrayList<>();
        List<JsonElement> kept = new ArrayList<>();

        try (Reader reader = new FileReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);

            if (root == null || root.isJsonNull()) throw new JsonParseException("the file is empty");
            if (!root.isJsonArray()) throw new JsonParseException("expected a list of settings");

            for (JsonElement entry : root.getAsJsonArray()) {
                try {
                    Setting setting = GSON.fromJson(entry, Setting.class);

                    if (setting != null) loadedSettings.add(setting);
                    else kept.add(entry);
                } catch (RuntimeException e) {
                    kept.add(entry);
                }
            }
        } catch (Exception e) {
            TRCLog.CORE.warn("The settings file of {} is empty or cannot be read ({}). Its settings keep"
                    + " their defaults and the file is written anew.", modId, e.getMessage());

            unreadable.remove(modId);
            boolean changed = resetAll(holder);

            lastWritten.remove(modId);
            dirty.remove(modId);
            save(holder);

            return changed;
        }

        if (kept.isEmpty()) {
            unreadable.remove(modId);
        } else {
            unreadable.put(modId, kept);
            TRCLog.CORE.info("{} entr{} in the settings of {} cannot be read by this TRC-Core version."
                            + " They are ignored and kept in the file unchanged.",
                    kept.size(), kept.size() == 1 ? "y" : "ies", modId);
        }

        return syncSettings(holder.getSettings(), loadedSettings);
    }

    private static boolean resetAll(@NotNull ISettingsHolder holder) {
        boolean changed = false;

        for (Setting setting : holder.getSettings()) {
            if (setting.isDefault()) continue;

            setting.reset();
            changed = true;
        }

        return changed;
    }

    private boolean syncSettings(List<Setting> original, List<Setting> loaded) {
        if (loaded == null) return false;

        boolean changed = false;

        for (Setting mine : original) {
            Setting stored = match(loaded, mine);

            if (stored != null && copyValue(mine, stored)) changed = true;
        }

        return changed;
    }

    private static @Nullable Setting match(@NotNull List<Setting> loaded, @NotNull Setting mine) {
        for (Setting stored : loaded) {
            if (mine.id != null && mine.id.equals(stored.id)) return stored;
        }

        for (Setting stored : loaded) {
            if (stored.id == null && mine.name.equals(stored.name)) return stored;
        }

        return null;
    }

    private static boolean copyValue(@NotNull Setting mine, @NotNull Setting stored) {
        switch (mine) {
            case BoolSetting to when stored instanceof BoolSetting from && to.value != from.value -> {
                to.value = from.value;
                return true;
            }
            case IntSetting to when stored instanceof IntSetting from && to.value != from.value -> {
                to.value = from.value;
                return true;
            }
            case ChoiceSetting to when stored instanceof ChoiceSetting from && to.index != from.index -> {
                to.index = from.index;
                return true;
            }
            case StringSetting to when stored instanceof StringSetting from && !to.value.equals(from.value) -> {
                to.value = from.value;
                return true;
            }
            case KeySetting to when stored instanceof KeySetting from && !to.value.equals(from.value) -> {
                to.value = from.value;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public Map<String, ISettingsHolder> getRegisteredMods() { return registeredMods; }

    public static SettingsManager getSettingsManager() {
        if (settingsManager == null) settingsManager = new SettingsManager();
        return settingsManager;
    }

    private static class SettingAdapter implements JsonSerializer<Setting>, JsonDeserializer<Setting> {
        @Override
        public @NotNull JsonElement serialize(Setting src, Type typeOfSrc, @NotNull JsonSerializationContext context) {
            JsonObject result = context.serialize(src, src.getClass()).getAsJsonObject();
            result.addProperty("type", src.getClass().getSimpleName());
            return result;
        }

        @Override
        public Setting deserialize(@NotNull JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            JsonElement typeElement = jsonObject.get("type");

            if (typeElement == null) {
                throw new JsonParseException("Missing type property in setting JSON");
            }

            String type = typeElement.getAsString();

            return switch (type) {
                case "BoolSetting" -> context.deserialize(jsonObject, BoolSetting.class);
                case "IntSetting" -> context.deserialize(jsonObject, IntSetting.class);
                case "ChoiceSetting" -> context.deserialize(jsonObject, ChoiceSetting.class);
                case "StringSetting" -> context.deserialize(jsonObject, StringSetting.class);
                case "KeySetting" -> context.deserialize(jsonObject, KeySetting.class);
                default -> throw new JsonParseException("Unknown setting type: " + type);
            };
        }
    }
}
