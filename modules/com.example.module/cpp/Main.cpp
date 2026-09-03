#include <algorithm>
#include <atomic>
#include <cstring>
#include <cstdint>
#include <elf.h>
#include <initializer_list>
#include <map>
#include <string>
#include <thread>
#include <vector>

#include <jni.h>
#include <unistd.h>

#include "Includes/Logger.h"
#include "Includes/Utils.hpp"
#include "Includes/Macros.h"
#include "Includes/obfuscate.h"
#include "Includes/EarlyLoadObserver.hpp"
#include "Menu/Jni.hpp"
#include "Menu/Menu.hpp"

// Replace this with the native library used by the target game.
#define targetLibName OBFUSCATE("libil2cpp.so")

// Safety switch: the template builds and displays every control, but it never touches a game's
// native code until the developer replaces the placeholder RVAs and intentionally enables this.
constexpr bool kNativeExamplesConfigured = false;

// Keep package-identity startup patches behind a second explicit switch. Root/BlackBox injection
// uses the original package identity and must never be blocked by an outdated shell-only scan.
constexpr bool kPackageIdentityExamplesConfigured = false;

enum class CompatibilityState : int {
    Waiting = 0,
    Installing,
    Ready,
    ReadyWithLimits,
    TargetNotFound,
    MapFailed,
    HookFailed,
    UnsupportedAbi,
};

// Record why a fatal startup prerequisite failed instead of collapsing target discovery and
// memory-write rejection into the same vague message.
enum class CompatibilityFailure : int {
    None = 0,
    TargetProfileMismatch,
    StartupPatchRejected,
};

// Generic MultiSelectSpinner callback contract. The example descriptor has three selectable
// entries after its leading "All Options" row, so its decoded native mask occupies bits 0..2.
constexpr int kExampleMultiSelectAllMask = (1 << 3) - 1;

// Add explicit feature IDs here to omit those controls from a shared build.
// Both positive and negative IDs are supported. Keep this list empty to expose
// the complete catalog. Example: {3, 4, 30, 31, 32, -50}.
constexpr std::initializer_list<int> kHiddenFeatureIds = {
};

void hack_thread();
void compatibility_wait_timeout();
void compatibility_install_watchdog();
static std::atomic<bool> gNativeInstallStarted{false};
static std::atomic<CompatibilityState> gCompatibilityState{CompatibilityState::Waiting};
static std::atomic<CompatibilityFailure> gCompatibilityFailure{CompatibilityFailure::None};
static std::atomic<int> gRuntimeMethod{0};
static std::atomic<bool> gHookExampleAvailable{true};
static std::atomic<bool> gDirectCallExampleAvailable{true};
static std::atomic<bool> gInstallWatchdogRecovered{false};

bool StartNativeRuntime(int method) {
    constexpr int kInjectionMethod = 1;
    constexpr int kDirectPatchMethod = 2;
    constexpr int kIdentityShellMethod = 3;
    constexpr int kIdentityShellCompatibilityMethod = 4;
    if (method != kInjectionMethod && method != kDirectPatchMethod &&
        method != kIdentityShellMethod && method != kIdentityShellCompatibilityMethod) {
        LOGE(OBFUSCATE("Rejected unknown native runtime method: %d"), method);
        return false;
    }

    int expected = 0;
    if (!gRuntimeMethod.compare_exchange_strong(expected, method,
                                                std::memory_order_acq_rel)) {
        if (expected != method) {
            LOGE(OBFUSCATE("Rejected native runtime method change: %d -> %d"),
                 expected, method);
            return false;
        }
        return true;
    }

    const char *runtimeName = method == kDirectPatchMethod ? "direct-patch" :
                              method == kIdentityShellMethod ? "identity-shell" :
                              method == kIdentityShellCompatibilityMethod ?
                                  "identity-shell-compatibility" :
                              "injection";
    LOGI(OBFUSCATE("Starting native runtime in %s mode"), runtimeName);
    if (!kNativeExamplesConfigured) {
        gCompatibilityState.store(CompatibilityState::Ready, std::memory_order_release);
        LOGI(OBFUSCATE("Template native examples disabled; skipping early native observer"));
        return true;
    }
    EarlyLoadObserver::Start(targetLibName, hack_thread, compatibility_wait_timeout);
    return true;
}

bool RequiresPackageIdentityBypass() {
    constexpr int kDirectPatchMethod = 2;
    constexpr int kIdentityShellMethod = 3;
    constexpr int kIdentityShellCompatibilityMethod = 4;
    const int method = gRuntimeMethod.load(std::memory_order_acquire);
    return method == kDirectPatchMethod || method == kIdentityShellMethod ||
           method == kIdentityShellCompatibilityMethod;
}

bool IsIdentityShellCompatibilityOnlyRuntime() {
    constexpr int kIdentityShellCompatibilityMethod = 4;
    return gRuntimeMethod.load(std::memory_order_acquire) ==
           kIdentityShellCompatibilityMethod;
}

// Initialized by Menu/Setup.cpp during JNI_OnLoad and available to future JNI bridge examples.
JavaVM *gJavaVm = nullptr;
jclass gMainClass = nullptr;

struct TemplateState {
    std::atomic<bool> basicToggle{false};
    std::atomic<bool> defaultOnToggle{true};
    std::atomic<bool> testingToggle{false};
    std::atomic<bool> defaultOnTestingToggle{true};
    std::atomic<bool> buttonOnOff{false};
    std::atomic<bool> checkBox{false};
    std::atomic<bool> patchEnabled{false};
    std::atomic<bool> hookEnabled{false};
    std::atomic<int> seekBarValue{0};
    std::atomic<int> spinnerIndex{0};
    std::atomic<int> multiSelectMask{kExampleMultiSelectAllMask};
    std::atomic<int> radioIndex{0};
    std::atomic<int> integerValue{0};
    std::atomic<long long> longValue{0};
    std::string textValue;
    std::string floatText{"0.0"};
} state;

std::string JStringToUtf8(JNIEnv *env, jstring value) {
    if (env == nullptr || value == nullptr) {
        return {};
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

// ---------------------------------------------------------------------------------------------
// Native examples
// ---------------------------------------------------------------------------------------------
// Replace every placeholder RVA with an RVA verified against the exact target version and ABI.
// Never copy an address from another game. A hook signature must match the native function,
// including the hidden MethodInfo argument used by many IL2CPP methods.

using ExampleFunction = int (*)(void *instance, int value, void *methodInfo);
ExampleFunction originalExampleFunction = nullptr;

int HookWithOriginalExample(void *instance, int value, void *methodInfo) {
    // Argument modification example.
    int forwardedValue = state.hookEnabled.load(std::memory_order_relaxed) ? value * 2 : value;

    // Preserve native behavior by calling the trampoline.
    int nativeResult = originalExampleFunction != nullptr
                       ? originalExampleFunction(instance, forwardedValue, methodInfo)
                       : forwardedValue;

    // Return-value modification example.
    return state.hookEnabled.load(std::memory_order_relaxed) ? nativeResult + 1 : nativeResult;
}

int HookWithoutOriginalExample(void *instance, int value, void *methodInfo) {
    (void) instance;
    (void) value;
    (void) methodInfo;
    // Full replacement example. Use HOOK_NO_ORIG only when skipping native behavior is intended.
    return 1;
}

using DirectFunction = void (*)(void *instance, int value, void *methodInfo);
DirectFunction directFunctionExample = nullptr;

template<typename T>
T ReadField(void *instance, uintptr_t offset, T fallback = {}) {
    if (instance == nullptr) {
        return fallback;
    }
    return *reinterpret_cast<T *>(reinterpret_cast<uintptr_t>(instance) + offset);
}

template<typename T>
void WriteField(void *instance, uintptr_t offset, const T &value) {
    if (instance != nullptr) {
        *reinterpret_cast<T *>(reinterpret_cast<uintptr_t>(instance) + offset) = value;
    }
}

// ---------------------------------------------------------------------------------------------
// Resilient target discovery and verified startup patch example
// ---------------------------------------------------------------------------------------------
// These values are deliberately inert placeholders. Replace every pattern, instruction offset,
// expected byte sequence, patch byte sequence, and profile RVA from the exact supported binary
// before setting kPackageIdentityExamplesConfigured to true.
constexpr const char *kStartupCheckStrictPattern = "DE AD BE EF 11 22 33 44";
constexpr const char *kStartupCheckRelaxedPattern = "DE AD ? ? 11 22 ? ?";
constexpr const char *kStartupCallerPattern = "AA BB CC DD ? ? ? 94";
constexpr size_t kStartupCallerBlOffset = 4;
constexpr uintptr_t kVerifiedStartupProfileRva = 0;
constexpr uint8_t kExpectedStartupPrologue[] = {
        0xDE, 0xAD, 0xBE, 0xEF, 0x11, 0x22, 0x33, 0x44};
constexpr uint8_t kStartupPassPatch[] = {
        0x20, 0x00, 0x80, 0x52, 0xC0, 0x03, 0x5F, 0xD6}; // mov w0, #1; ret

void AppendUniqueMatches(std::vector<uintptr_t> *destination,
                         const std::vector<uintptr_t> &source) {
    if (destination == nullptr) return;
    destination->insert(destination->end(), source.begin(), source.end());
    std::sort(destination->begin(), destination->end());
    destination->erase(
            std::unique(destination->begin(), destination->end()), destination->end());
}

std::vector<uintptr_t> FindTargetExecutablePattern(const char *pattern) {
    std::vector<uintptr_t> matches;

    // Scan every readable executable mapping carrying the target name. Vendor loaders may split
    // an APK-backed library into several mappings instead of one conventional contiguous range.
    const auto maps = KittyMemory::getMaps(
            KittyMemory::EProcMapFilter::Contains, targetLibName);
    for (const auto &map : maps) {
        if (!map.readable || !map.executable || map.length == 0) continue;
        AppendUniqueMatches(&matches, KittyScanner::findIdaPatternAll(
                map.startAddress, map.endAddress, pattern));
    }

    // Also scan executable ELF PT_LOAD segments. This covers native-bridge or anonymous mappings
    // whose /proc/self/maps pathname no longer contains the original library name.
    const auto targetElf = KittyScanner::ElfScanner::findElf(
            targetLibName, KittyScanner::EScanElfType::Any,
            KittyScanner::EScanElfFilter::Any);
    if (targetElf.isValid()) {
        for (const auto &programHeader : targetElf.programHeaders()) {
            if (programHeader.p_type != PT_LOAD ||
                (programHeader.p_flags & PF_X) == 0 || programHeader.p_filesz == 0) continue;
            const uintptr_t start = targetElf.loadBias() + programHeader.p_vaddr;
            const uintptr_t requestedEnd = start + programHeader.p_filesz;
            const auto addressMap = KittyMemory::getAddressMap(start);
            if (!addressMap.isValid() || !addressMap.readable || !addressMap.executable) continue;
            const uintptr_t safeEnd = std::min(requestedEnd, addressMap.endAddress);
            if (safeEnd <= start) continue;
            AppendUniqueMatches(&matches, KittyScanner::findIdaPatternAll(
                    start, safeEnd, pattern));
        }
    }
    return matches;
}

bool IsExecutableTargetAddress(uintptr_t address, size_t byteCount) {
    if (address == 0 || byteCount == 0 ||
        (address & (alignof(uint32_t) - 1)) != 0) return false;
    const auto map = KittyMemory::getAddressMap(address);
    return map.isValid() && map.readable && map.executable &&
           address <= map.endAddress && byteCount <= map.endAddress - address;
}

template <size_t N>
bool AddressMatches(uintptr_t address, const uint8_t (&expected)[N]) {
    uint8_t actual[N]{};
    return IsExecutableTargetAddress(address, N) &&
           KittyMemory::memRead(reinterpret_cast<const void *>(address), actual, N) &&
           std::memcmp(actual, expected, N) == 0;
}

uintptr_t ResolveArm64BlTarget(uintptr_t instructionAddress, uint32_t instruction) {
    if ((instruction & 0xFC000000u) != 0x94000000u) return 0;
    int64_t immediate = static_cast<int64_t>(instruction & 0x03FFFFFFu);
    if ((immediate & 0x02000000LL) != 0) immediate |= ~0x03FFFFFFLL;
    return static_cast<uintptr_t>(
            static_cast<int64_t>(instructionAddress) + (immediate << 2));
}

uintptr_t FindVerifiedStartupPatchTarget() {
    // Discovery ladder: exact AOB first, relaxed AOB second, then a structurally related BL
    // caller, and finally an RVA whose original bytes are verified. Never patch the first loose
    // match or use an unchecked fixed offset.
    auto matches = FindTargetExecutablePattern(kStartupCheckStrictPattern);
    if (matches.size() == 1 && AddressMatches(matches.front(), kExpectedStartupPrologue)) {
        return matches.front();
    }

    matches = FindTargetExecutablePattern(kStartupCheckRelaxedPattern);
    if (matches.size() == 1 && AddressMatches(matches.front(), kExpectedStartupPrologue)) {
        return matches.front();
    }

    const auto callers = FindTargetExecutablePattern(kStartupCallerPattern);
    if (callers.size() == 1) {
        const uintptr_t callAddress = callers.front() + kStartupCallerBlOffset;
        uint32_t instruction = 0;
        if (IsExecutableTargetAddress(callAddress, sizeof(instruction)) &&
            KittyMemory::memRead(reinterpret_cast<const void *>(callAddress),
                                 &instruction, sizeof(instruction))) {
            const uintptr_t target = ResolveArm64BlTarget(callAddress, instruction);
            if (AddressMatches(target, kExpectedStartupPrologue)) return target;
        }
    }

    if (kVerifiedStartupProfileRva != 0) {
        const uintptr_t libraryBase = getLibraryAddress(targetLibName);
        const uintptr_t profileTarget = libraryBase == 0
                                        ? 0 : libraryBase + kVerifiedStartupProfileRva;
        if (AddressMatches(profileTarget, kExpectedStartupPrologue)) return profileTarget;
    }

    LOGE(OBFUSCATE("Required startup target did not match a verified profile"));
    return 0;
}

template <size_t N>
bool ApplyPatchAndVerify(MemoryPatch *patch, const uint8_t (&expected)[N]) {
    if (patch == nullptr || !patch->isValid() || !patch->Modify()) return false;
    uint8_t actual[N]{};
    const bool verified = KittyMemory::memRead(
                                  reinterpret_cast<const void *>(patch->get_TargetAddress()),
                                  actual, N) &&
                          std::memcmp(actual, expected, N) == 0;
    if (!verified) {
        // A successful API return is not enough on every device. Roll back a partial or rejected
        // critical write so the original process is left in a predictable state.
        patch->Restore();
    }
    return verified;
}

bool InstallPackageIdentityCompatibilityExamples() {
    if (!RequiresPackageIdentityBypass() || !kPackageIdentityExamplesConfigured) return true;

#if defined(__aarch64__)
    const uintptr_t startupTarget = FindVerifiedStartupPatchTarget();
    if (startupTarget == 0) {
        gCompatibilityFailure.store(
                CompatibilityFailure::TargetProfileMismatch, std::memory_order_release);
        return false;
    }

    MemoryPatch startupPatch = MemoryPatch::createWithHex(
            startupTarget, OBFUSCATE("20008052C0035FD6"));
    if (!ApplyPatchAndVerify(&startupPatch, kStartupPassPatch)) {
        gCompatibilityFailure.store(
                CompatibilityFailure::StartupPatchRejected, std::memory_order_release);
        return false;
    }
    return true;
#else
    LOGE(OBFUSCATE("Package-identity compatibility example has no verified ARM32 profile"));
    return false;
#endif
}

bool InstallNativeExamples() {
    if (!kNativeExamplesConfigured) {
        LOGI(OBFUSCATE("Template native examples are disabled"));
        return true;
    }

#if defined(__aarch64__)
    const int failuresBefore = gDobbyHookFailureCount.load(std::memory_order_acquire);

    // Hook with a callable original trampoline.
    HOOK(targetLibName, "0x123456", HookWithOriginalExample, originalExampleFunction);
    const bool hookReady = originalExampleFunction != nullptr &&
                           gDobbyHookFailureCount.load(std::memory_order_acquire) ==
                                   failuresBefore;
    gHookExampleAvailable.store(hookReady, std::memory_order_release);

    // Hook without an original trampoline:
    // HOOK_NO_ORIG(targetLibName, "0x234568", HookWithoutOriginalExample);

    // Resolve a method for a later direct call:
    directFunctionExample = reinterpret_cast<DirectFunction>(
            getAbsoluteAddress(targetLibName, OBFUSCATE("0x345678")));
    const bool directCallReady = directFunctionExample != nullptr;
    gDirectCallExampleAvailable.store(directCallReady, std::memory_order_release);

    // Both examples are optional feature groups. A failure returns partial-ready rather than
    // disabling unrelated working controls. Reserve fatal states for prerequisites without which
    // the game or module cannot safely continue.
    return hookReady && directCallReady;
#else
    LOGI(OBFUSCATE("Add separately verified ARM32 examples before enabling native changes"));
    return false;
#endif
}

void ApplyPatchExample(JNIEnv *env, jobject context, bool enabled) {
    state.patchEnabled.store(enabled, std::memory_order_relaxed);

    if (!kNativeExamplesConfigured) {
        Toast(env, context,
              OBFUSCATE("Template patch is disabled. Replace the placeholder RVA first."),
              ToastLength::LENGTH_LONG);
        return;
    }

#if defined(__aarch64__)
    // ARM64 example: mov w0, #1; ret
    PATCH_SWITCH(targetLibName, "0x456780", "20 00 80 52 C0 03 5F D6", enabled);

    // Other common ARM64 examples:
    // Return false: 00 00 80 52 C0 03 5F D6
    // Return void:  C0 03 5F D6
    // NOP:          1F 20 03 D5
    // Interior patches must replace complete 4-byte-aligned instructions.
#else
    Toast(env, context, OBFUSCATE("No ARM32 patch example is configured."),
          ToastLength::LENGTH_SHORT);
#endif
}

void hack_thread() {
    LOGI(OBFUSCATE("Mod Menu Template native thread started"));

    if (!EarlyLoadObserver::IsMapped(targetLibName)) return;
    bool expected = false;
    if (!gNativeInstallStarted.compare_exchange_strong(expected, true)) return;
    gCompatibilityState.store(CompatibilityState::Installing, std::memory_order_release);
    compatibility_install_watchdog();

    // Install required game-specific package/signature compatibility patches before this branch.
    // Failure is fatal only for runtime methods that actually need those prerequisites.
    if (!InstallPackageIdentityCompatibilityExamples()) {
        gCompatibilityState.store(CompatibilityState::MapFailed, std::memory_order_release);
        LOGE(OBFUSCATE("Required package-identity compatibility setup failed"));
        return;
    }

    // An external shell launch needs those patches to run the untouched game, but must never
    // install the menu or feature hooks that follow.
    if (IsIdentityShellCompatibilityOnlyRuntime()) {
        gCompatibilityState.store(CompatibilityState::Ready, std::memory_order_release);
        LOGI(OBFUSCATE("External identity-shell launch: menu and feature hooks disabled"));
        return;
    }

#if defined(__aarch64__)
    gCompatibilityState.store(
            InstallNativeExamples() ? CompatibilityState::Ready
                                    : CompatibilityState::ReadyWithLimits,
            std::memory_order_release);
#else
    if (kNativeExamplesConfigured) {
        gCompatibilityState.store(CompatibilityState::UnsupportedAbi,
                                  std::memory_order_release);
        InstallNativeExamples();
    } else {
        gCompatibilityState.store(CompatibilityState::Ready, std::memory_order_release);
    }
#endif
    LOGI(OBFUSCATE("Mod Menu Template native setup finished"));
}

void compatibility_install_watchdog() {
    std::thread([] {
        constexpr int kInstallWatchdogMicroseconds = 15 * 1000 * 1000;
        usleep(kInstallWatchdogMicroseconds);

        CompatibilityState expected = CompatibilityState::Installing;
        if (EarlyLoadObserver::IsMapped(targetLibName) &&
            gCompatibilityState.compare_exchange_strong(
                    expected, CompatibilityState::ReadyWithLimits,
                    std::memory_order_acq_rel)) {
            gInstallWatchdogRecovered.store(true, std::memory_order_release);
            const char *libraryName = targetLibName;
            LOGW(OBFUSCATE("%s native install status stayed pending; marking compatibility limited"),
                 libraryName);
        }
    }).detach();
}

void compatibility_wait_timeout() {
    CompatibilityState expected = CompatibilityState::Waiting;
    if (gCompatibilityState.compare_exchange_strong(
            expected, CompatibilityState::TargetNotFound,
            std::memory_order_acq_rel)) {
        const char *libraryName = targetLibName;
        LOGW(OBFUSCATE("Timed out waiting for %s; fallback polling stopped"), libraryName);
    }
}

jboolean isGameLibLoaded(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    if (!kNativeExamplesConfigured) {
        gCompatibilityState.store(CompatibilityState::Ready, std::memory_order_release);
        return JNI_TRUE;
    }
    EarlyLoadObserver::NotifyIfReady();
    const CompatibilityState state = gCompatibilityState.load(std::memory_order_acquire);
    return state != CompatibilityState::Waiting &&
           state != CompatibilityState::Installing &&
           state != CompatibilityState::TargetNotFound;
}

const char *CompatibilityStateName(CompatibilityState state) {
    switch (state) {
        case CompatibilityState::Waiting:
            return "Waiting for target library";
        case CompatibilityState::Installing:
            return "Installing native hooks";
        case CompatibilityState::Ready:
            return "Ready";
        case CompatibilityState::ReadyWithLimits:
            return "Ready with limited features";
        case CompatibilityState::TargetNotFound:
            return "Target library not found";
        case CompatibilityState::MapFailed:
            return "Required startup compatibility failed";
        case CompatibilityState::HookFailed:
            return "Native hook setup failed";
        case CompatibilityState::UnsupportedAbi:
            return "Unsupported ABI";
    }
    return "Unknown";
}

const char *CompatibilityFailureName(CompatibilityFailure failure) {
    switch (failure) {
        case CompatibilityFailure::None:
            return "";
        case CompatibilityFailure::TargetProfileMismatch:
            return "target binary differs from every verified profile";
        case CompatibilityFailure::StartupPatchRejected:
            return "device rejected a verified startup patch";
    }
    return "unknown startup failure";
}

std::string CompatibilityFeatureDescriptor() {
    const CompatibilityState state = gCompatibilityState.load(std::memory_order_acquire);
    const char *color = state == CompatibilityState::Ready ? "#69D28C" :
                        (state == CompatibilityState::Waiting ||
                         state == CompatibilityState::Installing ||
                         state == CompatibilityState::ReadyWithLimits) ? "#E8B86A" : "#FF7A7A";
    std::string descriptor = "RichTextView_<font color='";
    descriptor += color;
    descriptor += "'><b>Menu status:</b> ";
    descriptor += CompatibilityStateName(state);
    if (state == CompatibilityState::MapFailed) {
        const CompatibilityFailure failure =
                gCompatibilityFailure.load(std::memory_order_acquire);
        if (failure != CompatibilityFailure::None) {
            descriptor += " - ";
            descriptor += CompatibilityFailureName(failure);
        }
    } else if (state == CompatibilityState::ReadyWithLimits) {
        if (gInstallWatchdogRecovered.load(std::memory_order_acquire)) {
            descriptor += " - setup did not confirm completion";
        } else if (!gHookExampleAvailable.load(std::memory_order_acquire)) {
            descriptor += " - hook example disabled";
        } else if (!gDirectCallExampleAvailable.load(std::memory_order_acquire)) {
            descriptor += " - direct-call example disabled";
        } else if (gDobbyHookFailureCount.load(std::memory_order_acquire) > 0) {
            descriptor += " - some optional hooks unavailable";
        }
    }
#if defined(__aarch64__)
    descriptor += " | ARM64";
#else
    descriptor += " | ARMv7";
#endif
    const long build = DetectedAppVersionCode();
    if (build > 0) {
        descriptor += " | ";
        descriptor += std::to_string(build);
    }
    descriptor += "</font>";
    return descriptor;
}

// ---------------------------------------------------------------------------------------------
// Complete feature-descriptor catalog
// ---------------------------------------------------------------------------------------------

bool IsFeatureHidden(int featureId) {
    for (const int hiddenFeatureId : kHiddenFeatureIds) {
        if (hiddenFeatureId == featureId) return true;
    }
    return false;
}

bool HasVisibleFeature(std::initializer_list<int> featureIds) {
    for (const int featureId : featureIds) {
        if (!IsFeatureHidden(featureId)) return true;
    }
    return false;
}

void AddFeatureIfVisible(std::vector<std::string> *features, int featureId,
                         const char *descriptor) {
    if (features != nullptr && !IsFeatureHidden(featureId)) {
        features->emplace_back(descriptor);
    }
}

jobjectArray BuildFeatureArray(JNIEnv *env, const std::vector<std::string> &features) {
    const jsize featureCount = static_cast<jsize>(features.size());
    jclass stringClass = env->FindClass(OBFUSCATE("java/lang/String"));
    jobjectArray result = env->NewObjectArray(
            featureCount, stringClass, env->NewStringUTF(OBFUSCATE("")));

    for (jsize i = 0; i < featureCount; ++i) {
        env->SetObjectArrayElement(
                result, i, env->NewStringUTF(features[static_cast<size_t>(i)].c_str()));
    }
    return result;
}

jobjectArray GetFeatureList(JNIEnv *env, jobject context) {
    (void) context;

    const std::string compatibilityDescriptor = CompatibilityFeatureDescriptor();
    std::vector<std::string> features;
    features.reserve(48);

    // Keep compatibility as a standalone top-level RichTextView. Prefixing it with
    // CollapseAdd_ before a Collapse_ parent would make it an orphaned child.
    features.emplace_back(compatibilityDescriptor);

    const CompatibilityState compatibilityState =
            gCompatibilityState.load(std::memory_order_acquire);
    if (compatibilityState == CompatibilityState::MapFailed ||
        compatibilityState == CompatibilityState::HookFailed ||
        compatibilityState == CompatibilityState::UnsupportedAbi) {
        // Do not render controls that look usable after a fatal prerequisite failure. Optional
        // failures use ReadyWithLimits below and remove only their own affected controls.
        features.emplace_back(OBFUSCATE(
                "RichTextView_<font color='#FF7A7A'><b>Features unavailable</b><br>The module stopped before installing feature hooks. No controls were applied to this session.</font>"));
        return BuildFeatureArray(env, features);
    }

    // Display-only types have no callback IDs and are included in every non-fatal session.
    features.emplace_back(OBFUSCATE("Category_Display Only Types"));
    features.emplace_back(OBFUSCATE(
            "RichTextView_<b>RichTextView</b> supports compact formatted guidance."));
    features.emplace_back(OBFUSCATE(
            "RichWebView_<html><body><b>RichWebView</b><br>supports HTML content.</body></html>"));
    features.emplace_back(OBFUSCATE("ButtonLink_Project Website_https://example.com"));

    if (HasVisibleFeature({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 29})) {
        features.emplace_back(OBFUSCATE("Category_Standalone Callback Controls"));
        AddFeatureIfVisible(&features, 1, OBFUSCATE("1_Toggle_Basic Toggle"));
        AddFeatureIfVisible(&features, 2,
                            OBFUSCATE("2_Toggle_Default On Toggle_True"));
        AddFeatureIfVisible(&features, 3,
                            OBFUSCATE("3_Toggle_Testing Toggle_ForTesting"));
        AddFeatureIfVisible(
                &features, 4,
                OBFUSCATE("4_Toggle_Default On Testing Toggle_True_ForTesting"));
        AddFeatureIfVisible(&features, 5, OBFUSCATE("5_Button_Ordinary Button"));
        AddFeatureIfVisible(&features, 6,
                            OBFUSCATE("6_ActionButton_Primary Action Button"));
        AddFeatureIfVisible(&features, 7,
                            OBFUSCATE("7_ButtonOnOff_Two State Button"));
        AddFeatureIfVisible(&features, 8,
                            OBFUSCATE("8_CheckBox_Example Check Box"));
        AddFeatureIfVisible(
                &features, 9,
                OBFUSCATE("9_RadioButton_Example Radio_First,Second,Third"));
        AddFeatureIfVisible(&features, 10,
                            OBFUSCATE("10_SeekBar_Example Seek Bar_0_100"));
        AddFeatureIfVisible(
                &features, 11,
                OBFUSCATE("11_Spinner_Example Spinner_Alpha,Beta,Gamma"));
        // The first MultiSelectSpinner CSV entry selects every later entry.
        AddFeatureIfVisible(
                &features, 29,
                OBFUSCATE("29_MultiSelectSpinner_Example Multi Select_All Options,Alpha,Beta,Gamma"));
    }

    if (HasVisibleFeature({12, 13, 14, 15, 16, 17, 18, 19})) {
        features.emplace_back(OBFUSCATE("Category_Input Types"));
        AddFeatureIfVisible(&features, 12,
                            OBFUSCATE("12_InputText_Text Input Without Default"));
        AddFeatureIfVisible(&features, 13,
                            OBFUSCATE("13_InputText_Guest_Text Input With Default"));
        AddFeatureIfVisible(
                &features, 14,
                OBFUSCATE("14_InputValue_100000_Integer Input With Maximum"));
        AddFeatureIfVisible(&features, 15,
                            OBFUSCATE("15_InputValue_Integer Input Without Maximum"));
        AddFeatureIfVisible(
                &features, 16,
                OBFUSCATE("16_InputFloat_100.5_Float Input With Maximum"));
        AddFeatureIfVisible(&features, 17,
                            OBFUSCATE("17_InputFloat_Float Input Without Maximum"));
        AddFeatureIfVisible(
                &features, 18,
                OBFUSCATE("18_InputLValue_9999999999_Long Input With Maximum"));
        AddFeatureIfVisible(&features, 19,
                            OBFUSCATE("19_InputLValue_Long Input Without Maximum"));
    }

    // Structural rows are emitted only while at least one child remains visible.
    if (HasVisibleFeature({20, 21, 22, 23, 24, 25})) {
        features.emplace_back(OBFUSCATE("Collapse_Default Open Collapse_True"));
        AddFeatureIfVisible(&features, 20,
                            OBFUSCATE("20_CollapseAdd_Toggle_Collapse Child Toggle"));
        if (HasVisibleFeature({23, 24, 25})) {
            features.emplace_back(OBFUSCATE(
                    "CollapseAdd_Group_Connected Input Workflow"));
            AddFeatureIfVisible(&features, 23,
                                OBFUSCATE("23_CollapseAdd_InputValue_500_Grouped Amount"));
            AddFeatureIfVisible(
                    &features, 24,
                    OBFUSCATE("24_CollapseAdd_Spinner_Grouped Mode_One,Two,Three"));
            AddFeatureIfVisible(
                    &features, 25,
                    OBFUSCATE("25_CollapseAdd_ActionButton_Apply Grouped Values"));
            features.emplace_back(OBFUSCATE("CollapseAdd_GroupEnd"));
        }
        if (!IsFeatureHidden(21)) {
            features.emplace_back(OBFUSCATE(
                    "CollapseAdd_Collapse_Nested Collapse_True"));
            AddFeatureIfVisible(&features, 21,
                                OBFUSCATE("21_CollapseAdd_Button_Nested Child Button"));
            features.emplace_back(OBFUSCATE("CollapseEnd"));
        }
        AddFeatureIfVisible(&features, 22,
                            OBFUSCATE("22_CollapseAdd_Button_Back In Parent Collapse"));
    }

    // The automatic-ID row intentionally remains visible: it has no stable ID to put in
    // kHiddenFeatureIds. Production controls should always use explicit IDs.
    features.emplace_back(OBFUSCATE("Collapse_Prefix and State Examples_ForTesting"));
    AddFeatureIfVisible(&features, 26,
                        OBFUSCATE("26_CollapseAdd_Button_Explicit Positive ID"));
    AddFeatureIfVisible(&features, -50,
                        OBFUSCATE("-50_CollapseAdd_Toggle_Signed Negative ID"));
    features.emplace_back(OBFUSCATE("CollapseAdd_Toggle_Automatic ID Example"));
    AddFeatureIfVisible(
            &features, 27,
            OBFUSCATE("27_CollapseAdd_ButtonOnOff_Default On Child_True"));
    AddFeatureIfVisible(
            &features, 28,
            OBFUSCATE("28_CollapseAdd_CheckBox_Default On Testing Child_True_ForTesting"));

    if (HasVisibleFeature({30, 31, 32})) {
        features.emplace_back(OBFUSCATE("Collapse_Native Implementation Examples"));
        AddFeatureIfVisible(
                &features, 30,
                OBFUSCATE("30_CollapseAdd_Toggle_Patch Example_ForTesting"));
        if (!IsFeatureHidden(31)) {
            if (gHookExampleAvailable.load(std::memory_order_acquire)) {
                features.emplace_back(OBFUSCATE(
                        "31_CollapseAdd_Toggle_Hook Example_ForTesting"));
            } else {
                features.emplace_back(OBFUSCATE(
                        "CollapseAdd_RichTextView_<font color='#E8B86A'>The hook example is unavailable for this binary. Other compatible examples remain active.</font>"));
            }
        }
        if (!IsFeatureHidden(32)) {
            if (gDirectCallExampleAvailable.load(std::memory_order_acquire)) {
                features.emplace_back(OBFUSCATE(
                        "32_CollapseAdd_Button_Direct Function Call Example_ForTesting"));
            } else {
                features.emplace_back(OBFUSCATE(
                        "CollapseAdd_RichTextView_<font color='#E8B86A'>The direct-call example is unavailable for this binary. Other compatible examples remain active.</font>"));
            }
        }
    }

    return BuildFeatureArray(env, features);
}

// Every callback type arrives here:
// - Toggle, ButtonOnOff, and CheckBox use boolean.
// - SeekBar, Spinner, MultiSelectSpinner, RadioButton, and InputValue use value.
// - MultiSelectSpinner returns 0 for all selected, or (1 << 30) | selectionMask for an
//   explicit subset. The first selectable CSV item maps to selectionMask bit 0.
// - InputLValue uses Lvalue.
// - InputText and InputFloat use text.
// - Button and ActionButton signal a press through their feature ID.
void Changes(JNIEnv *env, jclass clazz, jobject context, jint featNum, jstring featName,
             jint value, jlong Lvalue, jboolean boolean, jstring text) {
    (void) clazz;
    (void) featName;

    switch (featNum) {
        case 1:
            state.basicToggle.store(boolean, std::memory_order_relaxed);
            break;
        case 2:
            state.defaultOnToggle.store(boolean, std::memory_order_relaxed);
            break;
        case 3:
            state.testingToggle.store(boolean, std::memory_order_relaxed);
            break;
        case 4:
            state.defaultOnTestingToggle.store(boolean, std::memory_order_relaxed);
            break;
        case 5:
            Toast(env, context, OBFUSCATE("Ordinary Button pressed."),
                  ToastLength::LENGTH_SHORT);
            break;
        case 6:
            Toast(env, context, OBFUSCATE("Primary Action Button pressed."),
                  ToastLength::LENGTH_SHORT);
            break;
        case 7:
        case 27:
            state.buttonOnOff.store(boolean, std::memory_order_relaxed);
            break;
        case 8:
        case 28:
            state.checkBox.store(boolean, std::memory_order_relaxed);
            break;
        case 9:
            state.radioIndex.store(value, std::memory_order_relaxed);
            break;
        case 10:
            state.seekBarValue.store(value, std::memory_order_relaxed);
            break;
        case 11:
        case 24:
            state.spinnerIndex.store(value, std::memory_order_relaxed);
            break;
        case 29:
            // Java sends 0 for the default/all state. For an explicit subset, bit 30 is a
            // presence marker and the lower bits are the selected entries. Masking drops the
            // marker; an explicit empty selection therefore decodes to zero.
            state.multiSelectMask.store(
                    value == 0 ? kExampleMultiSelectAllMask
                               : value & kExampleMultiSelectAllMask,
                    std::memory_order_relaxed);
            break;
        case 12:
        case 13:
            state.textValue = JStringToUtf8(env, text);
            break;
        case 14:
        case 15:
        case 23:
            state.integerValue.store(value, std::memory_order_relaxed);
            break;
        case 16:
        case 17:
            state.floatText = JStringToUtf8(env, text);
            break;
        case 21:
            Toast(env, context, OBFUSCATE("Nested child button pressed."),
                  ToastLength::LENGTH_SHORT);
            break;
        case 22:
            Toast(env, context, OBFUSCATE("Parent collapse button pressed."),
                  ToastLength::LENGTH_SHORT);
            break;
        case 25:
            Toast(env, context, OBFUSCATE("Connected group action pressed."),
                  ToastLength::LENGTH_SHORT);
            break;
        case 30:
            ApplyPatchExample(env, context, boolean);
            break;
        case 31:
            if (!gHookExampleAvailable.load(std::memory_order_acquire)) {
                Toast(env, context, OBFUSCATE("Hook example is unavailable for this binary."),
                      ToastLength::LENGTH_LONG);
                break;
            }
            state.hookEnabled.store(boolean, std::memory_order_relaxed);
            break;
        case 32:
            if (!kNativeExamplesConfigured ||
                !gDirectCallExampleAvailable.load(std::memory_order_acquire) ||
                directFunctionExample == nullptr) {
                Toast(env, context,
                      OBFUSCATE("Direct function example is disabled until its RVA is configured."),
                      ToastLength::LENGTH_LONG);
                break;
            }
            directFunctionExample(nullptr, value, nullptr);
            break;
        case -50:
            // Signed explicit ID example. Avoid IDs reserved by Menu.cpp in real projects.
            state.basicToggle.store(boolean, std::memory_order_relaxed);
            break;
        default:
            // Automatic-ID declarations are useful for display prototypes. Prefer explicit IDs
            // for production controls so callbacks remain stable when the list is reordered.
            LOGI(OBFUSCATE("Unhandled template feature id: %d"), featNum);
            break;
    }

    if (featNum == 18 || featNum == 19) {
        state.longValue.store(static_cast<long long>(Lvalue), std::memory_order_relaxed);
    }
}
