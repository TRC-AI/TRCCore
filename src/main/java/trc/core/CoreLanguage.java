package trc.core;

import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.NotNull;

public final class CoreLanguage {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static final String FALLBACK_LANGUAGE = "en_us";

    private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z0-9_]{2,16}");

    private static final Map<String, Map<String, String>> files = new ConcurrentHashMap<>();

    public static @NotNull String get(String language, @NotNull String key, @NotNull String fallback) {
        return get(TRCCore.MOD_ID, language, key, fallback);
    }

    public static @NotNull String get(@NotNull String modId, String language, @NotNull String key, @NotNull String fallback) {
        String translated = file(modId, language).get(key);
        if (translated != null) return translated;

        translated = file(modId, FALLBACK_LANGUAGE).get(key);
        return translated != null ? translated : fallback;
    }

    private static @NotNull Map<String, String> file(@NotNull String modId, String language) {
        if (language == null) return Map.of();

        String code = language.toLowerCase(java.util.Locale.ROOT);

        if (!LANGUAGE_CODE.matcher(code).matches()) return Map.of();

        String cacheKey = modId + ":" + code;

        Map<String, String> known = files.get(cacheKey);
        if (known != null) return known;

        Optional<Map<String, String>> read = read(modId, code);
        read.ifPresent(map -> files.put(cacheKey, map));

        return read.orElse(Map.of());
    }

    private static @NotNull Optional<Map<String, String>> read(@NotNull String modId, @NotNull String code) {
        Optional<Path> path = FabricLoader.getInstance().getModContainer(modId)
                .flatMap(container -> container.findPath("assets/" + modId + "/lang/" + code + ".json"));

        if (path.isEmpty() || !Files.isRegularFile(path.get())) return Optional.empty();

        try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            Map<String, String> map = GSON.fromJson(reader, MAP_TYPE);
            return Optional.of(map == null ? Map.of() : Map.copyOf(map));
        } catch (Exception e) {
            TRCLog.CORE.warn("Could not read lang file {} of {}: {}", code, modId, e.getMessage());
            return Optional.of(Map.of());
        }
    }

    private CoreLanguage() {}
}