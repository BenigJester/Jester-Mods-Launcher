package com.moodtools.hub;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Observe existing modules without requiring new module builds. Never create or repair their UI. */
final class MenuAttachmentProbe {
    static String inspect(Class<?> entry) {
        try {
            Field field = entry.getDeclaredField("directMenu");
            if (!Modifier.isStatic(field.getModifiers())) return "unsupported";
            field.setAccessible(true);
            Object menu = field.get(null);
            if (menu == null) return "not_attached";
            Object attached = menu.getClass().getMethod("isOverlayAttached").invoke(menu);
            if (!(attached instanceof Boolean)) return "unsupported";
            return Boolean.TRUE.equals(attached) ? "attached" : "not_attached";
        } catch (NoSuchFieldException | NoSuchMethodException unsupported) {
            return "unsupported";
        } catch (Throwable ignored) {
            return "probe_error";
        }
    }
}
