



#include "IO.h"
#include "Log.h"

jmethodID getAbsolutePathMethodId;

list<IO::RelocateInfo> relocate_rule;

static bool hasPathPrefix(const char *path, const std::string &prefix) {
    const size_t prefixLength = prefix.length();
    return strncmp(path, prefix.c_str(), prefixLength) == 0
           && (path[prefixLength] == '\0' || path[prefixLength] == '/');
}

const char *IO::redirectPath(const char *__path) {
    if (__path == nullptr) {
        return nullptr;
    }
    
    if (strstr(__path, "resource-cache")) {
        ALOGD("Blocking resource-cache path: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, "@idmap")) {
        ALOGD("Blocking idmap path: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, "systemui") && (strstr(__path, ".frro") || strstr(__path, "-accent-") || strstr(__path, "-dynamic-") || strstr(__path, "-neutral-"))) {
        ALOGD("Blocking systemui problematic path: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, "data@resource-cache@")) {
        ALOGD("Blocking data@resource-cache@ pattern: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, ".frro")) {
        ALOGD("Blocking .frro file: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, "systemui")) {
        ALOGD("Blocking systemui path: %s", __path);
        return "/dev/null";
    }

    if (strstr(__path, "/blackbox/")) {
        return __path;
    }

    const RelocateInfo *bestRule = nullptr;
    for (const RelocateInfo &info : relocate_rule) {
        if (hasPathPrefix(__path, info.targetPath)
            && (bestRule == nullptr || info.targetPath.length() > bestRule->targetPath.length())) {
            bestRule = &info;
        }
    }

    if (bestRule != nullptr) {
        // The redirected value only needs to survive the intercepted libc call. A
        // thread-local buffer avoids leaking one allocation for every native file
        // operation and keeps simultaneous guest processes/threads independent.
        thread_local std::string redirectedPath;
        redirectedPath.assign(bestRule->relocatePath);
        redirectedPath.append(__path + bestRule->targetPath.length());
        return redirectedPath.c_str();
    }
    return __path;
}

jstring IO::redirectPath(JNIEnv *env, jstring path) {




    return BoxCore::redirectPathString(env, path);
}

jobject IO::redirectPath(JNIEnv *env, jobject path) {






    return BoxCore::redirectPathFile(env, path);
}

void IO::addRule(const char *targetPath, const char *relocatePath) {
    if (targetPath == nullptr || relocatePath == nullptr) {
        return;
    }
    relocate_rule.push_back({targetPath, relocatePath});
}

void IO::init(JNIEnv *env) {
    jclass tmpFile = env->FindClass("java/io/File");
    getAbsolutePathMethodId = env->GetMethodID(tmpFile, "getAbsolutePath", "()Ljava/lang/String;");
}
