



#include "FileSystemHook.h"
#include "Dobby/dobby.h"
#include "IO.h"
#include "Log.h"
#include "xdl.h"
#include <dirent.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdarg.h>
#include <cstring>
#include <errno.h>
#include <cstdio>


static int (*orig_open)(const char *pathname, int flags, ...) = nullptr;
static int (*orig_open64)(const char *pathname, int flags, ...) = nullptr;
static int (*orig_openat)(int dirfd, const char *pathname, int flags, ...) = nullptr;
static int (*orig_access)(const char *pathname, int mode) = nullptr;
static int (*orig_stat)(const char *pathname, struct stat *statbuf) = nullptr;
static int (*orig_lstat)(const char *pathname, struct stat *statbuf) = nullptr;
static int (*orig_mkdir)(const char *pathname, mode_t mode) = nullptr;
static int (*orig_unlink)(const char *pathname) = nullptr;
static int (*orig_remove)(const char *pathname) = nullptr;
static int (*orig_rename)(const char *oldpath, const char *newpath) = nullptr;
static DIR *(*orig_opendir)(const char *pathname) = nullptr;
static FILE *(*orig_fopen)(const char *pathname, const char *mode) = nullptr;
static FILE *(*orig_fopen64)(const char *pathname, const char *mode) = nullptr;

static bool hasModeArgument(int flags) {
    if ((flags & O_CREAT) != 0) {
        return true;
    }
#ifdef O_TMPFILE
    return (flags & O_TMPFILE) == O_TMPFILE;
#else
    return false;
#endif
}

static bool shouldBlock(const char *pathname) {
    return pathname != nullptr
           && (strstr(pathname, "resource-cache")
               || strstr(pathname, "@idmap")
               || strstr(pathname, ".frro")
               || strstr(pathname, "systemui")
               || strstr(pathname, "data@resource-cache@"));
}

static const char *redirect(const char *pathname) {
    return pathname == nullptr ? nullptr : IO::redirectPath(pathname);
}


int new_open(const char *pathname, int flags, ...) {
    if (shouldBlock(pathname)) {
        errno = ENOENT;
        return -1;
    }
    const char *redirected = redirect(pathname);
    if (!hasModeArgument(flags)) {
        return orig_open(redirected, flags);
    }
    va_list args;
    va_start(args, flags);
    mode_t mode = va_arg(args, mode_t);
    va_end(args);
    return orig_open(redirected, flags, mode);
}


int new_open64(const char *pathname, int flags, ...) {
    if (shouldBlock(pathname)) {
        errno = ENOENT;
        return -1;
    }
    const char *redirected = redirect(pathname);
    if (!hasModeArgument(flags)) {
        return orig_open64(redirected, flags);
    }
    va_list args;
    va_start(args, flags);
    mode_t mode = va_arg(args, mode_t);
    va_end(args);
    return orig_open64(redirected, flags, mode);
}

int new_openat(int dirfd, const char *pathname, int flags, ...) {
    if (shouldBlock(pathname)) {
        errno = ENOENT;
        return -1;
    }
    const char *redirected = redirect(pathname);
    if (!hasModeArgument(flags)) {
        return orig_openat(dirfd, redirected, flags);
    }
    va_list args;
    va_start(args, flags);
    mode_t mode = va_arg(args, mode_t);
    va_end(args);
    return orig_openat(dirfd, redirected, flags, mode);
}

int new_access(const char *pathname, int mode) {
    return orig_access(redirect(pathname), mode);
}

int new_stat(const char *pathname, struct stat *statbuf) {
    return orig_stat(redirect(pathname), statbuf);
}

int new_lstat(const char *pathname, struct stat *statbuf) {
    return orig_lstat(redirect(pathname), statbuf);
}

int new_mkdir(const char *pathname, mode_t mode) {
    return orig_mkdir(redirect(pathname), mode);
}

int new_unlink(const char *pathname) {
    return orig_unlink(redirect(pathname));
}

int new_remove(const char *pathname) {
    return orig_remove(redirect(pathname));
}

int new_rename(const char *oldpath, const char *newpath) {
    // Evaluate into separate buffers: IO's thread-local result is reused by the
    // second redirect call.
    const char *redirectedOld = redirect(oldpath);
    std::string oldCopy = redirectedOld == nullptr ? std::string() : redirectedOld;
    const char *redirectedNew = redirect(newpath);
    return orig_rename(oldpath == nullptr ? nullptr : oldCopy.c_str(), redirectedNew);
}

DIR *new_opendir(const char *pathname) {
    return orig_opendir(redirect(pathname));
}

FILE *new_fopen(const char *pathname, const char *mode) {
    return orig_fopen(redirect(pathname), mode);
}

FILE *new_fopen64(const char *pathname, const char *mode) {
    return orig_fopen64(redirect(pathname), mode);
}

template<typename Function>
static bool installHookAt(const char *name, void *target, void *replacement, Function *original) {
    if (target == nullptr) {
        ALOGD("FileSystemHook: %s is not exported", name);
        return false;
    }
    if (DobbyHook(target, replacement, reinterpret_cast<void **>(original)) != 0) {
        ALOGE("FileSystemHook: Failed to hook %s", name);
        return false;
    }
    return true;
}

template<typename Function>
static void installHook(void *handle, const char *name, void *replacement, Function *original) {
    installHookAt(name, xdl_sym(handle, name, nullptr), replacement, original);
}

void FileSystemHook::init() {
    ALOGD("FileSystemHook: Initializing file system hooks");
    
    
    void* handle = xdl_open("libc.so", XDL_DEFAULT);
    if (!handle) {
        ALOGE("FileSystemHook: Failed to open libc.so");
        return;
    }
    void *openTarget = xdl_sym(handle, "open", nullptr);
    installHookAt("open", openTarget, reinterpret_cast<void *>(new_open), &orig_open);
    void *open64Target = xdl_sym(handle, "open64", nullptr);
    if (open64Target == openTarget && orig_open != nullptr) {
        // Bionic aliases open64 to open on 64-bit Android. Hooking the same
        // instructions twice would replace Dobby's trampoline and recurse.
        orig_open64 = reinterpret_cast<decltype(orig_open64)>(orig_open);
    } else {
        installHookAt("open64", open64Target, reinterpret_cast<void *>(new_open64), &orig_open64);
    }
    installHook(handle, "openat", reinterpret_cast<void *>(new_openat), &orig_openat);
    installHook(handle, "access", reinterpret_cast<void *>(new_access), &orig_access);
    installHook(handle, "stat", reinterpret_cast<void *>(new_stat), &orig_stat);
    installHook(handle, "lstat", reinterpret_cast<void *>(new_lstat), &orig_lstat);
    installHook(handle, "mkdir", reinterpret_cast<void *>(new_mkdir), &orig_mkdir);
    installHook(handle, "unlink", reinterpret_cast<void *>(new_unlink), &orig_unlink);
    installHook(handle, "remove", reinterpret_cast<void *>(new_remove), &orig_remove);
    installHook(handle, "rename", reinterpret_cast<void *>(new_rename), &orig_rename);
    installHook(handle, "opendir", reinterpret_cast<void *>(new_opendir), &orig_opendir);
    void *fopenTarget = xdl_sym(handle, "fopen", nullptr);
    installHookAt("fopen", fopenTarget, reinterpret_cast<void *>(new_fopen), &orig_fopen);
    void *fopen64Target = xdl_sym(handle, "fopen64", nullptr);
    if (fopen64Target == fopenTarget && orig_fopen != nullptr) {
        orig_fopen64 = reinterpret_cast<decltype(orig_fopen64)>(orig_fopen);
    } else {
        installHookAt("fopen64", fopen64Target, reinterpret_cast<void *>(new_fopen64), &orig_fopen64);
    }

    xdl_close(handle);
}
