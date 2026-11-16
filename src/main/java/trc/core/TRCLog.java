package trc.core;

import org.slf4j.Logger;

import org.jetbrains.annotations.NotNull;

public record TRCLog(String prefix) {
    public static final TRCLog CORE = new TRCLog("TRC-Core");
    public static final TRCLog CLIENT = new TRCLog("TRC-Core-Client");

    public TRCLog(String prefix) { this.prefix = "[" + prefix + "] "; }

    public void info(@NotNull String message, Object... args) { logger().info(this.prefix + message, args); }

    public void warn(@NotNull String message, Object... args) { logger().warn(this.prefix + message, args); }

    public void error(@NotNull String message, Object... args) { logger().error(this.prefix + message, args); }

    public void debug(@NotNull String message, Object... args) { logger().debug(this.prefix + message, args); }

    public void error(@NotNull String message, @NotNull Throwable cause, Object @NotNull ... args) {
        Object[] withCause = new Object[args.length + 1];
        System.arraycopy(args, 0, withCause, 0, args.length);
        withCause[args.length] = cause;

        logger().error(this.prefix + message, withCause);
    }

    private static @NotNull Logger logger() { return TRCCore.LOGGER; }

}