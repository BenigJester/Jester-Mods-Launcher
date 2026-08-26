#pragma once

#include <atomic>
#include <cinttypes>
#include <cstdio>
#include <cstring>
#include <elf.h>
#include <mutex>
#include <sys/system_properties.h>
#include <sys/utsname.h>
#include <thread>
#include <unistd.h>

#include "KittyMemory/KittyScanner.hpp"
#include "xDL/xdl.h"

namespace EarlyLoadObserver {

using ReadyCallback = void (*)();
using TimeoutCallback = void (*)();

inline std::atomic<bool> started{false};
inline std::atomic<bool> notified{false};
inline const char *target = nullptr;
inline ReadyCallback ready = nullptr;
inline TimeoutCallback timedOut = nullptr;
inline std::atomic<int> nativeBridgeRuntime{-1};
inline std::mutex elfScannerMutex;

inline bool IsNativeBridgeRuntime() {
#if !defined(__aarch64__)
    return false;
#else
    int cached = nativeBridgeRuntime.load(std::memory_order_acquire);
    if (cached >= 0) return cached != 0;

    bool detected = false;
    char systemPrimaryAbi[PROP_VALUE_MAX] = {0};
    if (__system_property_get("ro.product.cpu.abi", systemPrimaryAbi) > 0) {
        detected = std::strstr(systemPrimaryAbi, "x86") != nullptr ||
                   std::strstr(systemPrimaryAbi, "amd64") != nullptr ||
                   std::strstr(systemPrimaryAbi, "i686") != nullptr;
    }

    utsname kernelInfo{};
    if (!detected && uname(&kernelInfo) == 0) {
        // An ARM64 module on an x86 kernel is necessarily running through Houdini or
        // NDK Translation, even when the translated target has an ordinary named map.
        detected = std::strstr(kernelInfo.machine, "x86") != nullptr ||
                   std::strstr(kernelInfo.machine, "amd64") != nullptr ||
                   std::strstr(kernelInfo.machine, "i686") != nullptr;
    }

    if (!detected) {
        FILE *maps = std::fopen("/proc/self/maps", "r");
        if (maps != nullptr) {
            char line[768];
            while (std::fgets(line, sizeof(line), maps) != nullptr) {
                if (std::strstr(line, "libhoudini") != nullptr ||
                    std::strstr(line, "libndk_translation") != nullptr) {
                    detected = true;
                    break;
                }
            }
            std::fclose(maps);
        }
    }

    int expected = -1;
    nativeBridgeRuntime.compare_exchange_strong(
            expected, detected ? 1 : 0, std::memory_order_acq_rel);
    return nativeBridgeRuntime.load(std::memory_order_acquire) != 0;
#endif
}

inline uintptr_t ResolveLibraryBase(const char *name) {
    if (name == nullptr || name[0] == '\0') return 0;

    // Prefer the ELF load bias represented by an offset-zero map. Unlike xDL,
    // this remains available while a native bridge is translating the image.
    FILE *maps = std::fopen("/proc/self/maps", "r");
    if (maps == nullptr) return 0;
    char line[1024];
    uintptr_t base = 0;
    while (std::fgets(line, sizeof(line), maps) != nullptr) {
        uintptr_t start = 0;
        uintptr_t end = 0;
        uintptr_t fileOffset = 0;
        char permissions[5] = {};
        char mappedPath[768] = {};
        const int parsed = std::sscanf(
                line,
                "%" SCNxPTR "-%" SCNxPTR " %4s %" SCNxPTR " %*s %*s %767s",
                &start,
                &end,
                permissions,
                &fileOffset,
                mappedPath);
        if (parsed == 5 && fileOffset == 0 &&
            std::strstr(mappedPath, name) != nullptr) {
            base = start;
            break;
        }
    }
    std::fclose(maps);
    if (base != 0) return base;

    // Native polling starts while Android is still creating application mappings. Scanning every
    // app ELF in that window can race mmap/munmap and dereference a stale ProcMap. Native installs
    // expose the target through the named-map path above, so retry safely on the next poll.
    // Translated runtimes still need the scanner for APK-backed/non-zero-offset ARM mappings.
    if (!IsNativeBridgeRuntime()) return 0;

    std::lock_guard<std::mutex> lock(elfScannerMutex);
    const auto targetElf = KittyScanner::ElfScanner::findElf(
            name,
            KittyScanner::EScanElfType::Any,
            KittyScanner::EScanElfFilter::App);
    return targetElf.isValid() ? targetElf.loadBias() : 0;
}

inline bool IsMapped(const char *name) {
    return ResolveLibraryBase(name) != 0;
}

inline bool IsExecutableLibraryAddress(uintptr_t address, const char *name) {
    if (address == 0 || name == nullptr) return false;

    FILE *maps = std::fopen("/proc/self/maps", "r");
    if (maps != nullptr) {
        char line[1024];
        while (std::fgets(line, sizeof(line), maps) != nullptr) {
            uintptr_t start = 0;
            uintptr_t end = 0;
            char permissions[5] = {};
            char mappedPath[768] = {};
            const int parsed = std::sscanf(
                    line,
                    "%" SCNxPTR "-%" SCNxPTR " %4s %*s %*s %*s %767s",
                    &start,
                    &end,
                    permissions,
                    mappedPath);
            if (parsed == 4 && address >= start && address < end &&
                std::strchr(permissions, 'x') != nullptr &&
                std::strstr(mappedPath, name) != nullptr) {
                std::fclose(maps);
                return true;
            }
        }
        std::fclose(maps);
    }

    // Never enter the process-wide ELF scanner from the native loader path. If the named
    // executable mapping is not stable yet, let the observer retry on its next poll.
    if (!IsNativeBridgeRuntime()) return false;

    // Houdini/NDK Translation may expose the ARM segment as non-executable in the host map.
    // Validate its executable range from the guest ELF instead.
    std::lock_guard<std::mutex> lock(elfScannerMutex);
    const auto targetElf = KittyScanner::ElfScanner::findElf(
            name,
            KittyScanner::EScanElfType::Any,
            KittyScanner::EScanElfFilter::App);
    if (!targetElf.isValid()) return false;
    for (const auto &segment : targetElf.segments()) {
        if (segment.executable && address >= segment.startAddress &&
            address < segment.endAddress) {
            return true;
        }
    }
    if (!targetElf.isEmulated() && !IsNativeBridgeRuntime()) return false;
    for (const auto &programHeader : targetElf.programHeaders()) {
        if (programHeader.p_type != PT_LOAD || (programHeader.p_flags & PF_X) == 0) continue;
        const uintptr_t start = targetElf.loadBias() + programHeader.p_vaddr;
        const uintptr_t end = start + programHeader.p_memsz;
        if (address >= start && address < end) return true;
    }
    return false;
}

inline void NotifyIfReady() {
    if (target == nullptr || ready == nullptr || !IsMapped(target)) return;
    // A mapped library can still be inside its loader/initialization transaction. Let that
    // transaction settle before game-specific hooks touch the newly loaded image.
    constexpr int kNativeReadyStabilizationMicroseconds = 250 * 1000;
    constexpr int kNativeBridgeReadyStabilizationMicroseconds = 300 * 1000;
    usleep(IsNativeBridgeRuntime()
           ? kNativeBridgeReadyStabilizationMicroseconds
           : kNativeReadyStabilizationMicroseconds);
    if (!IsMapped(target)) return;
    bool expected = false;
    if (notified.compare_exchange_strong(expected, true, std::memory_order_acq_rel)) {
        ready();
    }
}

inline void Start(const char *name, ReadyCallback readyCallback,
                  TimeoutCallback timeoutCallback = nullptr) {
    bool expected = false;
    if (!started.compare_exchange_strong(expected, true, std::memory_order_acq_rel)) return;

    target = name;
    ready = readyCallback;
    timedOut = timeoutCallback;

    std::thread([] {
        constexpr int kPollIntervalMicroseconds = 50 * 1000;
        constexpr int kMaximumPolls = 30 * 1000 / 50;
        for (int poll = 0; poll < kMaximumPolls &&
                           !notified.load(std::memory_order_acquire); ++poll) {
            NotifyIfReady();
            if (notified.load(std::memory_order_acquire)) return;
            usleep(kPollIntervalMicroseconds);
        }
        if (!notified.load(std::memory_order_acquire) && timedOut != nullptr) {
            started.store(false, std::memory_order_release);
            timedOut();
        }
    }).detach();
}

} // namespace EarlyLoadObserver
