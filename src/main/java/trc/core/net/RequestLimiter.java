package trc.core.net;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import trc.core.TRCLog;
import trc.core.settings.SettingsAudit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

public final class RequestLimiter {
    private static final int MAX_QUEUED = 64;

    private static final class State {
        boolean host;

        int lastTick = -1;

        int keysTick = -1;
        final Set<String> keysThisTick = new HashSet<>();

        final LinkedHashMap<String, Runnable> queued = new LinkedHashMap<>();
        boolean overflowLogged;

        void beginKeyTick(int tick) {
            if (keysTick == tick) return;

            keysTick = tick;
            keysThisTick.clear();
        }
    }

    private static final Map<UUID, State> players = new HashMap<>();

    public static void submit(@NotNull MinecraftServer server, @NotNull ServerPlayer player,
                              @NotNull String key, @NotNull Runnable request) {
        State state = players.computeIfAbsent(player.getUUID(), _ -> new State());
        state.host = server.isSingleplayerOwner(player.nameAndId());

        int tick = server.getTickCount();

        if (state.host) {
            state.beginKeyTick(tick);

            if (!state.keysThisTick.contains(key) && !state.queued.containsKey(key)) {
                state.keysThisTick.add(key);
                request.run();
                return;
            }
        } else if (state.queued.isEmpty() && state.lastTick != tick) {
            state.lastTick = tick;
            request.run();
            return;
        }

        if (!state.queued.containsKey(key) && state.queued.size() >= MAX_QUEUED) {
            if (!state.overflowLogged) {
                state.overflowLogged = true;
                TRCLog.CORE.warn("Dropping TRC-Core requests from {}: more than {} are waiting.",
                        SettingsAudit.clean(player.getName().getString()), MAX_QUEUED);
            }
            return;
        }

        state.queued.put(key, request);
    }

    public static boolean tryAcquire(@NotNull MinecraftServer server, @Nullable ServerPlayer player) {
        if (player == null || server.isSingleplayerOwner(player.nameAndId())) return true;

        State state = players.computeIfAbsent(player.getUUID(), _ -> new State());
        int tick = server.getTickCount();

        if (state.lastTick == tick || !state.queued.isEmpty()) return false;

        state.lastTick = tick;
        return true;
    }

    public static void drain(@NotNull MinecraftServer server) {
        int tick = server.getTickCount();

        for (State state : new ArrayList<>(players.values())) {
            if (state.queued.isEmpty()) continue;

            if (state.host) {
                state.beginKeyTick(tick);

                Iterator<Map.Entry<String, Runnable>> entries = state.queued.entrySet().iterator();

                while (entries.hasNext()) {
                    Map.Entry<String, Runnable> entry = entries.next();

                    if (state.keysThisTick.contains(entry.getKey())) continue;

                    entries.remove();
                    state.keysThisTick.add(entry.getKey());
                    entry.getValue().run();
                }
            } else {
                if (state.lastTick == tick) continue;

                Iterator<Runnable> next = state.queued.values().iterator();
                Runnable request = next.next();
                next.remove();

                state.lastTick = tick;
                request.run();
            }

            if (state.queued.isEmpty()) state.overflowLogged = false;
        }
    }

    public static void forget(@NotNull UUID player) {
        players.remove(player);
    }

    private RequestLimiter() {}
}
