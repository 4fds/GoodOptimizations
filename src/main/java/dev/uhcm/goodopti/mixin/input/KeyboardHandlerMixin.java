package dev.uhcm.goodopti.mixin.input;

import dev.uhcm.goodopti.config.GOConfig;
import dev.uhcm.goodopti.util.InputQueue;
import dev.uhcm.goodopti.util.VersionedRenderAccessor;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keyboard input-lag reduction — intercepts GLFW key callbacks.
 *
 * <h2>Strategy</h2>
 * Vanilla sends GLFW key events → {@code KeyboardHandler#keyPress} →
 * processes them synchronously inside the render loop.  This means there
 * is always at least one full frame of buffering between physical key
 * press and game reaction.
 *
 * <p>This mixin records every key event in {@link InputQueue} with a
 * high-resolution timestamp.  A Fabric client-tick handler (see
 * {@code dev.uhcm.goodopti.input.InputTickHandler}) then drains the queue
 * at the earliest opportunity in the next tick, applying the key state
 * before movement is calculated.  This removes the "stuck in the render
 * callback" latency.
 *
 * <h2>Cross-version notes (1.21 – 1.21.11)</h2>
 * <ul>
 *   <li>On 1.21 – 1.21.8 the target is
 *       {@code keyPress(long, int, int, int, int)} — matched by the first
 *       injector below via an explicit descriptor.</li>
 *   <li>On 1.21.9+ the target became
 *       {@code keyPress(long, int, KeyEvent)}. The second injector targets
 *       that descriptor as a raw string, so this file still compiles
 *       against 1.21.1 (where {@code KeyEvent} does not exist). The
 *       handler takes the event as {@code Object} and extracts
 *       {@code (key, scancode, mods)} via
 *       {@link VersionedRenderAccessor#readKeyEvent(Object)}.</li>
 *   <li>Both injectors use {@code require = 0}: on versions where a
 *       descriptor does not exist the injector is skipped instead of
 *       crashing. At least one of them matches on every 1.21.x version,
 *       and even if both miss, the per-tick {@code glfwPollEvents()}
 *       flush (which needs no mixin) still delivers a latency win.</li>
 * </ul>
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    /**
     * Intercept the raw GLFW key callback (1.21 – 1.21.8) and push the
     * event into our low-latency queue <em>in addition to</em> vanilla
     * processing. We use {@code HEAD} so our timestamp is as early as
     * possible.
     */
    @Inject(
        method = "keyPress(JIIII)V",
        at     = @At("HEAD"),
        require = 0
    )
    private void goodopti$captureKeyEvent(
            long window, int key, int scancode, int action, int mods,
            CallbackInfo ci
    ) {
        if (!GOConfig.INPUT_OPT_ENABLED) return;
        // Record the physical event with a nanosecond timestamp.
        // InputQueue.offer() is non-blocking — never stalls the render thread.
        InputQueue.offer(key, scancode, action, mods);
    }

    /**
     * Same capture for the 1.21.9+ {@code keyPress(long, int, KeyEvent)}
     * signature. Declared with a raw descriptor string + {@code Object}
     * parameter so compilation against 1.21.1 (no {@code KeyEvent} class)
     * still succeeds; Mixin resolves the descriptor at runtime.
     */
    @Inject(
        method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
        at     = @At("HEAD"),
        require = 0
    )
    private void goodopti$captureKeyEventModern(
            long window, int action, Object keyEvent,
            CallbackInfo ci
    ) {
        if (!GOConfig.INPUT_OPT_ENABLED) return;
        if (keyEvent == null) return;
        int[] parts = VersionedRenderAccessor.readKeyEvent(keyEvent);
        if (parts == null) return;
        InputQueue.offer(parts[0], parts[1], action, parts[2]);
    }
}
