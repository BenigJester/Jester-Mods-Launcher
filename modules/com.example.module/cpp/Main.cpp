#include <atomic>
#include <cstdint>
#include <map>
#include <string>
#include <thread>

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

enum class CompatibilityState : int {
    Waiting = 0,
    Installing,
    Ready,
    TargetNotFound,
    HookFailed,
    UnsupportedAbi,
};

// Generic MultiSelectSpinner callback contract. The example descriptor has three selectable
// entries after its leading "All Options" row, so its decoded native mask occupies bits 0..2.
constexpr int kExampleMultiSelectAllMask = (1 << 3) - 1;

void hack_thread();
void compatibility_wait_timeout();
void compatibility_install_watchdog();
static std::atomic<bool> gNativeInstallStarted{false};
static std::atomic<CompatibilityState> gCompatibilityState{CompatibilityState::Waiting};
static std::atomic<int> gRuntimeMethod{0};

bool StartNativeRuntime(int method) {
    constexpr int kInjectionMethod = 1;
    constexpr int kDirectPatchMethod = 2;
    if (method != kInjectionMethod && method != kDirectPatchMethod) {
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

    LOGI(OBFUSCATE("Starting native runtime in %s mode"),
         method == kDirectPatchMethod ? "direct-patch" : "injection");
    if (!kNativeExamplesConfigured) {
        gCompatibilityState.store(CompatibilityState::Ready, std::memory_order_release);
        LOGI(OBFUSCATE("Template native examples disabled; skipping early native observer"));
        return true;
    }
    EarlyLoadObserver::Start(targetLibName, hack_thread, compatibility_wait_timeout);
    return true;
}

bool IsDirectPatchRuntime() {
    constexpr int kDirectPatchMethod = 2;
    return gRuntimeMethod.load(std::memory_order_acquire) == kDirectPatchMethod;
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

bool InstallNativeExamples() {
    if (!kNativeExamplesConfigured) {
        LOGI(OBFUSCATE("Template native examples are disabled"));
        return true;
    }

#if defined(__aarch64__)
    // Hook with a callable original trampoline.
    HOOK(targetLibName, "0x123456", HookWithOriginalExample, originalExampleFunction);

    // Hook without an original trampoline:
    // HOOK_NO_ORIG(targetLibName, "0x234568", HookWithoutOriginalExample);

    // Resolve a method for a later direct call:
    directFunctionExample = reinterpret_cast<DirectFunction>(
            getAbsoluteAddress(targetLibName, OBFUSCATE("0x345678")));
    return originalExampleFunction != nullptr && directFunctionExample != nullptr;
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

#if defined(__aarch64__)
    gCompatibilityState.store(
            InstallNativeExamples() ? CompatibilityState::Ready
                                    : CompatibilityState::HookFailed,
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
                    expected, CompatibilityState::Ready, std::memory_order_acq_rel)) {
            const char *libraryName = targetLibName;
            LOGW(OBFUSCATE("%s native install status stayed pending; marking compatibility ready"),
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
        case CompatibilityState::TargetNotFound:
            return "Target library not found";
        case CompatibilityState::HookFailed:
            return "Native hook setup failed";
        case CompatibilityState::UnsupportedAbi:
            return "Unsupported ABI";
    }
    return "Unknown";
}

std::string CompatibilityFeatureDescriptor() {
    const CompatibilityState state = gCompatibilityState.load(std::memory_order_acquire);
    const char *color = state == CompatibilityState::Ready ? "#69D28C" :
                        (state == CompatibilityState::Waiting ||
                         state == CompatibilityState::Installing) ? "#E8B86A" : "#FF7A7A";
    std::string descriptor = "RichTextView_<font color='";
    descriptor += color;
    descriptor += "'><b>Menu status:</b> ";
    descriptor += CompatibilityStateName(state);
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

jobjectArray GetFeatureList(JNIEnv *env, jobject context) {
    (void) context;

    const std::string compatibilityDescriptor = CompatibilityFeatureDescriptor();
    const char *features[] = {
            // Keep compatibility as a standalone top-level RichTextView. Prefixing it with
            // CollapseAdd_ before a Collapse_ parent would make it an orphaned child.
            compatibilityDescriptor.c_str(),
            // Display-only types do not call Changes.
            OBFUSCATE("Category_Display Only Types"),
            OBFUSCATE("RichTextView_<b>RichTextView</b> supports compact formatted guidance."),
            OBFUSCATE("RichWebView_<html><body><b>RichWebView</b><br>supports HTML content.</body></html>"),
            OBFUSCATE("ButtonLink_Project Website_https://example.com"),

            OBFUSCATE("Category_Standalone Callback Controls"),
            OBFUSCATE("1_Toggle_Basic Toggle"),
            OBFUSCATE("2_Toggle_Default On Toggle_True"),
            OBFUSCATE("3_Toggle_Testing Toggle_ForTesting"),
            OBFUSCATE("4_Toggle_Default On Testing Toggle_True_ForTesting"),
            OBFUSCATE("5_Button_Ordinary Button"),
            OBFUSCATE("6_ActionButton_Primary Action Button"),
            OBFUSCATE("7_ButtonOnOff_Two State Button"),
            OBFUSCATE("8_CheckBox_Example Check Box"),
            OBFUSCATE("9_RadioButton_Example Radio_First,Second,Third"),
            OBFUSCATE("10_SeekBar_Example Seek Bar_0_100"),
            OBFUSCATE("11_Spinner_Example Spinner_Alpha,Beta,Gamma"),
            // MultiSelectSpinner keeps its popup open while radio-styled rows are toggled.
            // The first CSV entry selects every later entry. Callback value 0 means all;
            // otherwise bit 30 marks an explicit subset and bits 0..29 represent the entries.
            OBFUSCATE("29_MultiSelectSpinner_Example Multi Select_All Options,Alpha,Beta,Gamma"),

            OBFUSCATE("Category_Input Types"),
            OBFUSCATE("12_InputText_Text Input Without Default"),
            OBFUSCATE("13_InputText_Guest_Text Input With Default"),
            OBFUSCATE("14_InputValue_100000_Integer Input With Maximum"),
            OBFUSCATE("15_InputValue_Integer Input Without Maximum"),
            OBFUSCATE("16_InputFloat_100.5_Float Input With Maximum"),
            OBFUSCATE("17_InputFloat_Float Input Without Maximum"),
            OBFUSCATE("18_InputLValue_9999999999_Long Input With Maximum"),
            OBFUSCATE("19_InputLValue_Long Input Without Maximum"),

            // Top-level collapse, default-open suffix, CollapseAdd prefix, connected group,
            // ActionButton workflow, nested collapse, and CollapseEnd.
            OBFUSCATE("Collapse_Default Open Collapse_True"),
            OBFUSCATE("20_CollapseAdd_Toggle_Collapse Child Toggle"),
            OBFUSCATE("CollapseAdd_Group_Connected Input Workflow"),
            OBFUSCATE("23_CollapseAdd_InputValue_500_Grouped Amount"),
            OBFUSCATE("24_CollapseAdd_Spinner_Grouped Mode_One,Two,Three"),
            OBFUSCATE("25_CollapseAdd_ActionButton_Apply Grouped Values"),
            OBFUSCATE("CollapseAdd_GroupEnd"),
            OBFUSCATE("CollapseAdd_Collapse_Nested Collapse_True"),
            OBFUSCATE("21_CollapseAdd_Button_Nested Child Button"),
            OBFUSCATE("CollapseEnd"),
            OBFUSCATE("22_CollapseAdd_Button_Back In Parent Collapse"),

            // Marker and ID examples.
            OBFUSCATE("Collapse_Prefix and State Examples_ForTesting"),
            OBFUSCATE("26_CollapseAdd_Button_Explicit Positive ID"),
            OBFUSCATE("-50_CollapseAdd_Toggle_Signed Negative ID"),
            OBFUSCATE("CollapseAdd_Toggle_Automatic ID Example"),
            OBFUSCATE("27_CollapseAdd_ButtonOnOff_Default On Child_True"),
            OBFUSCATE("28_CollapseAdd_CheckBox_Default On Testing Child_True_ForTesting"),

            // Safe, disabled native examples.
            OBFUSCATE("Collapse_Native Implementation Examples"),
            OBFUSCATE("30_CollapseAdd_Toggle_Patch Example_ForTesting"),
            OBFUSCATE("31_CollapseAdd_Toggle_Hook Example_ForTesting"),
            OBFUSCATE("32_CollapseAdd_Button_Direct Function Call Example_ForTesting")
    };

    const int featureCount = sizeof(features) / sizeof(features[0]);
    jclass stringClass = env->FindClass(OBFUSCATE("java/lang/String"));
    jobjectArray result = env->NewObjectArray(featureCount, stringClass,
                                               env->NewStringUTF(OBFUSCATE("")));

    for (int i = 0; i < featureCount; ++i) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(features[i]));
    }
    return result;
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
            state.hookEnabled.store(boolean, std::memory_order_relaxed);
            break;
        case 32:
            if (!kNativeExamplesConfigured || directFunctionExample == nullptr) {
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
