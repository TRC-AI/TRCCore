package trc.core.mixin;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;

import trc.core.command.TRCClientCommand;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
    @Shadow
    private CommandDispatcher<ClientSuggestionProvider> commands;

    @Shadow
    @Final
    private ClientSuggestionProvider suggestionsProvider;

    @Inject(method = "handleCommands", at = @At("RETURN"))
    private void trc$graftClientCommands(ClientboundCommandsPacket packet, CallbackInfo info) {
        TRCClientCommand.graft(this.commands, (FabricClientCommandSource) (Object) this.suggestionsProvider);
    }
}