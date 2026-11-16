package trc.core.settings;

import net.fabricmc.loader.api.FabricLoader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;

import trc.core.TRCLog;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import org.jetbrains.annotations.Contract;

public final class SettingsAccessList {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Entry.class, new EntryAdapter())
            .setPrettyPrinting()
            .create();

    private static final String FILE_NAME = "trc-core-access.json";

    public static final class Entry {
        public @Nullable String uuid;
        public @Nullable String name;

        Entry(@Nullable String uuid, @Nullable String name) {
            this.uuid = uuid;
            this.name = name;
        }

        boolean isEmpty() { return (uuid == null || uuid.isBlank()) && (name == null || name.isBlank()); }

        boolean matches(@NotNull NameAndId player) {
            if (uuid != null && !uuid.isBlank()) return uuid.equalsIgnoreCase(player.id().toString());

            return name != null && name.equalsIgnoreCase(player.name());
        }

        @Contract(value = " -> new", pure = true)
        @NotNull Entry copy() { return new Entry(uuid, name); }
    }

    public enum Access {
        BLACKLISTED,
        WHITELISTED,
        DEFAULT
    }

    public enum Kind {
        WHITELIST,
        BLACKLIST,
        PERMISSION_EDITORS
    }

    private static class Lists {
        List<Entry> whitelist = new ArrayList<>();
        List<Entry> blacklist = new ArrayList<>();
        List<Entry> permissionEditors = new ArrayList<>();
    }

    private static final List<Entry> whitelist = new ArrayList<>();
    private static final List<Entry> blacklist = new ArrayList<>();
    private static final List<Entry> permissionEditors = new ArrayList<>();


    public static @NotNull Access of(@Nullable NameAndId player) {
        if (player == null) return Access.DEFAULT;

        if (find(blacklist, player) != null) return Access.BLACKLISTED;
        if (find(whitelist, player) != null) return Access.WHITELISTED;

        return Access.DEFAULT;
    }

    public static boolean isPermissionEditor(@Nullable NameAndId player) {
        if (player == null) return false;

        return find(permissionEditors, player) != null;
    }

    private static @Nullable Entry find(@NotNull List<Entry> list, @NotNull NameAndId player) {
        for (Entry entry : list) if (entry.matches(player)) return entry;

        return null;
    }


    public static void reconcile(@Nullable NameAndId player) {
        if (player == null) return;

        boolean changed = reconcile(whitelist, player)
                | reconcile(blacklist, player)
                | reconcile(permissionEditors, player);

        if (changed) save();
    }

    private static boolean reconcile(@NotNull List<Entry> list, @NotNull NameAndId player) {
        Entry entry = find(list, player);

        if (entry == null) return false;

        boolean changed = false;

        if (entry.uuid == null || entry.uuid.isBlank()) {
            entry.uuid = player.id().toString();
            changed = true;
        }

        if (!player.name().equals(entry.name)) {
            entry.name = player.name();
            changed = true;
        }

        if (changed) {
            TRCLog.CORE.info("Updated access entry for {} ({}).",
                    entry.name, entry.uuid == null ? "no id yet" : entry.uuid);
        }

        return changed;
    }


    public static void warnIfUnverified(@NotNull MinecraftServer server) {
        if (server.usesAuthentication()) return;

        int entries = whitelist.size() + blacklist.size() + permissionEditors.size();

        if (entries == 0) return;

        TRCLog.CORE.warn("This server does not verify players (online-mode=false), but TRC-Core's access lists"
                + " hold {} entr{}. Anybody can join under a listed name and get that player's rights, and a"
                + " blacklisted player can join under another name. Unless a proxy in front of the server"
                + " verifies players, do not rely on these lists.", entries, entries == 1 ? "y" : "ies");
    }


    public static @NotNull List<Entry> entries(@NotNull Kind kind) {
        List<Entry> copies = new ArrayList<>();
        for (Entry entry : listFor(kind)) copies.add(entry.copy());

        return copies;
    }

    public static boolean add(@NotNull Kind kind, @NotNull NameAndId player) {
        List<Entry> list = listFor(kind);

        if (find(list, player) != null) return false;

        list.add(new Entry(player.id().toString(), player.name()));
        save();
        return true;
    }

    public static boolean add(@NotNull Kind kind, @NotNull String entry) {
        if (entry.isBlank()) return false;

        Entry parsed = parse(entry);
        List<Entry> list = listFor(kind);

        for (Entry existing : list) if (sameAs(existing, parsed)) return false;

        list.add(parsed);
        save();
        return true;
    }

    public static boolean remove(@NotNull Kind kind, @NotNull String entry) {
        if (entry.isBlank()) return false;

        Entry parsed = parse(entry);
        List<Entry> list = listFor(kind);

        for (int i = 0; i < list.size(); i++) {
            if (sameAs(list.get(i), parsed)) {
                list.remove(i);
                save();
                return true;
            }
        }

        return false;
    }

    private static boolean sameAs(@NotNull Entry existing, @NotNull Entry other) {
        if (equalsIgnoreCase(existing.uuid, other.uuid)) return true;

        return equalsIgnoreCase(existing.name, other.name);
    }

    private static boolean equalsIgnoreCase(@Nullable String a, @Nullable String b) {
        return a != null && !a.isBlank() && a.equalsIgnoreCase(b);
    }

    private static @NotNull Entry parse(@NotNull String raw) {
        String trimmed = raw.trim();

        try { return new Entry(UUID.fromString(trimmed).toString(), null); }
        catch (IllegalArgumentException e) { return new Entry(null, trimmed); }
    }

    private static @NotNull List<Entry> listFor(@NotNull Kind kind) {
        return switch (kind) {
            case WHITELIST -> whitelist;
            case BLACKLIST -> blacklist;
            case PERMISSION_EDITORS -> permissionEditors;
        };
    }


    private static @NotNull Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("config/trc-settings");
    }

    public static void load() {

        whitelist.clear();
        blacklist.clear();
        permissionEditors.clear();

        File file = folder().resolve(FILE_NAME).toFile();

        if (!file.exists()) {
            write();
            return;
        }

        try (Reader reader = new FileReader(file)) {
            Lists loaded = GSON.fromJson(reader, Lists.class);

            if (loaded != null) {
                fill(whitelist, loaded.whitelist);
                fill(blacklist, loaded.blacklist);
                fill(permissionEditors, loaded.permissionEditors);
            }
        } catch (Exception e) {
            TRCLog.CORE.error("Could not read {}. Both lists are treated as empty, so the ordinary"
                    + " permission rules apply to everyone until it is fixed.", e, FILE_NAME);
            return;
        }

        write();

        TRCLog.CORE.debug("Access lists loaded: {} whitelisted, {} blacklisted, {} permission editor(s).",
                whitelist.size(), blacklist.size(), permissionEditors.size());
    }

    private static void fill(@NotNull List<Entry> target, @Nullable List<Entry> source) {
        if (source == null) return;
        for (Entry entry : source) if (entry != null && !entry.isEmpty()) target.add(entry);
    }

    private static volatile boolean dirty;

    public static void save() { dirty = true; }

    public static void flush() {
        if (!dirty) return;

        dirty = false;
        write();
    }

    private static volatile @Nullable String lastWritten;

    private static void write() {
        dirty = false;

        Lists lists = new Lists();
        lists.whitelist = new ArrayList<>(whitelist);
        lists.blacklist = new ArrayList<>(blacklist);
        lists.permissionEditors = new ArrayList<>(permissionEditors);

        String json = GSON.toJson(lists);

        if (json.equals(lastWritten)) return;

        AtomicWrite.write(folder().resolve(FILE_NAME), writer -> writer.write(json),
                "access lists");

        lastWritten = json;
    }

    private static class EntryAdapter implements JsonDeserializer<Entry> {
        @Override
        public @NotNull Entry deserialize(@NotNull JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive()) return parse(json.getAsString());

            if (!json.isJsonObject()) throw new JsonParseException("Access list entry must be a name, a UUID, or an object");

            JsonObject object = json.getAsJsonObject();

            return new Entry(text(object, "uuid"), text(object, "name"));
        }

        private static @Nullable String text(@NotNull JsonObject object, String key) {
            JsonElement element = object.get(key);

            if (element == null || element.isJsonNull()) return null;

            String value = element.getAsString().trim();

            return value.isEmpty() ? null : value;
        }
    }

    private SettingsAccessList() {}
}