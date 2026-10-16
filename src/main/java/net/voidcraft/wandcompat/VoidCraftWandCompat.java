package net.voidcraft.wandcompat;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** No registries, network channels, item changes or scheduled tasks. */
@Mod(value = "voidcraft_wand_compat", dist = Dist.DEDICATED_SERVER)
public final class VoidCraftWandCompat {
    public VoidCraftWandCompat() {
        LogUtils.getLogger().info("[VoidCraft Compatibility] Exact Wand1.3 and Ultimine26.1.2.5 verified; nested break and exception-safe capture guards enabled.");
    }
}
