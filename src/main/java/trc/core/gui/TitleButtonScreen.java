package trc.core.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import trc.core.settings.TRCGlobalSettingsScreen;

public class TitleButtonScreen extends TRCGlobalSettingsScreen {

    private final Screen parent;
    private final Screen nativeScreen;

    /**
     * Creates the core mod settings screen.
     */
    public TitleButtonScreen(Screen parent, Screen nativeScreen) {
        super(Component.translatable("menu.trc-core.settings"));
        this.parent = parent;
        this.nativeScreen = nativeScreen;
    }

    /**
     * When closed get back to MC main menu.
     */
    @Override
    public void onClose() {
        if (this.minecraft == null) {
            return;
        }

        this.minecraft.setScreen(this.nativeScreen != null ? this.nativeScreen : this.parent);
    }
}