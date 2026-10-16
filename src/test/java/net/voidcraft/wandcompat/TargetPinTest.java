package net.voidcraft.wandcompat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TargetPinTest {
    @TempDir Path temp;

    @Test void acceptsExactReviewedJar() {
        Path jar = Path.of("libs/architectural-building-wand-1.3.jar");
        assertTrue(Files.isRegularFile(jar));
        assertDoesNotThrow(() -> TargetPin.verify(jar));
    }

    @Test void rejectsChangedJar() throws Exception {
        Path file = temp.resolve("changed.jar");
        Files.writeString(file, "changed upstream bytes");
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> TargetPin.verify(file));
        assertTrue(error.getMessage().contains("hash mismatch"));
    }

    @Test void rejectsMissingFile() {
        assertThrows(IllegalStateException.class, () -> TargetPin.verify(temp.resolve("missing.jar")));
    }

    @Test void rejectsDirectory() {
        assertThrows(IllegalStateException.class, () -> TargetPin.verify(temp));
    }

    @Test void acceptsExactReviewedUltimineJar() {
        Path jar = Path.of("libs/ftb-ultimine-neoforge-26.1.2.5.jar");
        assertDoesNotThrow(() -> TargetPin.verify(jar, TargetPin.ULTIMINE_SHA256));
    }

    @Test void rejectsWandBytesInPlaceOfUltimine() {
        Path jar = Path.of("libs/architectural-building-wand-1.3.jar");
        assertThrows(IllegalStateException.class, () -> TargetPin.verify(jar, TargetPin.ULTIMINE_SHA256));
    }
}
