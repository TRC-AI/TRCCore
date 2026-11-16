package trc.core.net;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import trc.core.TRCCore;

import io.netty.buffer.ByteBuf;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public record SettingsRefreshPayload() implements CustomPacketPayload {
    public static final SettingsRefreshPayload INSTANCE = new SettingsRefreshPayload();

    public static final Type<@NotNull SettingsRefreshPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TRCCore.MOD_ID, "settings_refresh"));

    public static final StreamCodec<@NotNull ByteBuf, @NotNull SettingsRefreshPayload> CODEC =
            TolerantCodec.of("settings_refresh", StreamCodec.unit(INSTANCE), INSTANCE);

    @Override
    public @NonNull Type<? extends @NotNull CustomPacketPayload> type() { return TYPE; }
}
