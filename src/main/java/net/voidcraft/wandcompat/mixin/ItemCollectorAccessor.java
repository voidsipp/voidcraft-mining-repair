package net.voidcraft.wandcompat.mixin;

import dev.ftb.mods.ftbultimine.utils.ItemCollector;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Used only to preserve failed-operation evidence; no mutation or automatic reward replay. */
@Mixin(value = ItemCollector.class, remap = false)
public interface ItemCollectorAccessor {
    @Accessor("items") List<ItemStack> voidcraft$heldItems();
}
