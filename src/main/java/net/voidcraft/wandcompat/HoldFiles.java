package net.voidcraft.wandcompat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;

/** Small failure-only file transaction: an intent survives every serialization failure. */
final class HoldFiles {
    @FunctionalInterface interface RecordWriter { void write(Path destination) throws IOException; }

    private HoldFiles() {}

    static boolean hasUnresolved(Path directory) throws IOException {
        if (!Files.exists(directory)) return false;
        try (var files = Files.list(directory)) { return files.anyMatch(Files::isRegularFile); }
    }

    static Path preserve(Path directory, RecordWriter writeRecord) throws IOException {
        boolean posix = directory.getFileSystem().supportedFileAttributeViews().contains("posix");
        FileAttribute<?>[] dirAttributes = posix
            ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))}
            : new FileAttribute<?>[0];
        FileAttribute<?>[] fileAttributes = posix
            ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))}
            : new FileAttribute<?>[0];
        Files.createDirectories(directory, dirAttributes);
        if (posix) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
            // This parent is exclusively this addon's new private state directory.
            Files.setPosixFilePermissions(directory.getParent(), PosixFilePermissions.fromString("rwx------"));
        }
        String id = UUID.randomUUID().toString();
        Path intent = directory.resolve("hold-" + id + ".intent");
        Path temporary = directory.resolve("hold-" + id + ".nbt.tmp");
        Path committed = directory.resolve("hold-" + id + ".nbt");
        try (FileChannel channel = FileChannel.open(intent,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), fileAttributes)) {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode("held_operator_review_no_automatic_replay\n");
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        forceDirectory(directory, posix);
        // This call includes every codec/copy operation. The durable intent already exists.
        Files.createFile(temporary, fileAttributes);
        writeRecord.write(temporary);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
        Files.move(temporary, committed, StandardCopyOption.ATOMIC_MOVE);
        Files.delete(intent);
        forceDirectory(directory, posix);
        return committed;
    }

    private static void forceDirectory(Path directory, boolean posix) throws IOException {
        // Linux/Bisect supports directory fsync; Windows retains its inherited ACL semantics.
        if (posix) {
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
        }
    }
}
