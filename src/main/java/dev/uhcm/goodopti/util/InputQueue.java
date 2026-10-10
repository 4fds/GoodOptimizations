package dev.uhcm.goodopti.util;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * Lock-free-ish input event queue used by the keyboard mixin to decouple
 * GLFW callbacks from game-tick processing and reduce perceived input lag.
 *
 * <p>GLFW callbacks fire on the render thread; game logic reads the queue
 * on the tick thread.  {@link ArrayBlockingQueue} is thread-safe and
 * allocation-free when the capacity is not exceeded.
 */
public final class InputQueue {

    private InputQueue() {}

    public static final int CAPACITY = 256;

    private static final ArrayBlockingQueue<InputEvent> QUEUE =
            new ArrayBlockingQueue<>(CAPACITY);

    // ── Inner record ────────────────────────────────────────────────────

    public record InputEvent(int key, int scancode, int action, int mods, long timestampNs) {}

    // ── API ─────────────────────────────────────────────────────────────

    /**
     * Called from the GLFW keyboard callback (render thread).
     * Drops the event if the queue is full — better than blocking the
     * render thread.
     */
    public static void offer(int key, int scancode, int action, int mods) {
        // never blocks: offer() discards rather than waiting
        QUEUE.offer(new InputEvent(key, scancode, action, mods, System.nanoTime()));
    }

    /**
     * Drains all pending events and invokes {@code consumer} for each.
     * Called once per game tick, on the client tick thread.
     */
    public static void drain(java.util.function.Consumer<InputEvent> consumer) {
        InputEvent ev;
        while ((ev = QUEUE.poll()) != null) {
            consumer.accept(ev);
        }
    }

    public static int size() {
        return QUEUE.size();
    }
}
