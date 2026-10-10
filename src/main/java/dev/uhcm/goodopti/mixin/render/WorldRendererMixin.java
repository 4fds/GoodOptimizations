package dev.uhcm.goodopti.mixin.render;

import dev.uhcm.goodopti.config.GOConfig;
import dev.uhcm.goodopti.util.FrustumHelper;
import dev.uhcm.goodopti.util.VersionedRenderAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;

/**
 * Back-face chunk culling for {@link LevelRenderer}.
 *
 * <p>We prune the per-frame visible-section lists at the end of
 * {@code applyFrustum()}. Every section whose XZ centre lies behind the
 * camera is removed from the list, so its geometry is never issued as draw
 * calls this frame.
 *
 * <h2>Why {@code applyFrustum}?</h2>
 * This method is the single stable funnel present in <em>every</em> 1.21.x
 * version (1.21 through 1.21.11): vanilla itself clears and repopulates the
 * visible-section list here (intermediary {@code method_38551}). Older
 * approaches that hooked the per-layer draw method broke across versions
 * because that method was renamed ({@code renderChunkLayer} →
 * {@code renderSectionLayer}) and later removed entirely when the
 * frame-graph renderer arrived. Targeting {@code applyFrustum} avoids all
 * of that.
 *
 * <h2>Cross-version hardening</h2>
 * <ul>
 *   <li>{@code require = 0}: if a future version ever renames this method,
 *       the injector is skipped and the game still boots — chunk culling
 *       simply stays off.</li>
 *   <li>No {@code @Shadow} fields: the visible-section lists
 *       ({@code visibleSections} on all versions, plus
 *       {@code nearbyVisibleSections} on 1.21.4+) are looked up via
 *       {@link VersionedRenderAccessor} reflection, so a renamed field can
 *       never fail mixin application.</li>
 *   <li>Section origins resolve via
 *       {@link VersionedRenderAccessor#getSectionOrigin(Object)}, which
 *       tries {@code getRenderOrigin()} (1.21.2+) then {@code getOrigin()}
 *       (≤ 1.21.1).</li>
 * </ul>
 */
@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin {

    /**
     * Runs after vanilla repopulates the visible-section lists and drops
     * every section that sits behind the player's camera.
     */
    @Inject(
        method = "applyFrustum",
        at     = @At("TAIL"),
        require = 0
    )
    private void goodopti$filterRearSections(CallbackInfo ci) {
        if (!GOConfig.CHUNK_CULLING_ENABLED) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            if (mc.gameRenderer == null) return;

            List<List<Object>> lists =
                    VersionedRenderAccessor.getVisibleSectionLists((Object) this);
            if (lists.isEmpty()) return;

            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            float yaw = mc.player.getYRot();
            float[] lv = FrustumHelper.yawToLookXZ(yaw);

            for (List<Object> visibleSections : lists) {
                if (visibleSections == null || visibleSections.isEmpty()) continue;

                Iterator<Object> it = visibleSections.iterator();
                while (it.hasNext()) {
                    Object section;
                    try {
                        section = it.next();
                    } catch (RuntimeException e) {
                        break; // fail-fast list: stop rather than corrupt it
                    }
                    if (section == null) continue;

                    BlockPos origin = VersionedRenderAccessor.getSectionOrigin(section);
                    if (origin == null) continue; // unknown shape: keep it

                    double cx = origin.getX() + 8.0;
                    double cz = origin.getZ() + 8.0;

                    if (FrustumHelper.isBehindCamera(cam, lv[0], lv[1], cx, cz)) {
                        try {
                            it.remove();
                        } catch (RuntimeException e) {
                            break;
                        }
                    }
                }
            }
        } catch (LinkageError | RuntimeException e) {
            // Never let an optimisation crash the render thread. The worst
            // case is that culling silently does nothing on this version.
        }
    }
}
