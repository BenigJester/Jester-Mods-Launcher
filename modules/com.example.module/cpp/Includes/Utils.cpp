#include "obfuscate.h"
#include "Utils.hpp"
#include "EarlyLoadObserver.hpp"

#include <mutex>

std::map<std::string, uintptr_t> lib_links;
namespace {
std::mutex libLinksMutex;
}

uintptr_t getLibraryAddress(const char *libraryName) {
    if (libraryName == nullptr || libraryName[0] == '\0') return 0;
    std::lock_guard<std::mutex> lock(libLinksMutex);
    const auto cached = lib_links.find(libraryName);
    if (cached != lib_links.end() && cached->second != 0) return cached->second;

    const uintptr_t base = EarlyLoadObserver::ResolveLibraryBase(libraryName);
    // Never cache a failed early lookup. Native-bridge mappings can become
    // resolvable a few milliseconds later while the target is still loading.
    if (base != 0) lib_links[libraryName] = base;
    return base;
}

void* getSymAddress(const char *libraryName, const char *SymName, bool relative) {
    xdl_info_t info;
    void *handle = xdl_open(libraryName, XDL_DEFAULT);
    if (handle == nullptr) {
        LOGE(OBFUSCATE("xdl_open failed for %s"), libraryName);
        return nullptr;
    }

    memset(&info, 0, sizeof(xdl_info_t));
    if (0 > xdl_info(handle, XDL_DI_DLINFO, &info)) {
        LOGE(OBFUSCATE(">>> getsym_xdl_info(XDL_DI_DLINFO, %llx, %s" ") : FAILED"), (uintptr_t) handle, SymName);
    }

    void *symbol_addr = xdl_sym(handle, SymName, nullptr); // lookup "dynamic link symbols" in .dynsym

    if (symbol_addr == nullptr) {
        LOGW(OBFUSCATE(">>> !xdl_sym -> xdl_dsym..."));
        symbol_addr = xdl_dsym(handle, SymName, nullptr); // lookup "debugging symbols" in .symtab and ".symtab in .gnu_debugdata
    }

    xdl_close(handle);

    if (relative) {
        return (void*)((uintptr_t) symbol_addr - (uintptr_t) info.dli_fbase);
    } else return symbol_addr;
}

void* getAbsAddress(const char *libraryName, uintptr_t relativeAddr) {
    const uintptr_t base = getLibraryAddress(libraryName);
    return base != 0 ? reinterpret_cast<void *>(base + relativeAddr) : nullptr;
}

void* getRelativeAddress(const char *libraryName, const char *rootOffset, const char *addOffset) {
    uintptr_t offset = str2offset(rootOffset);
    uintptr_t offset2 = str2offset(addOffset);

    if(offset != 0) {
        return getAbsAddress(libraryName, offset + offset2);
    } else {
        return getSymAddress(libraryName, rootOffset, true);
    }
}

void* getAbsoluteAddress(const char *libraryName, const char *relative) {
    uintptr_t offset = str2offset(relative);

    if(offset != 0) {
        return getAbsAddress(libraryName, offset);
    } else {
        return getSymAddress(libraryName, relative, false);
        // ElfScanner is still available... you can use it for advanced searches
    }
}

bool isLibraryLoaded(const char *libraryName) {
    char line[512] = {0};
    FILE *fp = fopen(OBFUSCATE("/proc/self/maps"), OBFUSCATE("rt"));
    if (fp != nullptr) {
        while (fgets(line, sizeof(line), fp)) {
            std::string a = line;
            if (strstr(line, libraryName)) {
                // LOGI(OBFUSCATE("main library (%s) loaded: 0x%llx"), libraryName, getLibraryAddress(libraryName));
                fclose(fp);
                return true;
            }
        }
        fclose(fp);
    }
    return false;
}

uintptr_t str2offset(const char *c) {
    int base = 16;
    // See if this function catches all possibilities.
    // If it doesn't, the function would have to be amended
    // whenever you add a combination of architecture and
    // compiler that is not yet addressed.
    static_assert(sizeof(uintptr_t) == sizeof(unsigned long)
                  || sizeof(uintptr_t) == sizeof(unsigned long long));

    // Now choose the correct function ...
    if (sizeof(uintptr_t) == sizeof(unsigned long)) {
        return strtoul(c, nullptr, base);
    }

    // All other options exhausted, sizeof(uintptr_t) == sizeof(unsigned long long))
    return strtoull(c, nullptr, base);
}
