package trc.core.settings;

import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Setting {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]+");

    public String name;

    public String id;

    @Nullable
    public Component translationComponent;

    public Setting(String id, String name) {
        this(id, null, name);
    }

    public Setting(String id, Component translationComponent) {
        this(id, translationComponent, null);
    }

    public Setting(String id, @Nullable Component translationComponent, @Nullable String name) {
        this.name = name == null || name.isBlank() ? id : name;
        this.id = validateId(id, this.name);
        this.translationComponent = translationComponent;
    }

    public abstract void reset();

    public abstract boolean isDefault();

    private static @NotNull String validateId(String id, String name) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Setting '" + name + "' has no id. Every setting needs one: lowercase a-z, 0-9, underscore or hyphen.");
        }

        String trimmed = id.trim().toLowerCase(Locale.ROOT);

        if (!VALID_ID.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Setting '" + name + "' has the invalid id '" + id + "'. Ids may only contain a-z, 0-9, underscore and hyphen.");
        }

        return trimmed;
    }
}