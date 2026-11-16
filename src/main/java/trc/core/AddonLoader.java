package trc.core;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import trc.core.settings.SettingsManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AddonLoader {

    private record Loaded(EntrypointContainer<ITRCAddon> container, ITRCAddon addon, Set<String> holders) {}

    public static void run(@NotNull String entrypoint, @NotNull TRCLog log, SettingsManager.@NotNull Side side) {
        List<EntrypointContainer<ITRCAddon>> containers =
                FabricLoader.getInstance().getEntrypointContainers(entrypoint, ITRCAddon.class);

        if (containers.isEmpty()) {
            SettingsManager.getSettingsManager().prepareForStart(side);
            log.info("No TRC-Mods found.");
            return;
        }

        log.info("Found {} TRC-Mod(s), initialising...", containers.size());

        List<Loaded> initialised = new ArrayList<>();

        for (EntrypointContainer<ITRCAddon> container : containers) {
            Loaded loaded = initialise(container, log);

            if (loaded != null) initialised.add(loaded);
        }

        SettingsManager.getSettingsManager().prepareForStart(side);

        if (initialised.isEmpty()) {
            log.warn("No TRC-Mod initialised successfully, none will be started.");
            return;
        }

        log.info("Starting {} TRC-Mod(s)...", initialised.size());

        int started = 0;

        for (Loaded loaded : initialised) if (start(loaded, log)) started++;

        if (started == containers.size()) log.info("All TRC-Mods loaded and started. Total: {}", started);
        else log.warn("{} of {} TRC-Mod(s) loaded and started; {} failed.", started, containers.size(), containers.size() - started);
    }

    private static @Nullable Loaded initialise(@NotNull EntrypointContainer<ITRCAddon> container, @NotNull TRCLog log) {
        String id = idOf(container);
        Set<String> before = registeredHolders();

        try {
            ITRCAddon addon = container.getEntrypoint();

            log.info("Init mod: {} version {}", id, versionOf(container));
            addon.init();

            return new Loaded(container, addon, addedSince(before));
        } catch (Throwable e) {
            log.error("Init of mod {} version {} failed. It will not be started, and its settings"
                            + " have been removed.", e, id, versionOf(container));
            rollback(addedSince(before), log);

            return null;
        }
    }

    private static boolean start(@NotNull Loaded loaded, @NotNull TRCLog log) {
        String id = idOf(loaded.container());

        try {
            log.info("Start mod: {} version {}", id, versionOf(loaded.container()));
            loaded.addon().start();

            return true;
        } catch (Throwable e) {
            log.error("Start of mod {} version {} failed. Its settings have been removed.",
                    e, id, versionOf(loaded.container()));
            rollback(loaded.holders(), log);

            return false;
        }
    }

    private static @NotNull Set<String> registeredHolders() {
        return new HashSet<>(SettingsManager.getSettingsManager().getRegisteredMods().keySet());
    }

    private static @NotNull Set<String> addedSince(@NotNull Set<String> before) {
        Set<String> added = registeredHolders();
        added.removeAll(before);

        return added;
    }

    private static void rollback(@NotNull Set<String> holders, @NotNull TRCLog log) {
        for (String holder : holders) {
            if (SettingsManager.getSettingsManager().unregister(holder)) {
                log.warn("Removed settings of '{}' -- the mod that registered them did not start.", holder);
            }
        }
    }

    private static @NotNull String idOf(@NotNull EntrypointContainer<ITRCAddon> container) {
        return container.getProvider().getMetadata().getId();
    }

    private static @NotNull String versionOf(@NotNull EntrypointContainer<ITRCAddon> container) {
        return container.getProvider().getMetadata().getVersion().getFriendlyString();
    }

    private AddonLoader() {}
}