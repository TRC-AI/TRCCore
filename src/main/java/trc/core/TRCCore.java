package trc.core;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trc.core.command.TRCServerCommand;
import trc.core.net.TRCCoreNetworking;
import trc.core.settings.SettingsAccessList;
import trc.core.settings.SettingsManager;

public class TRCCore implements ModInitializer {
    public static final String MOD_ID = "trc-core";
    public static final Logger LOGGER = LoggerFactory.getLogger("TRC-Core");

    @Override
    public void onInitialize() {
        SettingsManager.getSettingsManager().register(TRCCoreSettings.INSTANCE);
        SettingsAccessList.load();
        ServerLifecycleEvents.SERVER_STARTED.register(SettingsAccessList::warnIfUnverified);
        TRCCoreNetworking.registerPayloads();
        TRCCoreNetworking.registerServerHandlers();
        TRCServerCommand.register();

        AddonLoader.run("trc_addon", TRCLog.CORE, SettingsManager.Side.COMMON);

        ServerTickEvents.END_SERVER_TICK.register(_ -> {
            SettingsManager.getSettingsManager().tick(SettingsManager.Side.COMMON);
            SettingsAccessList.flush();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(_ -> {
            SettingsManager.getSettingsManager().flush(SettingsManager.Side.COMMON);
            SettingsAccessList.flush();
        });
    }
}