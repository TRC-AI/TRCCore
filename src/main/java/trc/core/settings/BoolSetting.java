package trc.core.settings;

import net.minecraft.network.chat.Component;

public class BoolSetting extends Setting {
    public boolean value;

    public boolean defaultValue;

    public BoolSetting(String id, String name, boolean initialValue) {
        this(id, null, initialValue, name);
    }

    public BoolSetting(String id, Component translationComponent, boolean initialValue) {
        this(id, translationComponent, initialValue, null);
    }

    public BoolSetting(String id, Component translationComponent, boolean initialValue, String name) {
        super(id, translationComponent, name);
        this.value = initialValue;
        this.defaultValue = initialValue;
    }

    @Override
    public void reset() { this.value = this.defaultValue; }

    @Override
    public boolean isDefault() { return this.value == this.defaultValue; }
}