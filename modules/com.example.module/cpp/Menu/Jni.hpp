
#ifndef ANDROID_MOD_MENU_JNI_HPP
#define ANDROID_MOD_MENU_JNI_HPP

#include <jni.h>

namespace ToastLength {
    inline const int LENGTH_LONG = 1;
    inline const int LENGTH_SHORT = 0;
}

void Dialog(JNIEnv *env, jobject context, const char *title, const char *message, const char *openBtn, const char *closeBtn, int sec, const char *url);

void Toast(JNIEnv *env, jobject thiz, const char *text, int length);

void startService(JNIEnv *env, jobject ctx);

int get_api_sdk(JNIEnv *env);

void CheckOverlayPermission(JNIEnv *env, jclass thiz, jobject ctx);

// Starts the native observer exactly once after Java identifies how this payload was loaded.
// 1 = launcher injection (root or BlackBox), 2 = authorized direct-patch APK.
bool StartNativeRuntime(int method);

bool IsDirectPatchRuntime();

#endif //ANDROID_MOD_MENU_JNI_HPP
