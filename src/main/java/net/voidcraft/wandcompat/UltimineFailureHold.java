package net.voidcraft.wandcompat;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/** Failure-only quarantine. Records are never automatically restored or paid out. */
public final class UltimineFailureHold {
    private UltimineFailureHold() {}

    private static Path directory(ServerPlayer player) {
        // World-owned recovery state follows the server's retained world through pack updates.
        return player.level().getServer().getWorldPath(LevelResource.ROOT).resolve("data")
            .resolve("voidcraft-wand-compat").resolve("held-ultimine");
    }

    public static boolean hasUnresolved(ServerPlayer player) {
        try {
            // Intent, temporary and committed records all hold; nothing is automatically replayed.
            return HoldFiles.hasUnresolved(directory(player));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect held Ultimine state; hold Ultimine for operator review.", failure);
        }
    }

    public static void preserve(ServerPlayer player, BlockPos pos, List<ItemStack> stacks,
                                int xp, boolean captureWasActive, Throwable original) throws IOException {
        Path destination = HoldFiles.preserve(directory(player), temporary -> {
        CompoundTag record = new CompoundTag();
        record.putInt("schema", 1);
        record.putString("state", "held_operator_review_no_automatic_replay");
        record.putString("at_utc", Instant.now().toString());
        record.putString("player_uuid", player.getUUID().toString());
        record.putString("dimension", player.level().dimension().identifier().toString());
        record.putLong("origin", pos.asLong());
        record.putBoolean("original_capture_still_active", captureWasActive);
        record.putBoolean("partial_drop_possible", !captureWasActive);
        record.putString("failure_class", original.getClass().getName());
        record.putInt("captured_xp", xp);
        ListTag items = new ListTag();
        var ops = player.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) items.add(ItemStack.CODEC.encodeStart(ops, stack.copy()).getOrThrow());
        }
        record.put("captured_items", items);
        NbtIo.writeCompressed(record, temporary);
        });
        LogUtils.getLogger().error("[VoidCraft Compatibility] Ultimine failed; capture released and captured data held for operator review. No automatic replay. Record {}", destination.getFileName());
    }
}
