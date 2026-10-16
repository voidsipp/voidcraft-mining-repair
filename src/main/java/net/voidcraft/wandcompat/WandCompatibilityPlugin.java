package net.voidcraft.wandcompat;

import java.util.List;
import java.util.Set;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Hash and merged-field checks fail startup clearly if the reviewed contract changes. */
public final class WandCompatibilityPlugin implements IMixinConfigPlugin {
    private static final String TARGET = "net.minecraft.server.level.ServerPlayerGameMode";
    private boolean dedicatedServer;

    @Override
    public void onLoad(String mixinPackage) {
        FMLLoader loader = FMLLoader.getCurrent();
        dedicatedServer = loader.getDist() == Dist.DEDICATED_SERVER;
        if (dedicatedServer) {
            var file = loader.getLoadingModList().getModFileById(TargetPin.MOD_ID);
            if (file == null) {
                throw new IllegalStateException("VoidCraft Wand Compatibility requires Architectural Building Wand 1.3.");
            }
            TargetPin.verify(file.getFile().getFilePath());
            var ultimine = loader.getLoadingModList().getModFileById("ftbultimine");
            if (ultimine == null) throw new IllegalStateException("VoidCraft compatibility requires reviewed FTB Ultimine26.1.2.5.");
            TargetPin.verify(ultimine.getFile().getFilePath(), TargetPin.ULTIMINE_SHA256);
        }
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return dedicatedServer; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (TARGET.equals(target)) {
            // Upstream-injected fields do not exist until all main apply passes
            // finish. Validate them in postApply, before this class can execute.
        } else if ("dev.ftb.mods.ftbultimine.FTBUltimine".equals(target)) {
            requireField(node, "isBreakingBlock", "Z");
            requireField(node, "tempBlockDroppedXp", "I");
            requireField(node, "tempBlockDropsList", "Ldev/ftb/mods/ftblibrary/util/Lazy;");
        } else if ("dev.ftb.mods.ftbultimine.utils.ItemCollector".equals(target)) {
            requireField(node, "items", "Ljava/util/List;");
        } else {
            throw new IllegalStateException("Unexpected VoidCraft compatibility target: " + target);
        }
    }

    private static void requireField(ClassNode node, String name, String descriptor) {
        if (node.fields.stream().noneMatch(field -> field.name.equals(name) && field.desc.equals(descriptor))) {
            throw new IllegalStateException("VoidCraft Wand Compatibility missing reviewed field " + name + "; upstream mixin application/order changed. Hold startup.");
        }
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (TARGET.equals(target)) {
            requireField(node, "architectural_building_wand$brokenState", "Lnet/minecraft/world/level/block/state/BlockState;");
            requireField(node, "architectural_building_wand$brokenEntity", "Lnet/minecraft/world/level/block/entity/BlockEntity;");
        }
    }
}
