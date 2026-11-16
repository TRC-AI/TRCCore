package trc.core;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jetbrains.annotations.NotNull;

public final class Texts {

    public record Text(@NotNull String key, @NotNull String fallback) {
        public @NotNull MutableComponent get(Object... args) { return chat(key, fallback, args); }
    }

    public static @NotNull Text text(@NotNull String key, @NotNull String fallback) { return new Text(key, fallback); }

    public static @NotNull MutableComponent chat(@NotNull String key, @NotNull String fallback, Object... args) { return Component.translatableWithFallback(key, fallback, args); }

    private Texts() {}
}