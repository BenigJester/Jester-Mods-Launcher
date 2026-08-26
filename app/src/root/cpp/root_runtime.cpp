#include <jni.h>

#include <android/log.h>
#include <fcntl.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <string>

namespace {

constexpr char kLogTag[] = "MoodToolsRoot";
constexpr size_t kMaxDexBytes = 32U * 1024U * 1024U;
constexpr size_t kMaxNativeBytes = 64U * 1024U * 1024U;
constexpr uint64_t kPayloadMagic = 0x4d4f4f44524f4f54ULL;

struct PayloadDescriptor {
    uint64_t magic;
    int32_t dexFd;
    int32_t nativeFd;
};

#define ROOT_LOGI(...) __android_log_print(ANDROID_LOG_INFO, kLogTag, __VA_ARGS__)
#define ROOT_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)

bool clearException(JNIEnv *env, const char *operation) {
    if (!env->ExceptionCheck()) return false;
    ROOT_LOGE("JNI exception during %s", operation);
    env->ExceptionDescribe();
    env->ExceptionClear();
    return true;
}

std::string currentPackageName() {
    int fd = open("/proc/self/cmdline", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return {};
    char buffer[256] = {};
    const ssize_t count = read(fd, buffer, sizeof(buffer) - 1);
    close(fd);
    if (count <= 0) return {};

    std::string process(buffer, strnlen(buffer, static_cast<size_t>(count)));
    const size_t suffix = process.find(':');
    if (suffix != std::string::npos) process.resize(suffix);
    if (process.empty()) return {};
    for (const char value : process) {
        const bool valid = (value >= 'a' && value <= 'z') ||
                           (value >= 'A' && value <= 'Z') ||
                           (value >= '0' && value <= '9') || value == '_' || value == '.';
        if (!valid) return {};
    }
    return process;
}

bool copyFdToPath(int sourceFd, const std::string &destination, size_t maximumBytes) {
    struct stat sourceStat {};
    if (fstat(sourceFd, &sourceStat) != 0 || sourceStat.st_size <= 0 ||
        static_cast<uint64_t>(sourceStat.st_size) > maximumBytes) {
        ROOT_LOGE("Invalid payload size for %s", destination.c_str());
        return false;
    }

    int output = open(destination.c_str(), O_CREAT | O_TRUNC | O_WRONLY | O_CLOEXEC,
                      S_IRUSR | S_IWUSR | S_IXUSR);
    if (output < 0) {
        ROOT_LOGE("Cannot create %s: %s", destination.c_str(), strerror(errno));
        return false;
    }

    char buffer[64 * 1024];
    off_t offset = 0;
    bool success = true;
    while (offset < sourceStat.st_size) {
        const size_t requested = static_cast<size_t>(
                std::min<off_t>(sizeof(buffer), sourceStat.st_size - offset));
        const ssize_t count = pread(sourceFd, buffer, requested, offset);
        if (count <= 0) {
            success = false;
            break;
        }
        ssize_t written = 0;
        while (written < count) {
            const ssize_t result = write(output, buffer + written,
                                         static_cast<size_t>(count - written));
            if (result <= 0) {
                success = false;
                break;
            }
            written += result;
        }
        if (!success) break;
        offset += count;
    }
    if (success) success = fsync(output) == 0;
    close(output);
    if (!success) unlink(destination.c_str());
    return success;
}

void *mapDex(int dexFd, size_t *sizeOut) {
    struct stat dexStat {};
    if (fstat(dexFd, &dexStat) != 0 || dexStat.st_size <= 0 ||
        static_cast<uint64_t>(dexStat.st_size) > kMaxDexBytes) {
        return nullptr;
    }
    const size_t size = static_cast<size_t>(dexStat.st_size);
    void *memory = mmap(nullptr, size, PROT_READ | PROT_WRITE,
                        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (memory == MAP_FAILED) return nullptr;

    size_t offset = 0;
    while (offset < size) {
        const ssize_t count = pread(dexFd, static_cast<char *>(memory) + offset,
                                    size - offset, static_cast<off_t>(offset));
        if (count <= 0) {
            munmap(memory, size);
            return nullptr;
        }
        offset += static_cast<size_t>(count);
    }
    if (mprotect(memory, size, PROT_READ) != 0) {
        munmap(memory, size);
        return nullptr;
    }
    *sizeOut = size;
    return memory;
}

jobject currentApplication(JNIEnv *env) {
    jclass activityThread = env->FindClass("android/app/ActivityThread");
    if (activityThread == nullptr || clearException(env, "find ActivityThread")) return nullptr;
    jmethodID method = env->GetStaticMethodID(
            activityThread, "currentApplication", "()Landroid/app/Application;");
    if (method == nullptr || clearException(env, "resolve currentApplication")) return nullptr;
    jobject application = env->CallStaticObjectMethod(activityThread, method);
    if (clearException(env, "call currentApplication")) return nullptr;
    return application;
}

std::string codeCachePath(JNIEnv *env, jobject application) {
    jclass contextClass = env->FindClass("android/content/Context");
    jmethodID getCodeCacheDir = env->GetMethodID(
            contextClass, "getCodeCacheDir", "()Ljava/io/File;");
    jobject file = env->CallObjectMethod(application, getCodeCacheDir);
    if (file == nullptr || clearException(env, "get code cache directory")) return {};

    jclass fileClass = env->FindClass("java/io/File");
    jmethodID getAbsolutePath = env->GetMethodID(
            fileClass, "getAbsolutePath", "()Ljava/lang/String;");
    auto path = static_cast<jstring>(env->CallObjectMethod(file, getAbsolutePath));
    if (path == nullptr || clearException(env, "get code cache path")) return {};
    const char *characters = env->GetStringUTFChars(path, nullptr);
    if (characters == nullptr) return {};
    std::string result(characters);
    env->ReleaseStringUTFChars(path, characters);
    return result;
}

jobject createModuleClassLoader(JNIEnv *env, jobject application,
                                void *dexMemory, size_t dexSize) {
    jclass contextClass = env->FindClass("android/content/Context");
    jmethodID getClassLoader = env->GetMethodID(
            contextClass, "getClassLoader", "()Ljava/lang/ClassLoader;");
    jobject parent = env->CallObjectMethod(application, getClassLoader);
    if (parent == nullptr || clearException(env, "get game class loader")) return nullptr;

    jobject byteBuffer = env->NewDirectByteBuffer(dexMemory, static_cast<jlong>(dexSize));
    if (byteBuffer == nullptr || clearException(env, "create DEX byte buffer")) return nullptr;

    jclass loaderClass = env->FindClass("dalvik/system/InMemoryDexClassLoader");
    if (loaderClass == nullptr || clearException(env, "find InMemoryDexClassLoader")) return nullptr;
    jmethodID constructor = env->GetMethodID(
            loaderClass, "<init>", "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V");
    if (constructor == nullptr || clearException(env, "resolve DEX loader constructor")) {
        return nullptr;
    }
    jobject loader = env->NewObject(loaderClass, constructor, byteBuffer, parent);
    if (loader == nullptr || clearException(env, "create module class loader")) return nullptr;

    jclass threadClass = env->FindClass("java/lang/Thread");
    jmethodID currentThread = env->GetStaticMethodID(
            threadClass, "currentThread", "()Ljava/lang/Thread;");
    jmethodID setContextClassLoader = env->GetMethodID(
            threadClass, "setContextClassLoader", "(Ljava/lang/ClassLoader;)V");
    jobject thread = env->CallStaticObjectMethod(threadClass, currentThread);
    env->CallVoidMethod(thread, setContextClassLoader, loader);
    if (clearException(env, "set thread class loader")) return nullptr;
    return loader;
}

jclass loadClass(JNIEnv *env, jobject loader, const char *name) {
    jclass classLoader = env->FindClass("java/lang/ClassLoader");
    jmethodID load = env->GetMethodID(
            classLoader, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring className = env->NewStringUTF(name);
    jobject loaded = env->CallObjectMethod(loader, load, className);
    if (loaded == nullptr || clearException(env, name)) return nullptr;
    return reinterpret_cast<jclass>(loaded);
}

struct WorkerArgs {
    JavaVM *vm;
    int dexFd;
    int nativeFd;
};

void *loadModuleWorker(void *opaque) {
    auto *args = static_cast<WorkerArgs *>(opaque);
    JNIEnv *env = nullptr;
    if (args->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
        ROOT_LOGE("Unable to attach standalone root runtime thread");
        close(args->dexFd);
        close(args->nativeFd);
        delete args;
        return nullptr;
    }

    jobject application = nullptr;
    for (int attempt = 0; attempt < 1500 && application == nullptr; ++attempt) {
        application = currentApplication(env);
        if (application == nullptr) usleep(20 * 1000);
    }
    if (application == nullptr) {
        ROOT_LOGE("Game Application did not become available");
        goto cleanup;
    }

    {
        std::string cache = codeCachePath(env, application);
        if (cache.empty()) goto cleanup;
        std::string runtimeDirectory = cache + "/moodtools-root-runtime";
        if (mkdir(runtimeDirectory.c_str(), S_IRWXU) != 0 && errno != EEXIST) {
            ROOT_LOGE("Cannot create runtime directory: %s", strerror(errno));
            goto cleanup;
        }
        chmod(runtimeDirectory.c_str(), S_IRWXU);
        std::string nativePath = runtimeDirectory + "/libmenu_native.so";
        if (!copyFdToPath(args->nativeFd, nativePath, kMaxNativeBytes)) goto cleanup;

        size_t dexSize = 0;
        void *dexMemory = mapDex(args->dexFd, &dexSize);
        if (dexMemory == nullptr) {
            ROOT_LOGE("Unable to map module DEX");
            goto cleanup;
        }
        jobject loader = createModuleClassLoader(env, application, dexMemory, dexSize);
        if (loader == nullptr) goto cleanup;
        env->NewGlobalRef(loader);

        jclass runtime = loadClass(env, loader, "com.android.support.ModuleRuntime");
        if (runtime == nullptr) goto cleanup;
        jmethodID loadNative = env->GetStaticMethodID(runtime, "loadNative", "(Ljava/lang/String;)V");
        jstring nativePathString = env->NewStringUTF(nativePath.c_str());
        env->CallStaticVoidMethod(runtime, loadNative, nativePathString);
        if (clearException(env, "load module native payload")) goto cleanup;

        jclass bootstrap = loadClass(env, loader, "com.android.support.RootBootstrap");
        if (bootstrap == nullptr) goto cleanup;
        jmethodID install = env->GetStaticMethodID(
                bootstrap, "install", "(Landroid/app/Application;)V");
        env->CallStaticVoidMethod(bootstrap, install, application);
        if (clearException(env, "install root Activity bootstrap")) goto cleanup;
        ROOT_LOGI("Standalone payload loaded in %s (PID %d)",
                  currentPackageName().c_str(), getpid());
    }

cleanup:
    close(args->dexFd);
    close(args->nativeFd);
    args->vm->DetachCurrentThread();
    delete args;
    return nullptr;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    if (vm == nullptr || reserved == nullptr) {
        ROOT_LOGE("Standalone root bootstrap did not receive a payload descriptor");
        return JNI_ERR;
    }
    const auto descriptor = *static_cast<const PayloadDescriptor *>(reserved);
    if (descriptor.magic != kPayloadMagic || descriptor.dexFd < 0 || descriptor.nativeFd < 0) {
        ROOT_LOGE("Standalone root bootstrap rejected an invalid payload descriptor");
        return JNI_ERR;
    }
    const int dexFd = dup(descriptor.dexFd);
    const int nativeFd = dup(descriptor.nativeFd);
    close(descriptor.dexFd);
    close(descriptor.nativeFd);
    if (dexFd < 0 || nativeFd < 0) {
        ROOT_LOGE("Unable to duplicate injected payload descriptors: %s", strerror(errno));
        if (dexFd >= 0) close(dexFd);
        if (nativeFd >= 0) close(nativeFd);
        return JNI_ERR;
    }

    auto *workerArgs = new WorkerArgs{vm, dexFd, nativeFd};
    pthread_t worker;
    if (pthread_create(&worker, nullptr, loadModuleWorker, workerArgs) != 0) {
        ROOT_LOGE("Unable to create standalone root runtime worker");
        close(dexFd);
        close(nativeFd);
        delete workerArgs;
        return JNI_ERR;
    }
    pthread_detach(worker);
    ROOT_LOGI("Standalone root bootstrap entered PID %d", getpid());
    return JNI_VERSION_1_6;
}
