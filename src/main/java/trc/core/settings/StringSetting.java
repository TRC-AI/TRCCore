package trc.core.settings;

import net.minecraft.network.chat.Component;

public class StringSetting extends Setting {
    public static final int MAX_LENGTH = 128;

    public String value;
    public String defaultValue;
    public int maxLength;

    public boolean sensitive;

    public StringSetting(String id, Component translationComponent, String initialValue, int maxLength, String name) {
        super(id, translationComponent, name);
        this.maxLength = Math.clamp(maxLength, 1, MAX_LENGTH);
        this.value = limit(initialValue);
        this.defaultValue = this.value;
    }

    public StringSetting sensitive() {
        this.sensitive = true;
        return this;
    }

    public void setValue(String newValue) { this.value = limit(newValue); }

    private String limit(String input) {
        if (input == null) return "";
        return input.length() > this.maxLength ? input.substring(0, this.maxLength) : input;
    }

    @Override
    public void reset() { this.value = this.defaultValue; }

    @Override
    public boolean isDefault() { return this.value.equals(this.defaultValue); }
}