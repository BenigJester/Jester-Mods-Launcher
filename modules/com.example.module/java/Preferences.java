package com.android.support;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class Preferences {
    private static SharedPreferences sharedPreferences;
    private static Preferences prefsInstance;
    public static Context context;
    public static boolean loadPref, isExpanded, suppressNativeSync;
    private static final Map<Integer, Boolean> sessionBooleans = new HashMap<>();
    private static final Map<Integer, Integer> sessionInts = new HashMap<>();
    private static final Map<Integer, Long> sessionLongs = new HashMap<>();
    private static final Map<Integer, String> sessionStrings = new HashMap<>();

    private static final String LENGTH = "_length";
    private static final String DEFAULT_STRING_VALUE = "";
    private static final int DEFAULT_INT_VALUE = 0; //-1
    private static final double DEFAULT_DOUBLE_VALUE = 0d; //-1d
    private static final float DEFAULT_FLOAT_VALUE = 0f; //-1f
    private static final long DEFAULT_LONG_VALUE = 0L; //-1L
    private static final boolean DEFAULT_BOOLEAN_VALUE = false;

    public static native void Changes(Context context, int featNum, String featName, int value, long Lvalue, boolean isOn, String inputText);

    private static boolean shouldPersist(int featureNum) {
        // Negative IDs are menu/settings controls and persist independently.
        // Game feature values only persist while Save feature preferences is enabled.
        return featureNum < 0 || loadPref;
    }

    private static boolean hasStoredFeature(int featureNum) {
        return context != null && Preferences.with(context).contains(String.valueOf(featureNum));
    }

    private static void persistSessionFeatures() {
        if (context == null) return;
        Preferences preferences = Preferences.with(context);
        for (Map.Entry<Integer, Boolean> entry : sessionBooleans.entrySet()) {
            if (entry.getKey() >= 0) preferences.writeBoolean(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Integer, Integer> entry : sessionInts.entrySet()) {
            if (entry.getKey() >= 0) preferences.writeInt(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Integer, Long> entry : sessionLongs.entrySet()) {
            if (entry.getKey() >= 0) preferences.writeLong(String.valueOf(entry.getKey()), entry.getValue());
        }
        for (Map.Entry<Integer, String> entry : sessionStrings.entrySet()) {
            if (entry.getKey() >= 0) preferences.writeString(entry.getKey(), entry.getValue());
        }
    }

    public static void setFeaturePreferenceSavingEnabled(Context sourceContext, boolean enabled) {
        if (sourceContext != null) context = sourceContext;
        if (context == null) return;

        loadPref = enabled;
        sessionBooleans.put(-1, enabled);
        Preferences preferences = Preferences.with(context);
        preferences.writeBoolean(-1, enabled);
        if (enabled) {
            // Capture the logic state that is active right now, not only future UI changes.
            persistSessionFeatures();
        } else {
            // Do not erase language/menu settings when feature persistence is disabled.
            preferences.clearFeaturePreferences();
        }
    }

    public static void changeFeatureInt(String featureName, int featureNum, int value) {
        sessionInts.put(featureNum, value);
        if (shouldPersist(featureNum)) Preferences.with(context).writeInt(featureNum, value);
        Changes(context, featureNum, featureName, value, 0, false, null);
    }

    public static void changeFeatureLong(String featureName, int featureNum, long Lvalue) {
        sessionLongs.put(featureNum, Lvalue);
        if (shouldPersist(featureNum)) Preferences.with(context).writeLong(String.valueOf(featureNum), Lvalue);
        Changes(context, featureNum, featureName, 0, Lvalue, false, null);
    }

    public static void changeFeatureString(String featureName, int featureNum, String inputString) {
        String safeValue = inputString == null ? "" : inputString;
        sessionStrings.put(featureNum, safeValue);
        if (shouldPersist(featureNum)) Preferences.with(context).writeString(featureNum, safeValue);
        Changes(context, featureNum, featureName, 0, 0, false, safeValue);
    }

    public static void changeFeatureBool(String featureName, int featureNum, boolean bool) {
        sessionBooleans.put(featureNum, bool);
        if (shouldPersist(featureNum)) Preferences.with(context).writeBoolean(featureNum, bool);
        Changes(context, featureNum, featureName, 0, 0, bool, null);
    }

    public static void changeFeatureBoolSession(String featureName, int featureNum, boolean bool) {
        sessionBooleans.put(featureNum, bool);
        Changes(context, featureNum, featureName, 0, 0, bool, null);
    }

    public static int loadPrefInt(String featureName, int featureNum) {
        Integer sessionValue = sessionInts.get(featureNum);
        if (sessionValue != null) return sessionValue;

        if (loadPref && featureNum >= 0 && hasStoredFeature(featureNum)) {
            int value = Preferences.with(context).readInt(featureNum);
            sessionInts.put(featureNum, value);
            Changes(context, featureNum, featureName, value, 0, false, null);
            return value;
        }
        if (featureNum < 0) return Preferences.with(context).readInt(featureNum);
        return 0;
    }

    public static long loadPrefLong(String featureName, int featureNum) {
        Long sessionValue = sessionLongs.get(featureNum);
        if (sessionValue != null) return sessionValue;

        if (loadPref && featureNum >= 0 && hasStoredFeature(featureNum)) {
            long Lvalue = Preferences.with(context).readLong(String.valueOf(featureNum));
            sessionLongs.put(featureNum, Lvalue);
            Changes(context, featureNum, featureName, 0, Lvalue, false, null);
            return Lvalue;
        }
        if (featureNum < 0) return Preferences.with(context).readLong(String.valueOf(featureNum));
        return 0;
    }

    public static boolean loadPrefBool(String featureName, int featureNum, boolean bDef) {
        Boolean sessionValue = sessionBooleans.get(featureNum);
        if (sessionValue != null) return sessionValue;

        if (featureNum == -1) {
            loadPref = Preferences.with(context).readBoolean(featureNum, bDef);
            sessionBooleans.put(featureNum, loadPref);
            return loadPref;
        }
        if (featureNum == -3) {
            isExpanded = Preferences.with(context).readBoolean(featureNum, bDef);
            sessionBooleans.put(featureNum, isExpanded);
            return isExpanded;
        }
        if (featureNum < 0) {
            boolean value = Preferences.with(context).readBoolean(featureNum, bDef);
            sessionBooleans.put(featureNum, value);
            return value;
        }

        boolean value = bDef;
        if (loadPref && hasStoredFeature(featureNum)) {
            value = Preferences.with(context).readBoolean(featureNum, bDef);
        }

        // Keep the native feature state aligned with the state shown by the menu.
        // Storing it in the session map also prevents localization/menu rebuilds from
        // repeatedly reapplying the same native toggle.
        sessionBooleans.put(featureNum, value);
        Changes(context, featureNum, featureName, 0, 0, value, null);
        return value;
    }

    public static String loadPrefString(String featureName, int featureNum) {
        String sessionValue = sessionStrings.get(featureNum);
        if (sessionValue != null) return sessionValue;

        if (loadPref && featureNum >= 0 && hasStoredFeature(featureNum)) {
            String text = Preferences.with(context).readString(featureNum);
            sessionStrings.put(featureNum, text);
            Changes(context, featureNum, featureName, 0, 0, false, text);
            return text;
        }
        if (featureNum < 0) return Preferences.with(context).readString(featureNum);
        return "";
    }

    private Preferences(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(
                context.getPackageName() + "_preferences",
                Context.MODE_PRIVATE
        );
    }

    private Preferences(Context context, String preferencesName) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(
                preferencesName,
                Context.MODE_PRIVATE
        );
    }

    /**
     * @param context
     * @return Returns a 'Preferences' instance
     */
    public static Preferences with(Context context) {
        if (prefsInstance == null) {
            prefsInstance = new Preferences(context);
        }
        return prefsInstance;
    }

    /**
     * @param context
     * @param forceInstantiation
     * @return Returns a 'Preferences' instance
     */
    public static Preferences with(Context context, boolean forceInstantiation) {
        if (forceInstantiation) {
            prefsInstance = new Preferences(context);
        }
        return prefsInstance;
    }

    /**
     * @param context
     * @param preferencesName
     * @return Returns a 'Preferences' instance
     */
    public static Preferences with(Context context, String preferencesName) {
        if (prefsInstance == null) {
            prefsInstance = new Preferences(context, preferencesName);
        }
        return prefsInstance;
    }

    /**
     * @param context
     * @param preferencesName
     * @param forceInstantiation
     * @return Returns a 'Preferences' instance
     */
    public static Preferences with(Context context, String preferencesName,
                                   boolean forceInstantiation) {
        if (forceInstantiation) {
            prefsInstance = new Preferences(context, preferencesName);
        }
        return prefsInstance;
    }

    // String related methods

    /**
     * @param what
     * @return Returns the stored value of 'what'
     */
    public String readString(String what) {
        return sharedPreferences.getString(what, DEFAULT_STRING_VALUE);
    }

    /**
     * @param what
     * @return Returns the stored value of 'what'
     */
    public String readString(int what) {
        try {
            return sharedPreferences.getString(String.valueOf(what), DEFAULT_STRING_VALUE);
        } catch (java.lang.ClassCastException ex) {
            return "";
        }
    }

    /**
     * @param what
     * @param defaultString
     * @return Returns the stored value of 'what'
     */
    public String readString(String what, String defaultString) {
        return sharedPreferences.getString(what, defaultString);
    }

    /**
     * @param where
     * @param what
     */
    public void writeString(String where, String what) {
        sharedPreferences.edit().putString(where, what).apply();
    }

    /**
     * @param where
     * @param what
     */
    public void writeString(int where, String what) {
        sharedPreferences.edit().putString(String.valueOf(where), what).apply();
    }

    // int related methods

    /**
     * @param what
     * @return Returns the stored value of 'what'
     */
    public int readInt(String what) {
        return sharedPreferences.getInt(what, DEFAULT_INT_VALUE);
    }


    /**
     * @param what
     * @return Returns the stored value of 'what'
     */
    public int readInt(int what) {
        try {
            return sharedPreferences.getInt(String.valueOf(what), DEFAULT_INT_VALUE);
        } catch (java.lang.ClassCastException ex) {
            return 0;
        }
    }

    /**
     * @param what
     * @param defaultInt
     * @return Returns the stored value of 'what'
     */
    public int readInt(String what, int defaultInt) {
        return sharedPreferences.getInt(what, defaultInt);
    }

    /**
     * @param where
     * @param what
     */
    public void writeInt(String where, int what) {
        sharedPreferences.edit().putInt(where, what).apply();
    }

    /**
     * @param where
     * @param what
     */
    public void writeInt(int where, int what) {
        sharedPreferences.edit().putInt(String.valueOf(where), what).apply();
    }

    // double related methods

    /**
     * @param what
     * @return Returns the stored value of 'what'
     */
    public double readDouble(String what) {
        if (!contains(what))
            return DEFAULT_DOUBLE_VALUE;
        return Double.longBitsToDouble(readLong(what));
    }

    /**
     * @param what
     * @param defaultDouble
     * @return Returns the stored value of 'what'
     */
    public double readDouble(String what, double defaultDouble) {
        if (!contains(what))
            return defaultDouble;
        return Double.longBitsToDouble(readLong(what));
    }

    /**
     * @param where
     * @param what
     */
    public void writeDouble(String where, double what) {
        writeLong(where, Double.doubleToRawLongBits(what));
    }

    // float related methods

    /**
     * @param what
     * @return Returns the stored value of 'what'
     */
    public float readFloat(String what) {
        return sharedPreferences.getFloat(what, DEFAULT_FLOAT_VALUE);
    }

    /**
     * @param what
     * @param defaultFloat
     * @return Returns the stored value of 'what'
     */
    public float readFloat(String what, float defaultFloat) {
        return sharedPreferences.getFloat(what, defaultFloat);
    }

    /**
     * @param where
     * @param what
     */
    public void writeFloat(String where, float what) {
        sharedPreferences.edit().putFloat(where, what).apply();
    }

    // long related methods

    /**
     * @param what
     * @return Returns the stored value of 'what'
     */
    public long readLong(String what) {
        return sharedPreferences.getLong(what, DEFAULT_LONG_VALUE);
    }

    /**
     * @param what
     * @param defaultLong
     * @return Returns the stored value of 'what'
     */
    public long readLong(String what, long defaultLong) {
        return sharedPreferences.getLong(what, defaultLong);
    }

    /**
     * @param where
     * @param what
     */
    public void writeLong(String where, long what) {
        sharedPreferences.edit().putLong(where, what).apply();
    }

    // boolean related methods

    /**
     * @param what
     * @return Returns the stored value of 'what'
     */
    public boolean readBoolean(String what) {
        return sharedPreferences.getBoolean(what, DEFAULT_BOOLEAN_VALUE);
    }

    /**
     * @param what
     * @return Returns the stored value of 'what'
     */
    public boolean readBoolean(int what) {
        return sharedPreferences.getBoolean(String.valueOf(what), DEFAULT_BOOLEAN_VALUE);
    }

    /**
     * @param what
     * @param defaultBoolean
     * @return Returns the stored value of 'what'
     */
    public boolean readBoolean(String what, boolean defaultBoolean) {
        /*if (defaultBoolean == true && !sharedPreferences.contains(what))
            writeBoolean(what, true);*/
        return sharedPreferences.getBoolean(what, defaultBoolean);
    }

    /**
     * @param what
     * @param defaultBoolean
     * @return Returns the stored value of 'what'
     */
    public boolean readBoolean(int what, boolean defaultBoolean) {
        /*if (defaultBoolean == true && !sharedPreferences.contains(String.valueOf(what)))
            writeBoolean(what, true);*/
        try {
            return sharedPreferences.getBoolean(String.valueOf(what), defaultBoolean);
        } catch (java.lang.ClassCastException ex) {
            return defaultBoolean;
        }
    }

    /**
     * @param where
     * @param what
     */
    public void writeBoolean(String where, boolean what) {
        sharedPreferences.edit().putBoolean(where, what).apply();
    }

    /**
     * @param where
     * @param what
     */
    public void writeBoolean(int where, boolean what) {
        sharedPreferences.edit().putBoolean(String.valueOf(where), what).apply();
    }

    // String set methods

    /**
     * @param key
     * @param value
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void putStringSet(final String key, final Set<String> value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            sharedPreferences.edit().putStringSet(key, value).apply();
        } else {
            // Workaround for pre-HC's lack of StringSets
            putOrderedStringSet(key, value);
        }
    }

    /**
     * @param key
     * @param value
     */
    public void putOrderedStringSet(String key, Set<String> value) {
        int stringSetLength = 0;
        if (sharedPreferences.contains(key + LENGTH)) {
            // First read what the value was
            stringSetLength = readInt(key + LENGTH);
        }
        writeInt(key + LENGTH, value.size());
        int i = 0;
        for (String aValue : value) {
            writeString(key + "[" + i + "]", aValue);
            i++;
        }
        for (; i < stringSetLength; i++) {
            // Remove any remaining values
            remove(key + "[" + i + "]");
        }
    }

    /**
     * @param key
     * @param defValue
     * @return Returns the String Set with HoneyComb compatibility
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public Set<String> getStringSet(final String key, final Set<String> defValue) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            return sharedPreferences.getStringSet(key, defValue);
        } else {
            // Workaround for pre-HC's missing getStringSet
            return getOrderedStringSet(key, defValue);
        }
    }

    /**
     * @param key
     * @param defValue
     * @return Returns the ordered String Set
     */
    public Set<String> getOrderedStringSet(String key, final Set<String> defValue) {
        if (contains(key + LENGTH)) {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            int stringSetLength = readInt(key + LENGTH);
            if (stringSetLength >= 0) {
                for (int i = 0; i < stringSetLength; i++) {
                    set.add(readString(key + "[" + i + "]"));
                }
            }
            return set;
        }
        return defValue;
    }

    // end related methods

    /**
     * @param key
     */
    public void remove(final String key) {
        if (contains(key + LENGTH)) {
            // Workaround for pre-HC's lack of StringSets
            int stringSetLength = readInt(key + LENGTH);
            if (stringSetLength >= 0) {
                sharedPreferences.edit().remove(key + LENGTH).apply();
                for (int i = 0; i < stringSetLength; i++) {
                    sharedPreferences.edit().remove(key + "[" + i + "]").apply();
                }
            }
        }
        sharedPreferences.edit().remove(key).apply();
    }

    /**
     * @param key
     * @return Returns if that key exists
     */
    public boolean contains(final String key) {
        return sharedPreferences.contains(key);
    }

    /** Removes only persisted game-feature values (numeric IDs >= 0). */
    public void clearFeaturePreferences() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (String key : sharedPreferences.getAll().keySet()) {
            try {
                if (Integer.parseInt(key) >= 0) editor.remove(key);
            } catch (NumberFormatException ignored) {
                // Named menu settings such as language/animations are intentionally preserved.
            }
        }
        editor.apply();
    }

    /**
     * Clear all the preferences
     */
    public void clear() {
        sharedPreferences.edit().clear().apply();
    }
}
