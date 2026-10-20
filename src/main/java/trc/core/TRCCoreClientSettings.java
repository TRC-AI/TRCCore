package trc.core;

import net.minecraft.locale.Language;

import trc.core.settings.ChoiceSetting;
import trc.core.settings.ISettingsHolder;
import trc.core.settings.Setting;

import java.util.List;

public class TRCCoreClientSettings implements ISettingsHolder {
    public static final TRCCoreClientSettings INSTANCE = new TRCCoreClientSettings();

    public static final String MOD_ID = TRCCore.MOD_ID + "_client";

    public static final ChoiceSetting CHAT_NOTICES = new ChoiceSetting("chat_notices", null, List.of("all", "none", "command only", "ui only"), 2, "Settings changes in chat");

    private static final List<Setting> SETTINGS = List.of(CHAT_NOTICES);

    public static void localize() { CHAT_NOTICES.name = Language.getInstance().getOrDefault("trc-core.setting.chat_notices", "Settings changes in chat"); }

    public static boolean showsNotice(boolean fromCommand) {
        return switch (CHAT_NOTICES.index) {
            case 1 -> false;
            case 2 -> fromCommand;
            case 3 -> !fromCommand;
            default -> true;
        };
    }

    @Override
    public String getModId() { return MOD_ID; }

    @Override
    public List<Setting> getSettings() { return SETTINGS; }
}