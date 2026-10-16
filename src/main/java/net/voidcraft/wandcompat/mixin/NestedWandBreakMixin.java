package net.voidcraft.wandcompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * The reviewed Wand mixin (priority1000) captures context in two fields and clears
 * them at every return. Run after its transformation, wrapping the complete method.
 * Nested calls now restore the outer call's context before its callback executes.
 */
@Mixin(value = ServerPlayerGameMode.class, priority = 900)
public abstract class NestedWandBreakMixin {
    @Unique private static VarHandle voidcraft$wandStateHandle;
    @Unique private static VarHandle voidcraft$wandEntityHandle;

    @Unique
    private static void voidcraft$resolveWandContext() {
        if (voidcraft$wandStateHandle != null) return;
        try {
            // This method is merged into ServerPlayerGameMode. Its own lookup can
            // access the upstream private fields AFTER all mixins have applied.
            // @Shadow cannot bind fields introduced by another mixin during its
            // earlier preprocessing phase, even with a lower application priority.
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            VarHandle state = lookup.findVarHandle(ServerPlayerGameMode.class,
                "architectural_building_wand$brokenState", BlockState.class);
            VarHandle entity = lookup.findVarHandle(ServerPlayerGameMode.class,
                "architectural_building_wand$brokenEntity", BlockEntity.class);
            voidcraft$wandEntityHandle = entity;
            voidcraft$wandStateHandle = state;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("VoidCraft Wand Compatibility could not bind reviewed break-context fields; hold operation and review mixin contract.", failure);
        }
    }

    @WrapMethod(method = "destroyBlock", require = 1)
    private boolean voidcraft$preserveNestedWandContext(BlockPos pos, Operation<Boolean> original) {
        voidcraft$resolveWandContext();
        BlockState previousState = (BlockState) voidcraft$wandStateHandle.get(this);
        BlockEntity previousEntity = (BlockEntity) voidcraft$wandEntityHandle.get(this);
        try {
            return original.call(pos);
        } finally {
            // Run for success, cancelled/failed destruction and any propagated exception.
            voidcraft$wandStateHandle.set(this, previousState);
            voidcraft$wandEntityHandle.set(this, previousEntity);
        }
    }
}
