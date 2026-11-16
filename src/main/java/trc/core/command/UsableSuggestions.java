package trc.core.command;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.NotNull;

public final class UsableSuggestions {

    public static boolean isTrc(@NotNull ParseResults<?> parse) {
        var nodes = parse.getContext().getNodes();

        return !nodes.isEmpty() && "trc".equals(nodes.getFirst().getNode().getName());
    }

    public static <S> @NotNull CompletableFuture<Suggestions> of(@NotNull ParseResults<S> parse) {
        int cursor = parse.getReader().getTotalLength();
        CommandContextBuilder<S> context = parse.getContext();
        SuggestionContext<S> nodeBeforeCursor = context.findSuggestionContext(cursor);

        int start = Math.min(nodeBeforeCursor.startPos, cursor);
        String fullInput = parse.getReader().getString();
        String truncatedInput = fullInput.substring(0, cursor);
        String truncatedInputLowerCase = truncatedInput.toLowerCase(Locale.ROOT);

        List<CompletableFuture<Suggestions>> futures = new ArrayList<>();

        for (CommandNode<S> node : nodeBeforeCursor.parent.getChildren()) {
            if (!node.canUse(context.getSource())) continue;

            CompletableFuture<Suggestions> future = Suggestions.empty();

            try {
                future = node.listSuggestions(context.build(truncatedInput),
                        new SuggestionsBuilder(truncatedInput, truncatedInputLowerCase, start));
            } catch (CommandSyntaxException ignored) {
            }

            futures.add(future);
        }

        CompletableFuture<Suggestions> result = new CompletableFuture<>();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenRun(() -> {
            List<Suggestions> suggestions = new ArrayList<>(futures.size());

            for (CompletableFuture<Suggestions> future : futures) {
                suggestions.add(future.join());
            }

            result.complete(Suggestions.merge(fullInput, suggestions));
        });

        return result;
    }

    private UsableSuggestions() {}
}