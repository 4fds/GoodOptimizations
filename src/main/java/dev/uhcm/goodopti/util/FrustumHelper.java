package dev.uhcm.goodopti.util;

import dev.uhcm.goodopti.config.GOConfig;
import net.minecraft.world.phys.Vec3;

/**
 * Utility methods shared by the chunk-culling mixins.
 */
public final class FrustumHelper {

    private FrustumHelper() {}

    /**
     * Returns {@code true} if the chunk at {@code chunkCenterX / chunkCenterZ}
     * is behind the player's camera and should therefore be culled.
     *
     * <p>The test uses the XZ-plane dot-product between the player's look
     * direction and the vector from the player to the chunk centre.
     * Chunks with a normalised dot product below
     * {@link GOConfig#CHUNK_CULL_DOT_THRESHOLD} are considered "behind".
     *
     * @param playerPos     world-space camera position
     * @param lookX         X component of the normalised look vector (yaw only)
     * @param lookZ         Z component of the normalised look vector (yaw only)
     * @param chunkCenterX  world-space X centre of the chunk (chunkX * 16 + 8)
     * @param chunkCenterZ  world-space Z centre of the chunk (chunkZ * 16 + 8)
     * @return {@code true} → cull this chunk
     */
    public static boolean isBehindCamera(Vec3 playerPos,
                                         float lookX, float lookZ,
                                         double chunkCenterX, double chunkCenterZ) {
        double dx = chunkCenterX - playerPos.x;
        double dz = chunkCenterZ - playerPos.z;

        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) return false;   // player is inside the chunk → never cull

        double ndx = dx / len;
        double ndz = dz / len;

        double dot = ndx * lookX + ndz * lookZ;
        return dot < GOConfig.CHUNK_CULL_DOT_THRESHOLD;
    }

    /**
     * Decomposes a Minecraft yaw angle (degrees, south = 0, CW positive)
     * into a normalised XZ look vector.
     *
     * @param yawDegrees player camera yaw from {@code Entity#getYRot()}
     * @return float[]{lookX, lookZ} both normalised
     */
    public static float[] yawToLookXZ(float yawDegrees) {
        // MC yaw: 0 = south (+Z), 90 = west (-X), 180 = north (-Z), 270 = east (+X)
        double rad = Math.toRadians(yawDegrees);
        float lookX = (float) -Math.sin(rad);
        float lookZ = (float)  Math.cos(rad);
        return new float[]{ lookX, lookZ };
    }
}
