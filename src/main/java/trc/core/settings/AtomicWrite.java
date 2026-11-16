package trc.core.settings;

import trc.core.TRCLog;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.jetbrains.annotations.NotNull;

final class AtomicWrite {

    interface Content {
        void writeTo(Writer writer) throws IOException;
    }

    static void write(@NotNull Path target, @NotNull Content content, @NotNull String what) {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");

        try {
            Files.createDirectories(target.getParent());

            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                content.writeTo(writer);
            }

            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException | UnsupportedOperationException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            TRCLog.CORE.error("Could not write the {} to {}. The previous file is unchanged.", e, what, target);

            try { Files.deleteIfExists(temporary); }
            catch (IOException ignored) {}
        }
    }

    private AtomicWrite() {}
}
