package trc.core.mixin;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import trc.core.command.TRCClientCommand;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientSuggestionProvider.class)
abstract class ClientSuggestionProviderMixin {

    @Inject(method = "customSuggestion", at = @At("RETURN"), cancellable = true)
    private void trc$addOwnSuggestions(CommandContext<?> context,
                                       CallbackInfoReturnable<CompletableFuture<Suggestions>> info) {
        CompletableFuture<Suggestions> combined = TRCClientCommand.withOwnSuggestions(
                context.getInput(), (FabricClientCommandSource) (Object) this, info.getReturnValue());

        if (combined != null) info.setReturnValue(combined);
    }
}