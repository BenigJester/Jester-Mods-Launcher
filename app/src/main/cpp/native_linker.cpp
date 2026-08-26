#include <jni.h>
#include <dlfcn.h>
#include <dirent.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

using MenuInitialize = int (*)(const char* package_name, const char* module_path);

static void* g_module_handle = nullptr;
static MenuInitialize g_initialize = nullptr;

namespace {

constexpr jint kSignalTracer = 1 << 0;
constexpr jint kSignalInstrumentationMap = 1 << 1;
constexpr jint kSignalHookFrameworkMap = 1 << 2;
constexpr jint kSignalInstrumentationThread = 1 << 3;
constexpr jint kSignalWritableExecutableMap = 1 << 4;

bool containsText(const char* value, const char* needle) {
    return value != nullptr && needle != nullptr && strstr(value, needle) != nullptr;
}

jint inspectStatus() {
    FILE* status = fopen("/proc/self/status", "re");
    if (status == nullptr) return 0;
    jint signals = 0;
    char line[512] = {};
    while (fgets(line, sizeof(line), status) != nullptr) {
        if (strncmp(line, "TracerPid:", 10) == 0 && strtol(line + 10, nullptr, 10) > 0) {
            signals |= kSignalTracer;
            break;
        }
    }
    fclose(status);
    return signals;
}

jint inspectMaps() {
    FILE* maps = fopen("/proc/self/maps", "re");
    if (maps == nullptr) return 0;
    jint signals = 0;
    char line[2048] = {};
    while (fgets(line, sizeof(line), maps) != nullptr) {
        if (containsText(line, "frida") || containsText(line, "libgum") ||
            containsText(line, "gadget.so")) {
            signals |= kSignalInstrumentationMap;
        }
        if (containsText(line, "xposed") || containsText(line, "lsposed") ||
            containsText(line, "substrate") || containsText(line, "zygisk")) {
            signals |= kSignalHookFrameworkMap;
        }
        const char* permissions = strchr(line, ' ');
        if (permissions != nullptr) {
            while (*permissions == ' ') ++permissions;
            if (permissions[0] == 'r' && permissions[1] == 'w' && permissions[2] == 'x') {
                signals |= kSignalWritableExecutableMap;
            }
        }
    }
    fclose(maps);
    return signals;
}

jint inspectThreads() {
    DIR* tasks = opendir("/proc/self/task");
    if (tasks == nullptr) return 0;
    jint signals = 0;
    dirent* entry = nullptr;
    while ((entry = readdir(tasks)) != nullptr) {
        if (entry->d_name[0] == '.') continue;
        std::string path = std::string("/proc/self/task/") + entry->d_name + "/comm";
        FILE* comm = fopen(path.c_str(), "re");
        if (comm == nullptr) continue;
        char name[128] = {};
        if (fgets(name, sizeof(name), comm) != nullptr &&
            (containsText(name, "gum-js-loop") || containsText(name, "gmain") ||
             containsText(name, "frida"))) {
            signals |= kSignalInstrumentationThread;
        }
        fclose(comm);
    }
    closedir(tasks);
    return signals;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_moodtools_hub_nativebridge_NativeLinker_load(
    JNIEnv* env, jclass, jstring native_path, jstring package_name) {
    const char* path = env->GetStringUTFChars(native_path, nullptr);
    const char* package = env->GetStringUTFChars(package_name, nullptr);
    g_module_handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (g_module_handle != nullptr) {
        g_initialize = reinterpret_cast<MenuInitialize>(dlsym(g_module_handle, "menu_initialize"));
    }
    const bool loaded = g_module_handle != nullptr && g_initialize != nullptr;
    if (loaded) g_initialize(package, path);
    env->ReleaseStringUTFChars(native_path, path);
    env->ReleaseStringUTFChars(package_name, package);
    return loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_moodtools_hub_nativebridge_NativeLinker_unload(JNIEnv*, jclass) {
    g_initialize = nullptr;
    if (g_module_handle != nullptr) {
        dlclose(g_module_handle);
        g_module_handle = nullptr;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_moodtools_hub_nativebridge_NativeLinker_inspectRuntime(JNIEnv*, jclass) {
    return inspectStatus() | inspectMaps() | inspectThreads();
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* /*vm*/, void* /*reserved*/) {
    return JNI_VERSION_1_6;
}
