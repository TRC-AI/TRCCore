package trc.core.settings;

import net.minecraft.network.chat.Component;

import java.util.List;

public class ChoiceSetting extends Setting {
    public List<String> values;
    public int index;
    public int defaultIndex;

    public ChoiceSetting(String id, Component translationComponent, List<String> values, int initialIndex, String name) {
        super(id, translationComponent, name);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("ChoiceSetting '" + id + "' needs at least one value");
        }
        this.values = List.copyOf(values);
        this.index = Math.clamp(initialIndex, 0, this.values.size() - 1);
        this.defaultIndex = this.index;
    }

    public String getValue() { return this.values.get(this.index); }

    @Override
    public void reset() { this.index = this.defaultIndex; }

    @Override
    public boolean isDefault() { return this.index == this.defaultIndex; }
}