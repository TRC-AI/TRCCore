package trc.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import trc.core.command.UsableSuggestions;

import java.util.concurrent.CompletableFuture;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {

    @WrapOperation(method = "handleCustomCommandSuggestions", at = @At(value = "INVOKE",
            target = "Lcom/mojang/brigadier/CommandDispatcher;getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Suggestions> trc$onlyUsableSuggestions(CommandDispatcher<CommandSourceStack> dispatcher,
                                                                     ParseResults<CommandSourceStack> parse,
                                                                     Operation<CompletableFuture<Suggestions>> original) {
        return UsableSuggestions.isTrc(parse) ? UsableSuggestions.of(parse) : original.call(dispatcher, parse);
    }
}