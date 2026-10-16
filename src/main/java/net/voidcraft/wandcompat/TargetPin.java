package net.voidcraft.wandcompat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Binds this deliberately narrow compatibility fix to reviewed upstream bytes. */
final class TargetPin {
    static final String MOD_ID = "architectural_building_wand";
    static final String SHA256 = "ce80818c091c925d7f172f70a14f99423c923858c2cea3d56776a313bc0640a4";
    static final String ULTIMINE_SHA256 = "2c5169a6f082338359f8c1249288a8420a87f9cdeec96fd1c2d983b17236c88f";

    private TargetPin() {}

    static void verify(Path path) {
        verify(path, SHA256);
    }

    static void verify(Path path, String expected) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > 32L * 1024 * 1024) {
                throw new IllegalStateException("VoidCraft Wand Compatibility requires the reviewed, regular Wand 1.3 JAR.");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            if (!expected.equals(HexFormat.of().formatHex(digest.digest()))) {
                throw new IllegalStateException("VoidCraft Wand Compatibility target hash mismatch. Hold startup and review the changed Wand JAR; do not disable this guard.");
            }
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("VoidCraft Wand Compatibility cannot verify its upstream target.", failure);
        }
    }
}
