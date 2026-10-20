package trc.core.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;

import org.jspecify.annotations.NonNull;
import trc.core.TRCLog;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.NotNull;

final class TolerantCodec {

    static <T> @NotNull StreamCodec<ByteBuf, T> of(@NotNull String name, @NotNull StreamCodec<ByteBuf, T> inner, @NotNull T invalid) {
        AtomicBoolean reported = new AtomicBoolean();

        return new StreamCodec<>() {
            @Override
            public @NonNull T decode(@NonNull ByteBuf input) {
                T value;

                try {
                    value = inner.decode(input);
                } catch (RuntimeException e) {
                    input.skipBytes(input.readableBytes());

                    if (reported.compareAndSet(false, true)) {
                        TRCLog.CORE.warn("Ignored a {} payload that could not be read ({}). The other side may run"
                                + " a different TRC-Core version. Further ones are logged at debug level.", name, e.toString());
                    } else {
                        TRCLog.CORE.debug("Ignored a {} payload that could not be read ({}).", name, e.toString());
                    }

                    return invalid;
                }

                if (input.isReadable()) input.skipBytes(input.readableBytes());

                return value;
            }

            @Override
            public void encode(@NonNull ByteBuf output, @NonNull T value) {
                inner.encode(output, value);
            }
        };
    }

    private TolerantCodec() {}
}
