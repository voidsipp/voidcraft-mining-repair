package net.voidcraft.wandcompat;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HoldFilesTest {
    @TempDir Path world;

    private Path directory() { return world.resolve("data/voidcraft-wand-compat/held-ultimine"); }

    @Test void codecFailureLeavesDurableRestartVisibleHoldBeforeWritingRecord() throws IOException {
        IllegalArgumentException cause = new IllegalArgumentException("codec failure");
        assertSame(cause, assertThrows(IllegalArgumentException.class, () ->
            HoldFiles.preserve(directory(), path -> {
                assertTrue(HoldFiles.hasUnresolved(directory()));
                try (var files = Files.list(directory())) {
                    assertTrue(files.anyMatch(file -> file.toString().endsWith(".intent")));
                }
                throw cause;
            })));
        assertTrue(HoldFiles.hasUnresolved(directory()));
        try (var files = Files.list(directory())) {
            assertTrue(files.anyMatch(file -> file.toString().endsWith(".intent")));
        }
    }

    @Test void successfulRecordCommitsWithoutOverwritingAndRemainsHeld() throws IOException {
        Path first = HoldFiles.preserve(directory(), path -> Files.writeString(path, "one"));
        Path second = HoldFiles.preserve(directory(), path -> Files.writeString(path, "two"));
        assertNotEquals(first, second);
        assertEquals("one", Files.readString(first));
        assertEquals("two", Files.readString(second));
        assertTrue(HoldFiles.hasUnresolved(directory()));
        try (var files = Files.list(directory())) {
            assertTrue(files.allMatch(file -> file.toString().endsWith(".nbt")));
        }
        if (directory().getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(directory()));
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(first));
        }
    }

    @Test void missingDirectoryIsClearAndFilesystemFailurePropagates() throws IOException {
        assertFalse(HoldFiles.hasUnresolved(directory()));
        Files.createDirectories(directory().getParent());
        Files.writeString(directory(), "blocked directory");
        assertThrows(IOException.class, () -> HoldFiles.preserve(directory(), path -> fail("must not serialize")));
    }
}
