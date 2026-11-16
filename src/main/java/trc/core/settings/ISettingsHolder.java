package trc.core.settings;

import java.util.List;

public interface ISettingsHolder {
    String getModId();
    List<Setting> getSettings();

    default void onSettingsChanged() {}
}