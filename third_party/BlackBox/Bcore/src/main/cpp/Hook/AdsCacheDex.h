#pragma once

#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <limits.h>
#include <sys/stat.h>
#include <unistd.h>

namespace AdsCacheDex {
inline const char *skipDigits(const char *value) {
    const char *start = value;
    while (*value >= '0' && *value <= '9') ++value;
    return value == start ? nullptr : value;
}

inline bool numericJar(const char *path) {
    if (!path) return false;
    const char *name = strrchr(path, '/');
    const char *suffix = skipDigits(name ? name + 1 : path);
    return suffix && strcmp(suffix, ".jar") == 0;
}

inline bool supportedGuestCache(const char *path) {
    constexpr char host[] = "/com.moodtools.hub.nonroot/blackbox/data/user/";
    if (!path) return false;
    const char *cursor;
    if (strncmp(path, "/data/user/", 11) == 0) cursor = skipDigits(path + 11);
    else if (strncmp(path, "/data/data/", 11) == 0) cursor = path + 10;
    else return false;
    if (!cursor || strncmp(cursor, host, sizeof(host) - 1) != 0) return false;
    cursor = skipDigits(cursor + sizeof(host) - 1);
    if (!cursor) return false;
    // Both games use the legacy Google Ads numeric-JAR writer. Keep the scope explicit.
    const char *guests[] = {"/com.appsomniacs.da2/cache/", "/com.shinybox.smash/cache/"};
    for (const char *guest : guests) {
        const size_t length = strlen(guest);
        if (strncmp(cursor, guest, length) != 0) continue;
        const char *name = cursor + length;
        return strchr(name, '/') == nullptr && numericJar(name);
    }
    return false;
}

// Seal before writing, not after loading fails. An already-open writer remains usable.
// Existing populated code is deliberately not trusted or modified.
inline int sealEmptyWriter(int fd, int flags) {
    // File.createNewFile uses O_CREAT|O_EXCL and closes its descriptor before
    // FileOutputStream opens the real writer. Do not seal that preliminary open.
    if (fd < 0 || (flags & O_ACCMODE) == O_RDONLY || !(flags & O_TRUNC)) return 0;
    struct stat info{};
    if (fstat(fd, &info) != 0 || !S_ISREG(info.st_mode) || info.st_size != 0) return 0;
    return fchmod(fd, info.st_mode & ~(S_IWUSR | S_IWGRP | S_IWOTH)) == 0 ? 1 : -1;
}

inline int seal(int fd, const char *requestedPath, int flags, int api) {
    if (api < 34 || fd < 0 || (flags & O_ACCMODE) == O_RDONLY
        || !(flags & O_TRUNC) || !numericJar(requestedPath)) return 0;
    // Resolve the descriptor, so relative opens and symlinks cannot escape the scope.
    char link[64], actual[PATH_MAX];
    snprintf(link, sizeof(link), "/proc/self/fd/%d", fd);
    const ssize_t count = readlink(link, actual, sizeof(actual) - 1);
    if (count < 0) return 0;
    actual[count] = '\0';
    return supportedGuestCache(actual) ? sealEmptyWriter(fd, flags) : 0;
}
}
