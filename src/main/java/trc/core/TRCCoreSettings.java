package trc.core;

import net.minecraft.network.chat.Component;

import trc.core.settings.BoolSetting;
import trc.core.settings.ISettingsHolder;
import trc.core.settings.IntSetting;
import trc.core.settings.Setting;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

public class TRCCoreSettings implements ISettingsHolder {
    public static final TRCCoreSettings INSTANCE = new TRCCoreSettings();

    public static final IntSetting REQUIRED_OP_LEVEL =
            new IntSetting("required_op_level", Component.translatable("trc-core.setting.required_op_level"), 5, 0, 5, 1000, false);

    public static final BoolSetting ALLOW_LAN_EDITS =
            new BoolSetting("allow_lan_edits", Component.translatable("trc-core.setting.allow_lan_edits"), false);

    private static final List<Setting> SETTINGS = List.of(REQUIRED_OP_LEVEL, ALLOW_LAN_EDITS);

    private static final int HIGHEST_PERMISSION_LEVEL = 4;

    public static @Nullable Component valueLabel(@Nullable String modId, @NotNull Setting setting) {
        return isPastTop(modId, setting) ? Texts.chat("trc-core.value.nobody", "nobody") : null;
    }

    public static boolean isPastTop(@Nullable String modId, @NotNull Setting setting) {
        return TRCCore.MOD_ID.equals(modId)
                && setting instanceof IntSetting level
                && REQUIRED_OP_LEVEL.id.equals(level.id)
                && level.value > HIGHEST_PERMISSION_LEVEL;
    }

    @Override
    public String getModId() { return TRCCore.MOD_ID; }

    @Override
    public List<Setting> getSettings() { return SETTINGS; }
}