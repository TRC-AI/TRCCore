package trc.core.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.NonNull;
import trc.core.TRCCore;

public record SettingsSyncPayload(boolean allowed, boolean mayManage, String json) implements CustomPacketPayload {
    public static final int MAX_JSON_LENGTH = 1 << 20;

    public static final CustomPacketPayload.Type<SettingsSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TRCCore.MOD_ID, "settings_sync"));

    public static final SettingsSyncPayload INVALID = new SettingsSyncPayload(false, false, "");

    public static final StreamCodec<ByteBuf, SettingsSyncPayload> CODEC = TolerantCodec.of("settings_sync",
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SettingsSyncPayload::allowed,
                    ByteBufCodecs.BOOL, SettingsSyncPayload::mayManage,
                    ByteBufCodecs.stringUtf8(MAX_JSON_LENGTH), SettingsSyncPayload::json,
                    SettingsSyncPayload::new),
            INVALID);

    @Override
    public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
