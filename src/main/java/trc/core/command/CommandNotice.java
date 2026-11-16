package trc.core.command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import trc.core.net.TRCCoreNetworking;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import static trc.core.Texts.chat;

public final class CommandNotice {

    public enum Audience {
        PERMISSION_ADMINS,

        SETTINGS_EDITORS;

        boolean reaches(@NotNull MinecraftServer server, @NotNull ServerPlayer player) {
            return this == PERMISSION_ADMINS
                    ? TRCCoreNetworking.maySetPermissionSetting(server, player)
                    : TRCCoreNetworking.mayEdit(server, player);
        }
    }

    public static final String SETTING_COMMAND_PREFIX = "trc-core.notice.command.";
    public static final String SETTING_UI_PREFIX = "trc-core.notice.ui.";

    static void toOthers(@NotNull CommandSourceStack source, @NotNull Audience audience,
                         @Nullable ServerPlayer except, @NotNull Component notice) {
        toOthers(source.getServer(), source.getPlayer(), audience, except, notice);
    }

    public static void toOthers(@NotNull MinecraftServer server, @Nullable ServerPlayer actor, @NotNull Audience audience,
                                @Nullable ServerPlayer except, @NotNull Component notice) {
        Component styled = notice.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == actor || player == except) continue;

            if (audience.reaches(server, player)) player.sendSystemMessage(styled);
        }

        if (actor != null) server.sendSystemMessage(notice);
    }

    static @NotNull Component actor(@NotNull CommandSourceStack source) {
        return actor(source.getPlayer());
    }

    public static @NotNull Component actor(@Nullable ServerPlayer player) {
        return player == null
                ? chat("trc-core.notice.console", "The server console")
                : Component.literal(player.getName().getString());
    }

    private CommandNotice() {}
}