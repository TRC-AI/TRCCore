package trc.core;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import trc.core.command.TRCClientCommand;
import trc.core.command.CommandNotice;
import trc.core.net.ClientSettingsCache;
import trc.core.net.SettingsSyncPayload;
import trc.core.settings.SettingsManager;
import trc.core.settings.TRCGlobalSettingsScreen;

import org.lwjgl.glfw.GLFW;

import static trc.core.TRCCore.MOD_ID;

public class TRCCoreClient implements ClientModInitializer {
    public static KeyMapping settingsKey;

    @Override
    public void onInitializeClient() {

        ClientPlayNetworking.registerGlobalReceiver(SettingsSyncPayload.TYPE, (payload, _) -> { if (payload != SettingsSyncPayload.INVALID) ClientSettingsCache.accept(payload); });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, _) -> {
            if (!(message.getContents() instanceof TranslatableContents translatable)) return true;

            String key = translatable.getKey();

            if (key.startsWith(CommandNotice.SETTING_COMMAND_PREFIX)) return TRCCoreClientSettings.showsNotice(true);
            if (key.startsWith(CommandNotice.SETTING_UI_PREFIX)) return TRCCoreClientSettings.showsNotice(false);

            return true;
        });

        SettingsManager.getSettingsManager().register(TRCCoreClientSettings.INSTANCE);

        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> ClientSettingsCache.clear());

        TRCClientCommand.register();

        KeyMapping.Category CATEGORY = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "trc-core")
        );

        settingsKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        "key.trc-core.settings",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_U,
                        CATEGORY
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (settingsKey.consumeClick()) client.setScreen(new TRCGlobalSettingsScreen(Component.empty()));

            SettingsManager.getSettingsManager().tick(SettingsManager.Side.CLIENT);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> SettingsManager.getSettingsManager().flush(SettingsManager.Side.CLIENT));

        AddonLoader.run("trc_addon_client", TRCLog.CLIENT, SettingsManager.Side.CLIENT);
    }
}