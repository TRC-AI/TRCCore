package trc.core.mixin;

import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import trc.core.TRCCore;
import trc.core.gui.TitleButtonScreen;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Unique
    private static final Identifier ICON_SPRITE =
            Identifier.fromNamespaceAndPath(TRCCore.MOD_ID, "icon");
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    /**
     * Adds the core mod button to the MC main menu.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void addButton(CallbackInfo ci) {

        int x = this.width / 2 - 100 - 4 - 20;
        int y = this.height / 4 + 48;

        SpriteIconButton button = SpriteIconButton.builder(
                        Component.translatable("menu.trc-core.settings"),
                        _ -> this.minecraft.setScreen(
                                new TitleButtonScreen(this, this)
                        ),
                        true
                )
                .size(20, 20)
                .sprite(ICON_SPRITE, 16, 16)
                .build();

        button.setPosition(x, y);
        this.addRenderableWidget(button);
    }
}
