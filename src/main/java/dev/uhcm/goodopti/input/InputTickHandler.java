package dev.uhcm.goodopti.input;

import dev.uhcm.goodopti.config.GOConfig;
import dev.uhcm.goodopti.util.InputQueue;
import dev.uhcm.goodopti.util.KeyCompat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Per-tick half of the keyboard input-lag reduction.
 *
 * <p>Runs on {@link ClientTickEvents#END_CLIENT_TICK} — a Fabric API event
 * whose signature is identical on every supported Minecraft version, unlike
 * {@code KeyboardInput#tick}, whose descriptor changed from
 * {@code tick(boolean, float)} (≤ 1.21.3) to {@code tick()} (≥ 1.21.4) and
 * therefore cannot be targeted by a single mixin.
 *
 * <p>Each tick this handler:
 * <ol>
 *   <li>flushes OS-buffered key events with an extra
 *       {@code glfwPollEvents()} call (client tick runs on the render
 *       thread, so this is safe), shaving up to one frame of latency; then
 *   <li>drains {@link InputQueue} and early-applies the key states via
 *       {@link KeyCompat} on versions where that API exists.</li>
 * </ol>
 */
public final class InputTickHandler {

    private InputTickHandler() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onEndTick(client));
    }

    private static void onEndTick(Minecraft mc) {
        if (!GOConfig.INPUT_OPT_ENABLED) return;
        if (mc == null || mc.getWindow() == null) return;

        try {
            // Extra poll — flushes OS events that arrived since the last
            // render-frame poll so movement code sees the freshest state.
            if (GOConfig.INPUT_DIRECT_POLL) {
                long handle = mc.getWindow().getWindow();
                if (handle != 0L) {
                    GLFW.glfwPollEvents();
                }
            }

            // Drain the timestamped queue captured by KeyboardHandlerMixin.
            // KeyCompat auto-selects the legacy (int,int) or modern
            // (KeyEvent) conversion per version, falling back to poll-only.
            if (KeyCompat.writesSupported() && InputQueue.size() > 0) {
                InputQueue.drain(event ->
                        KeyCompat.applyEvent(event.key(), event.scancode(), event.action(), event.mods()));
            }
        } catch (LinkageError | RuntimeException e) {
            // Input optimisation must never break the tick loop.
        }
    }
}
