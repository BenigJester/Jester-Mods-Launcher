#include <jni.h>
#include <dirent.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>

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
        char path[128] = {};
        if (snprintf(path, sizeof(path), "/proc/self/task/%s/comm", entry->d_name) <= 0) continue;
        FILE* comm = fopen(path, "re");
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

extern "C" JNIEXPORT jint JNICALL
Java_com_moodtools_hub_nativebridge_NativeLinker_inspectRuntime(JNIEnv*, jclass) {
    return inspectStatus() | inspectMaps() | inspectThreads();
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM*, void*) {
    return JNI_VERSION_1_6;
}
