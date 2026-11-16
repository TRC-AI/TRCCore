package trc.core.settings;

import net.minecraft.network.chat.Component;

public class KeySetting extends Setting {
    public static final String UNBOUND = "key.keyboard.unknown";

    public String value;
    public String defaultValue;

    public KeySetting(String id, String name, String initialKey) {
        this(id, null, initialKey, name);
    }

    public KeySetting(String id, Component translationComponent, String initialKey) {
        this(id, translationComponent, initialKey, null);
    }

    public KeySetting(String id, Component translationComponent, String initialKey, String name) {
        super(id, translationComponent, name);
        this.value = normalize(initialKey);
        this.defaultValue = this.value;
    }

    public void setValue(String newKey) { this.value = normalize(newKey); }

    public boolean isUnbound() { return UNBOUND.equals(this.value); }

    private static String normalize(String key) {
        return key == null || key.isBlank() ? UNBOUND : key;
    }

    @Override
    public void reset() { this.value = this.defaultValue; }

    @Override
    public boolean isDefault() { return this.value.equals(this.defaultValue); }
}