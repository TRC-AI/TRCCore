package trc.core.net;

import net.minecraft.network.codec.StreamCodec;

import trc.core.TRCLog;

import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.buffer.ByteBuf;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

final class TolerantCodec {

    static <T> @NotNull StreamCodec<@NotNull ByteBuf, @NotNull T> of(@NotNull String name, @NotNull StreamCodec<@NotNull ByteBuf, @NotNull T> inner, @NotNull T invalid) {
        AtomicBoolean reported = new AtomicBoolean();

        return new StreamCodec<>() {
            @Override
            public @NonNull T decode(@NonNull ByteBuf input) {
                T value;

                try { value = inner.decode(input); }
                catch (RuntimeException e) {
                    input.skipBytes(input.readableBytes());

                    if (reported.compareAndSet(false, true)) {
                        TRCLog.CORE.warn("Ignored a {} payload that could not be read ({}). The other side may run"
                                + " a different TRC-Core version. Further ones are logged at debug level.", name, e.toString());
                    } else TRCLog.CORE.debug("Ignored a {} payload that could not be read ({}).", name, e.toString());

                    return invalid;
                }

                if (input.isReadable()) input.skipBytes(input.readableBytes());
                return value;
            }

            @Override
            public void encode(@NonNull ByteBuf output, @NonNull T value) { inner.encode(output, value); }
        };
    }

    private TolerantCodec() {}
}