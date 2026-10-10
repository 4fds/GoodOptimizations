package dev.uhcm.goodopti.util;

import com.mojang.blaze3d.platform.InputConstants;
import dev.uhcm.goodopti.GoodOptimizations;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Version-tolerant bridge between the queued raw GLFW key events captured by
 * {@code KeyboardHandlerMixin} and vanilla's {@link KeyMapping} state.
 *
 * <h2>Why this class exists</h2>
 * The helper that turns a {@code (keyCode, scanCode)} pair into a key object,
 * {@code InputConstants.getKey(int, int)}, was replaced upstream by
 * {@code getKey(KeyEvent)} when the input rework landed (1.21.9+).  The
 * <em>consumers</em> — {@code KeyMapping.set(Key, boolean)} and
 * {@code KeyMapping.click(Key)} — are unchanged on every 1.21.x version, so
 * only the single conversion call is volatile.
 *
 * <p>We compile against 1.21.1 (where {@code getKey(int, int)} exists) and
 * resolve the conversion at runtime:
 * <ol>
 *   <li>Fast path: direct {@code getKey(int, int)} call — works on
 *       1.21 – 1.21.8.</li>
 *   <li>New path: {@code getKey(KeyEvent)} via reflection — works on
 *       1.21.9+ without a compile-time dependency on the record class
 *       (which does not exist on older versions).</li>
 *   <li>Poll-only fallback: if neither conversion exists, vanilla still
 *       applies key state through its own synchronous path on every
 *       {@code glfwPollEvents()}, so input keeps working everywhere —
 *       only the redundant early-apply is skipped.</li>
 * </ol>
 */
public final class KeyCompat {

    private KeyCompat() {}

    /** 0 = unknown, 1 = legacy (int,int), 2 = modern (KeyEvent), -1 = none. */
    private static volatile int mode = 0;
    private static volatile boolean loggedMode = false;

    // Reflection cache for the modern path.
    private static volatile Class<?> keyEventClass;
    private static volatile java.lang.reflect.Constructor<?> keyEventCtor;
    private static volatile java.lang.reflect.Method getKeyModern;
    private static volatile boolean modernResolved;

    public static boolean writesSupported() {
        return mode != -1;
    }

    /**
     * Applies one queued key event to vanilla's key-binding state.
     *
     * @param key      GLFW key code (or {@code -1} for unknown)
     * @param scancode platform scan code
     * @param action   GLFW action ({@code GLFW_PRESS}, {@code GLFW_RELEASE},
     *                 {@code GLFW_REPEAT})
     * @return {@code true} if the state was applied, {@code false} if this
     *         version needs poll-only mode
     */
    public static boolean applyEvent(int key, int scancode, int action) {
        return applyEvent(key, scancode, action, 0);
    }

    /**
     * Applies one queued key event, including GLFW modifiers (needed to
     * construct the 1.21.9+ {@code KeyEvent} record).
     */
    public static boolean applyEvent(int key, int scancode, int action, int mods) {
        if (mode == -1) return false;
        if (key < 0) return false;

        // ── Fast path: legacy getKey(int, int), present on 1.21 – 1.21.8 ──
        if (mode == 0 || mode == 1) {
            try {
                InputConstants.Key keyObj = InputConstants.getKey(key, scancode);
                KeyMapping.set(keyObj, action != GLFW.GLFW_RELEASE);

                // Repeating keys act like fresh presses for movement purposes.
                if (action == GLFW.GLFW_REPEAT) {
                    KeyMapping.click(keyObj);
                }
                mode = 1;
                return true;
            } catch (LinkageError e) {
                // Legacy method gone (1.21.9+) — never try it again.
                mode = 2;
            } catch (RuntimeException e) {
                return false;
            }
        }

        // ── Modern path: getKey(KeyEvent) via reflection, 1.21.9+ ──
        try {
            Object keyObj = toKeyModern(key, scancode, mods);
            if (keyObj == null) {
                downgradeToPollOnly();
                return false;
            }
            KeyMapping.set((InputConstants.Key) keyObj, action != GLFW.GLFW_RELEASE);
            if (action == GLFW.GLFW_REPEAT) {
                KeyMapping.click((InputConstants.Key) keyObj);
            }
            mode = 2;
            return true;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException e) {
            downgradeToPollOnly();
            return false;
        }
    }

    private static void downgradeToPollOnly() {
        mode = -1;
        if (!loggedMode) {
            loggedMode = true;
            GoodOptimizations.LOGGER.info(
                    "[GoodOptimizations] Key-state early-apply not available on this Minecraft version; using poll-only input mode.");
        }
    }

    /**
     * Builds {@code InputConstants.Key} via the modern
     * {@code getKey(KeyEvent)} signature using reflection only, so this
     * class still loads on versions where {@code KeyEvent} does not exist.
     */
    private static Object toKeyModern(int key, int scancode, int mods)
            throws ReflectiveOperationException {
        if (!modernResolved) {
            synchronized (KeyCompat.class) {
                if (!modernResolved) {
                    try {
                        keyEventClass = Class.forName("net.minecraft.client.input.KeyEvent");
                        keyEventCtor = keyEventClass.getDeclaredConstructor(
                                int.class, int.class, int.class);
                        keyEventCtor.setAccessible(true);
                        getKeyModern = InputConstants.class.getMethod(
                                "getKey", keyEventClass);
                        getKeyModern.setAccessible(true);
                    } catch (ClassNotFoundException | NoSuchMethodException e) {
                        keyEventClass = null;
                        keyEventCtor = null;
                        getKeyModern = null;
                    }
                    modernResolved = true;
                }
            }
        }
        if (keyEventCtor == null || getKeyModern == null) return null;
        // Canonical record order: (key, scancode, modifiers).
        Object event = keyEventCtor.newInstance(key, scancode, mods);
        return getKeyModern.invoke(null, event);
    }
}
