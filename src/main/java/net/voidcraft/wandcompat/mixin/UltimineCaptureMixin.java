package net.voidcraft.wandcompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftblibrary.util.Lazy;
import dev.ftb.mods.ftbultimine.FTBUltimine;
import dev.ftb.mods.ftbultimine.utils.ItemCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.voidcraft.wandcompat.UltimineFailureHold;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Releases only this operation's capture on failure and preserves, never replays, ambiguous drops. */
@Mixin(value = FTBUltimine.class, remap = false)
public abstract class UltimineCaptureMixin {
    @Shadow private boolean isBreakingBlock;
    @Shadow private int tempBlockDroppedXp;
    @Shadow @Final private Lazy<ItemCollector> tempBlockDropsList;
    @Unique private boolean voidcraft$operationStarted;
    @Unique private boolean voidcraft$heldForReview;

    @Inject(method = "handleBlockBreak", at = @At(value = "FIELD",
        target = "Ldev/ftb/mods/ftbultimine/FTBUltimine;isBreakingBlock:Z",
        opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER), require = 1)
    private void voidcraft$markCaptureStarted(LevelAccessor level, BlockPos pos, BlockState state,
        ServerPlayer player, CallbackInfoReturnable<Boolean> ci) {
        voidcraft$operationStarted = true;
    }

    @WrapMethod(method = "handleBlockBreak", require = 1)
    private boolean voidcraft$releaseCaptureOnFailure(LevelAccessor level, BlockPos pos, BlockState state,
        ServerPlayer player, Operation<Boolean> original) {
        // The original rejects nested calls while an owned operation is active.
        if (isBreakingBlock) return original.call(level, pos, state, player);
        if (voidcraft$heldForReview || UltimineFailureHold.hasUnresolved(player)) return false;
        voidcraft$operationStarted = false;
        try {
            return original.call(level, pos, state, player);
        } catch (RuntimeException | Error failure) {
            boolean captureWasActive = isBreakingBlock;
            // Release globally intercepting ItemEntity/XP events BEFORE any recovery work.
            isBreakingBlock = false;
            if (voidcraft$operationStarted) {
                voidcraft$heldForReview = true;
                try {
                    ItemCollector collector = tempBlockDropsList.get();
                    UltimineFailureHold.preserve(player, pos,
                        ((ItemCollectorAccessor) collector).voidcraft$heldItems(), tempBlockDroppedXp,
                        captureWasActive, failure);
                } catch (Exception | LinkageError preservationFailure) {
                    failure.addSuppressed(preservationFailure);
                    LogUtils.getLogger().error("[VoidCraft Compatibility] Failed to persist held Ultimine data; in-memory collector retained and Ultimine held. Capture remains released.", preservationFailure);
                }
            }
            throw failure;
        } finally {
            isBreakingBlock = false;
            voidcraft$operationStarted = false;
        }
    }
}
