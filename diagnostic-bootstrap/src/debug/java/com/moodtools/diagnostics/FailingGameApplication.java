package com.moodtools.diagnostics;

/** Isolated-process device-test fixture; never part of the release bootstrap embedded in games. */
public final class FailingGameApplication extends android.app.Application {
    @Override public void onCreate() { throw new AssertionError("Diagnostic queries must not start gameplay"); }
}
