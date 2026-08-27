package top.niunaijun.blackbox.utils;

import android.content.Intent;
import android.os.Bundle;

import java.io.Serializable;
import java.util.ArrayList;

public final class IntentSanitizer {
    private static final String TAG = "IntentSanitizer";
    private static final String CLASS_MARKER_PREFIX = "_B_|_class_extra_|";
    private static final String ENUM_CLASS_MARKER_PREFIX = "_B_|_enum_class_extra_|";
    private static final String ENUM_NAME_MARKER_PREFIX = "_B_|_enum_name_extra_|";

    private IntentSanitizer() {
    }

    public static void sanitizeClassExtrasForIpc(Intent intent) {
        if (intent == null) {
            return;
        }
        sanitizeIntent(intent);
    }

    public static void restoreSanitizedClassExtras(Intent intent, ClassLoader classLoader) {
        if (intent == null) {
            return;
        }
        restoreIntent(intent, classLoader != null ? classLoader : IntentSanitizer.class.getClassLoader());
    }

    private static void sanitizeIntent(Intent intent) {
        sanitizeBundle(intent.getExtras());
        Intent selector = intent.getSelector();
        if (selector != null) {
            sanitizeIntent(selector);
        }
    }

    private static void sanitizeBundle(Bundle bundle) {
        if (bundle == null) {
            return;
        }
        for (String key : new ArrayList<>(bundle.keySet())) {
            Object value;
            try {
                value = bundle.get(key);
            } catch (Throwable e) {
                Slog.w(TAG, "Removing unreadable extra before IPC: " + key + ", error=" + e.getClass().getSimpleName());
                bundle.remove(key);
                continue;
            }

            if (value instanceof Class<?>) {
                Class<?> clazz = (Class<?>) value;
                bundle.putString(CLASS_MARKER_PREFIX + key, clazz.getName());
                bundle.remove(key);
                Slog.d(TAG, "Sanitized Class extra for IPC: " + key + " -> " + clazz.getName());
                continue;
            }

            if (value instanceof Enum<?>) {
                Enum<?> enumValue = (Enum<?>) value;
                bundle.putString(ENUM_CLASS_MARKER_PREFIX + key,
                        enumValue.getDeclaringClass().getName());
                bundle.putString(ENUM_NAME_MARKER_PREFIX + key, enumValue.name());
                bundle.remove(key);
                Slog.d(TAG, "Sanitized Enum extra for IPC: " + key + " -> "
                        + enumValue.getDeclaringClass().getName() + "." + enumValue.name());
                continue;
            }

            if (value instanceof Intent) {
                sanitizeIntent((Intent) value);
            } else if (value instanceof Bundle) {
                sanitizeBundle((Bundle) value);
            } else if (value instanceof ArrayList<?>) {
                sanitizeList((ArrayList<?>) value);
            }
        }
    }

    private static void sanitizeList(ArrayList<?> values) {
        for (Object value : values) {
            if (value instanceof Intent) {
                sanitizeIntent((Intent) value);
            } else if (value instanceof Bundle) {
                sanitizeBundle((Bundle) value);
            }
        }
    }

    private static void restoreIntent(Intent intent, ClassLoader classLoader) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            extras.setClassLoader(classLoader);
            restoreBundle(extras, classLoader);
        }
        Intent selector = intent.getSelector();
        if (selector != null) {
            restoreIntent(selector, classLoader);
        }
    }

    private static void restoreBundle(Bundle bundle, ClassLoader classLoader) {
        bundle.setClassLoader(classLoader);
        for (String key : new ArrayList<>(bundle.keySet())) {
            if (key.startsWith(CLASS_MARKER_PREFIX)) {
                String originalKey = key.substring(CLASS_MARKER_PREFIX.length());
                String className = bundle.getString(key);
                bundle.remove(key);
                if (className == null || bundle.containsKey(originalKey)) {
                    continue;
                }
                try {
                    bundle.putSerializable(originalKey, Class.forName(className, false, classLoader));
                    Slog.d(TAG, "Restored Class extra after IPC: " + originalKey + " <- " + className);
                } catch (Throwable e) {
                    Slog.w(TAG, "Failed to restore Class extra after IPC: " + originalKey + ", class=" + className + ", error=" + e.getClass().getSimpleName());
                }
                continue;
            }

            if (key.startsWith(ENUM_CLASS_MARKER_PREFIX)) {
                String originalKey = key.substring(ENUM_CLASS_MARKER_PREFIX.length());
                String className = bundle.getString(key);
                String enumNameKey = ENUM_NAME_MARKER_PREFIX + originalKey;
                String enumName = bundle.getString(enumNameKey);
                bundle.remove(key);
                bundle.remove(enumNameKey);
                if (className == null || enumName == null || bundle.containsKey(originalKey)) {
                    continue;
                }
                try {
                    Class<?> enumClass = Class.forName(className, false, classLoader);
                    if (!enumClass.isEnum()) {
                        throw new IllegalArgumentException("Marker class is not an enum");
                    }
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Enum<?> enumValue = Enum.valueOf((Class<? extends Enum>) enumClass, enumName);
                    bundle.putSerializable(originalKey, (Serializable) enumValue);
                    Slog.d(TAG, "Restored Enum extra after IPC: " + originalKey + " <- "
                            + className + "." + enumName);
                } catch (Throwable e) {
                    Slog.w(TAG, "Failed to restore Enum extra after IPC: " + originalKey
                            + ", class=" + className + ", value=" + enumName
                            + ", error=" + e.getClass().getSimpleName());
                }
                continue;
            }

            if (key.startsWith(ENUM_NAME_MARKER_PREFIX)) {
                continue;
            }

            Object value;
            try {
                value = bundle.get(key);
            } catch (Throwable e) {
                Slog.w(TAG, "Removing unreadable extra after IPC: " + key + ", error=" + e.getClass().getSimpleName());
                bundle.remove(key);
                continue;
            }

            if (value instanceof Intent) {
                restoreIntent((Intent) value, classLoader);
            } else if (value instanceof Bundle) {
                restoreBundle((Bundle) value, classLoader);
            } else if (value instanceof ArrayList<?>) {
                restoreList((ArrayList<?>) value, classLoader);
            }
        }
    }

    private static void restoreList(ArrayList<?> values, ClassLoader classLoader) {
        for (Object value : values) {
            if (value instanceof Intent) {
                restoreIntent((Intent) value, classLoader);
            } else if (value instanceof Bundle) {
                restoreBundle((Bundle) value, classLoader);
            }
        }
    }
}
