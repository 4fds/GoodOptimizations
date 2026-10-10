package dev.uhcm.goodopti.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.uhcm.goodopti.config.GOConfig;
import dev.uhcm.goodopti.util.FrustumHelper;
import dev.uhcm.goodopti.util.VersionedRenderAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Prevents background mesh-rebuild tasks from being dispatched for
 * chunk sections that are currently behind the camera.
 *
 * <p>Vanilla queues a rebuild task every time a block changes or a
 * chunk loads.  By returning {@code false} from
 * {@code SectionRenderDispatcher.RenderSection#isDirty()} for rear
 * sections we avoid the CPU+GPU cost of building geometry that will
 * not be drawn this frame anyway.
 *
 * <p>Uses MixinExtras {@link ModifyReturnValue} to keep the mixin
 * compatible with other mods that also target this method.
 *
 * <h2>Cross-version notes (1.21 – 1.21.11)</h2>
 * <ul>
 *   <li>{@code isDirty()} keeps the same intermediary identity
 *       ({@code method_3672}) on every 1.21.x version, so this injector
 *       resolves everywhere. {@code require = 0} is set anyway so a
 *       future rename degrades to a no-op instead of a crash.</li>
 *   <li>No {@code @Shadow} methods: the section origin is resolved via
 *       {@link VersionedRenderAccessor#getSectionOrigin(Object)}, which
 *       tries {@code getRenderOrigin()} (1.21.2+, intermediary
 *       {@code method_3670}) then {@code getOrigin()} (≤ 1.21.1).</li>
 * </ul>
 */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class ChunkBuilderMixin {

    /**
     * If the section is behind the camera AND
     * {@link GOConfig#SKIP_REAR_CHUNK_UPDATE} is enabled, suppress
     * the dirty flag so vanilla will not enqueue a rebuild.
     */
    @ModifyReturnValue(
        method = "isDirty",
        at     = @At("RETURN"),
        require = 0
    )
    private boolean goodopti$skipRearSectionRebuild(boolean original) {
        if (!original)                            return false;   // already clean
        if (!GOConfig.SKIP_REAR_CHUNK_UPDATE)     return true;    // feature off
        if (!GOConfig.CHUNK_CULLING_ENABLED)      return true;    // master switch

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return true;
            if (mc.gameRenderer == null) return true;

            Vec3  cam  = mc.gameRenderer.getMainCamera().getPosition();
            float yaw  = mc.player.getYRot();
            float[] lv = FrustumHelper.yawToLookXZ(yaw);

            BlockPos origin = VersionedRenderAccessor.getSectionOrigin((Object) this);
            if (origin == null) return true;
            double   cx     = origin.getX() + 8.0;
            double   cz     = origin.getZ() + 8.0;

            // If it's behind us, pretend it's clean → skip the rebuild task
            return !FrustumHelper.isBehindCamera(cam, lv[0], lv[1], cx, cz);
        } catch (LinkageError | RuntimeException e) {
            // Never break chunk rebuilds because of an optimisation.
            return true;
        }
    }
}
