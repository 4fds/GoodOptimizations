package dev.uhcm.goodopti.config;

import dev.uhcm.goodopti.GoodOptimizations;

/**
 * Central configuration for GoodOptimizations.
 * <p>
 * All fields are intentionally public so Mixins can read them
 * without reflection overhead.  A proper file-backed config
 * (JSON / TOML) can be wired here without breaking callers.
 */
public final class GOConfig {

    private GOConfig() {}

    // ── Chunk back-face culling ──────────────────────────────────────────
    /** Enable/disable the entire chunk culling subsystem. */
    public static boolean CHUNK_CULLING_ENABLED = true;

    /**
     * Dot-product threshold in the range [-1, 1].
     * A chunk whose direction vector has a dot product below this value
     * (measured against the player's look vector) is considered "behind"
     * and skipped.
     * <p>
     * {@code -0.3} ≈ 107° field-of-view cutoff — safe default that avoids
     * visible pop-in while still skipping most rear chunks.
     */
    public static float CHUNK_CULL_DOT_THRESHOLD = -0.3f;

    // ── Keyboard / input optimisation ───────────────────────────────────
    /** Enable/disable keyboard input-lag reduction. */
    public static boolean INPUT_OPT_ENABLED = true;

    /**
     * When {@code true} the keyboard handler polls GLFW directly each
     * game tick rather than relying on the event callback queue,
     * shaving off up to one frame of latency.
     */
    public static boolean INPUT_DIRECT_POLL = true;

    // ── Misc rendering tweaks ────────────────────────────────────────────
    /** Skip updating chunks that are entirely behind the frustum AND behind the player. */
    public static boolean SKIP_REAR_CHUNK_UPDATE = true;

    // ────────────────────────────────────────────────────────────────────
    public static void load() {
        // Future: read from goodopti.json in config dir
        GoodOptimizations.LOGGER.debug("[GOConfig] Using built-in defaults");
    }
}
