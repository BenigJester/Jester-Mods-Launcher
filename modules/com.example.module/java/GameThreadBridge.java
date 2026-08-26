package com.android.support;

import android.app.Activity;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

final class GameThreadBridge {
    private static final String TAG = "GameThreadBridge";
    private static final AtomicBoolean autoplayQueued = new AtomicBoolean(false);
    private static volatile GLSurfaceView glSurfaceView;

    private GameThreadBridge() {
    }

    private static native void nativeRunPendingSave();
    private static native void nativeRunAutoCloseAds();
    private static native void nativeRunAutoplay();

    static void bind(Context context) {
        if (!(context instanceof Activity)) return;
        try {
            View root = ((Activity) context).getWindow().getDecorView();
            GLSurfaceView found = findGlSurfaceView(root);
            if (found != null) {
                glSurfaceView = found;
                Log.i(TAG, "Bound GLSurfaceView fallback: " + found.getClass().getName());
            } else {
                Log.w(TAG, "No GLSurfaceView found while binding game Activity");
            }
        } catch (Throwable error) {
            Log.w(TAG, "Unable to bind game render view", error);
        }
    }

    private static GLSurfaceView findGlSurfaceView(View view) {
        if (view instanceof GLSurfaceView) return (GLSurfaceView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            GLSurfaceView found = findGlSurfaceView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static boolean queueOnGameThread(Runnable runnable, String action) {
        try {
            Class<?> helperClass = Class.forName("org.cocos2dx.lib.Cocos2dxHelper");
            Method runOnGlThread = helperClass.getDeclaredMethod("runOnGLThread", Runnable.class);
            runOnGlThread.setAccessible(true);
            runOnGlThread.invoke(null, runnable);
            Log.i(TAG, "Queued " + action + " through Cocos2dxHelper");
            return true;
        } catch (Throwable cocosError) {
            GLSurfaceView fallback = glSurfaceView;
            if (fallback != null) {
                try {
                    fallback.queueEvent(runnable);
                    Log.i(TAG, "Queued " + action + " through GLSurfaceView fallback");
                    return true;
                } catch (Throwable glError) {
                    Log.w(TAG, "Unable to queue " + action + " on GLSurfaceView", glError);
                }
            }
            Log.w(TAG, "Unable to queue " + action + " on a game GL thread", cocosError);
            return false;
        }
    }

    static boolean queuePendingSave() {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                nativeRunPendingSave();
            }
        };

        return queueOnGameThread(runnable, "save action");
    }

    static boolean queueAutoCloseAds() {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                nativeRunAutoCloseAds();
            }
        };

        return queueOnGameThread(runnable, "auto-close ads action");
    }

    static boolean queueAutoplay() {
        if (!autoplayQueued.compareAndSet(false, true)) {
            return true;
        }
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    nativeRunAutoplay();
                } finally {
                    autoplayQueued.set(false);
                }
            }
        };

        if (queueOnGameThread(runnable, "autoplay action")) {
            return true;
        } else {
            autoplayQueued.set(false);
            return false;
        }
    }
}
