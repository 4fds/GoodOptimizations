package dev.uhcm.goodopti.util;

import net.minecraft.core.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflection-based access to render internals whose <em>official</em> names
 * changed across 1.21.x while the intermediary names stayed stable.
 *
 * <p>Why reflection instead of {@code @Shadow}?
 * Loom remaps {@code @Shadow} members to intermediary names at build time,
 * which are stable ({@code field_45616}, {@code method_3670}, ...). That
 * already works on every 1.21.x version. This helper is a second safety net:
 * if a future 1.21.x build ever moves/renames one of these members, the
 * mixin still applies (no {@code MixinApplyError}) and the optimisation
 * silently no-ops instead of crashing the game.
 *
 * <p>All methods are null-safe and never throw.
 */
public final class VersionedRenderAccessor {

    private VersionedRenderAccessor() {}

    // ── Section origin ────────────────────────────────────────────────
    private static volatile Method originMethod;
    private static volatile boolean originResolved;

    /**
     * Returns the section origin for a {@code RenderSection} instance,
     * trying {@code getRenderOrigin()} (1.21.2+) then {@code getOrigin()}
     * (≤ 1.21.1). Returns {@code null} if neither exists.
     */
    public static BlockPos getSectionOrigin(Object section) {
        if (section == null) return null;
        try {
            Method m = originMethod;
            if (!originResolved) {
                synchronized (VersionedRenderAccessor.class) {
                    if (!originResolved) {
                        originMethod = m = resolveOriginMethod(section.getClass());
                        originResolved = true;
                    }
                }
            }
            if (m == null) {
                // Class changed under us (dev env reload)? Re-resolve once.
                m = resolveOriginMethod(section.getClass());
                originMethod = m;
                if (m == null) return null;
            }
            Object out = m.invoke(section);
            return out instanceof BlockPos pos ? pos : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Method resolveOriginMethod(Class<?> clazz) {
        for (String name : new String[]{"getRenderOrigin", "getOrigin"}) {
            try {
                Method m = clazz.getMethod(name);
                if (BlockPos.class.isAssignableFrom(m.getReturnType())
                        && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    // ── Visible sections lists ────────────────────────────────────────
    private static volatile Field visibleField;
    private static volatile Field nearbyField;
    private static volatile boolean listsResolved;

    /**
     * Collects every per-frame visible-section list on the renderer.
     * On 1.21 – 1.21.3 this is just {@code visibleSections};
     * on 1.21.4+ there is additionally {@code nearbyVisibleSections}.
     * Missing fields are skipped — the caller culls whatever is found.
     */
    @SuppressWarnings("unchecked")
    public static List<List<Object>> getVisibleSectionLists(Object renderer) {
        List<List<Object>> out = new ArrayList<>(2);
        if (renderer == null) return out;
        try {
            if (!listsResolved) {
                synchronized (VersionedRenderAccessor.class) {
                    if (!listsResolved) {
                        visibleField = findListField(renderer.getClass(),
                                "visibleSections", "field_45616");
                        nearbyField = findListField(renderer.getClass(),
                                "nearbyVisibleSections", "field_54164");
                        listsResolved = true;
                    }
                }
            }
            for (Field f : new Field[]{visibleField, nearbyField}) {
                if (f == null) continue;
                try {
                    Object v = f.get(renderer);
                    if (v instanceof List<?> list) {
                        out.add((List<Object>) list);
                    }
                } catch (IllegalAccessException | RuntimeException ignored) {
                }
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    private static Field findListField(Class<?> clazz, String... names) {
        // 1. Try known (Mojang / intermediary) names directly.
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        // 2. Fallback: first List-typed instance field (visibleSections is
        //    the first ObjectArrayList on LevelRenderer on every 1.21.x).
        for (Field f : clazz.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    return f;
                } catch (RuntimeException ignored) {
                }
            }
        }
        return null;
    }

    // ── KeyEvent (1.21.9+) field extraction ───────────────────────────
    /**
     * Extracts {@code (key, scancode, mods)} from a
     * {@code net.minecraft.client.input.KeyEvent} record instance using
     * record components (order: key, scancode, modifiers), so no compile-time
     * dependency on the class is needed and obfuscation-proof access works.
     *
     * @return int[]{key, scancode, mods} or {@code null} if unreadable
     */
    public static int[] readKeyEvent(Object keyEvent) {
        if (keyEvent == null) return null;
        try {
            // Preferred: record components preserve declaration order.
            java.lang.reflect.RecordComponent[] comps =
                    keyEvent.getClass().getRecordComponents();
            if (comps != null && comps.length == 3) {
                int[] vals = new int[3];
                for (int i = 0; i < 3; i++) {
                    Object v = comps[i].getAccessor().invoke(keyEvent);
                    if (!(v instanceof Integer iv)) return null;
                    vals[i] = iv;
                }
                return vals;
            }
            // Fallback: try common accessor names (all mappings).
            int key = callIntNoArg(keyEvent,
                    "key", "comp_4795", "m_412394_", "f_412394_", "input",
                    "method_74228", "m_415169_", "getKeycode");
            int scancode = callIntNoArg(keyEvent,
                    "scancode", "comp_4796", "m_412055_", "f_412055_");
            int mods = callIntNoArg(keyEvent,
                    "modifiers", "comp_4797", "m_415041_");
            if (key == Integer.MIN_VALUE
                    || scancode == Integer.MIN_VALUE
                    || mods == Integer.MIN_VALUE) return null;
            return new int[]{key, scancode, mods};
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static int callIntNoArg(Object target, String... names) {
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name);
                if (m.getReturnType() == int.class && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return (int) m.invoke(target);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Integer.MIN_VALUE;
    }
}
