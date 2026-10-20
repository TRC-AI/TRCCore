package trc.core.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.NonNull;
import trc.core.TRCCore;

public record SettingsEditPayload(String modId, String settingId, String value, boolean preview) implements CustomPacketPayload {
    private static final int MAX_FIELD_LENGTH = 256;

    public static final CustomPacketPayload.Type<SettingsEditPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TRCCore.MOD_ID, "settings_edit"));

    public static final SettingsEditPayload INVALID = new SettingsEditPayload("", "", "", false);

    public static final StreamCodec<ByteBuf, SettingsEditPayload> CODEC = TolerantCodec.of("settings_edit",
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_FIELD_LENGTH), SettingsEditPayload::modId,
                    ByteBufCodecs.stringUtf8(MAX_FIELD_LENGTH), SettingsEditPayload::settingId,
                    ByteBufCodecs.stringUtf8(MAX_FIELD_LENGTH), SettingsEditPayload::value,
                    ByteBufCodecs.BOOL, SettingsEditPayload::preview,
                    SettingsEditPayload::new),
            INVALID);

    @Override
    public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
