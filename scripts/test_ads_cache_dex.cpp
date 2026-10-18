// Compile with the Android NDK and run on-device; no root is needed.
#include "../third_party/BlackBox/Bcore/src/main/cpp/Hook/AdsCacheDex.h"
#include <cassert>
#include <cstdlib>
#include <initializer_list>

int main() {
    using namespace AdsCacheDex;
    assert(supportedGuestCache("/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.appsomniacs.da2/cache/1651189566953.jar"));
    assert(supportedGuestCache("/data/user/10/com.moodtools.hub.nonroot/blackbox/data/user/2/com.appsomniacs.da2/cache/123.jar"));
    assert(supportedGuestCache("/data/data/com.moodtools.hub.nonroot/blackbox/data/user/0/com.appsomniacs.da2/cache/1651189566953.jar"));
    assert(supportedGuestCache("/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.shinybox.smash/cache/1644353911296.jar"));
    assert(supportedGuestCache("/data/user/10/com.moodtools.hub.nonroot/blackbox/data/user/2/com.shinybox.smash/cache/123.jar"));
    assert(supportedGuestCache("/data/data/com.moodtools.hub.nonroot/blackbox/data/user/0/com.shinybox.smash/cache/123.jar"));
    for (const char *path : {
        "/data/user/0/com.shinybox.smash/cache/123.jar",
        "/data/user/0/com.moodtools.hub.root/blackbox/data/user/0/com.shinybox.smash/cache/123.jar",
        "/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.shinybox.smash/files/123.jar",
        "/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.shinybox.smash/cache/sub/123.jar",
        "/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.shinybox.smash/cache/../123.jar",
        "/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.shinybox.smash/cache/account.jar",
        "/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.shinybox.smash/cache/123.jar.tmp",
        "/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.shinybox.smash.other/cache/123.jar",
        "/data/user/0/com.appsomniacs.da2/cache/123.jar",
        "/data/data/com.appsomniacs.da2/cache/123.jar",
        "/data/data/com.moodtools.hub.nonroot/blackbox/data/user/0/com.other.game/cache/123.jar",
        "/data/user/0/com.moodtools.hub.root/blackbox/data/user/0/com.appsomniacs.da2/cache/123.jar",
        "/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.other.game/cache/123.jar",
        "/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.appsomniacs.da2/files/123.jar",
        "/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.appsomniacs.da2/cache/sub/123.jar",
        "/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.appsomniacs.da2/cache/../123.jar",
        "/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.appsomniacs.da2/cache/account.jar",
        "/data/user/0/com.moodtools.hub.nonroot/blackbox/data/user/0/com.appsomniacs.da2/cache/123.jar.tmp"
    }) assert(!supportedGuestCache(path));
    assert(!supportedGuestCache(nullptr));
    char path[] = "/data/local/tmp/jester-ads-dex-XXXXXX";
    const int fd = mkstemp(path);
    assert(fd >= 0);
    assert(fchmod(fd, 0660) == 0);
    assert(seal(fd, "123.jar", O_CREAT | O_TRUNC | O_WRONLY, 33) == 0);
    assert(seal(fd, "123.jar", O_CREAT | O_TRUNC | O_WRONLY, 36) == 0); // Outside guest cache.
    assert(sealEmptyWriter(fd, O_RDONLY) == 0);
    assert(sealEmptyWriter(fd, O_WRONLY) == 0);
    struct stat info{};
    assert(sealEmptyWriter(fd, O_CREAT | O_EXCL | O_RDWR) == 0); // Preliminary createNewFile.
    assert(fstat(fd, &info) == 0 && (info.st_mode & 0222));
    assert(sealEmptyWriter(fd, O_CREAT | O_TRUNC | O_WRONLY) == 1);
    assert(fstat(fd, &info) == 0 && !(info.st_mode & 0222));
    assert(write(fd, "dex", 3) == 3); // Sealing must not break the active SDK writer.
    assert(fchmod(fd, 0660) == 0);
    assert(sealEmptyWriter(fd, O_CREAT | O_TRUNC | O_WRONLY) == 0); // Populated files untouched.
    assert(fstat(fd, &info) == 0 && (info.st_mode & 0222));
    close(fd);
    assert(unlink(path) == 0);
    puts("PASS: ads-cache scope, empty-file sealing and active writer semantics");
}
