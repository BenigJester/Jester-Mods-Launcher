# Issue 145: BlackBox cross-page code writes

Report: https://github.com/BenigJester/Jester-Mods-Launcher/issues/145

Otherworld Legends 3.7.0/20680, launcher 4.6.2, Xiaomi 25053RT47C,
Android 16/API 36, ARM64, 4096-byte pages. Selected PID 6338 starts at
1793278171989 and exits at 1793278172267. Its native stack records
memcpy -> CodePatch -> DobbyHook during IOCore.enableRedirect, before game/menu
initialization. The launcher libblackbox build ID is
722daab7efd5bbefb1e563a389bdedbf11ba7559.

The destination starts at 0x760bd37ffc, four bytes before a page boundary,
with a 16-byte copy. The fault is SEGV_ACCERR at 0x760bd38000, the next page.
Disassembly of the bundled ARM64 archive's code-patch-tool-posix.cc.o confirms
it calls mprotect for one page only before memcpy and does not check its result.
Archive SHA256: B0128FE5E30B5401859C389703E0515C499E0AF5DB9D1E4B5E8FACE672D61CDC.

The launcher now links --wrap=CodePatch and supplies a small shared writer that
calculates the entire page span using the runtime page size. It rejects empty,
null, overflowing and invalid-page-size requests, checks write permission before
copying, clears the instruction cache and propagates RX restoration failures.
It retains the original writer's RX postcondition. The vendor static archives,
hook targets, redirection policy and game modules are unchanged. No signals are
swallowed and no guest initialization is skipped.

Upstream context (not assumed to match our old archive):
https://github.com/jmpews/Dobby/blob/master/source/Backend/UserMode/ExecMemory/code-patch-tool-posix.cc
also accounts for a patch spanning page boundaries. The local archive's actual
machine code, rather than upstream version assumptions, establishes this defect.

Validation:

- `node scripts/test_blackbox_code_patch.cjs <NDK LLVM bin directory>` compiles
  the production writer body to WebAssembly with deterministic platform fakes.
  It executes single-page, exact-boundary, crossing and multi-page writes with
  4K/16K pages, denied write/restore operations, invalid inputs, overflow and
  byte-scope checks. PASS. The fake platform does not simulate kernel mprotect.
- `gradlew.bat :Bcore:externalNativeBuildDebug --console=plain`: PASS for
  arm64-v8a and armeabi-v7a. Log: ignored temp/issue145-native-check.log.
- Diagnostic relinks retain maps under Bcore/.cxx/Debug/503q10c2/<ABI>/
  issue145-link.map. ARM64 disassembly shows branch/call sites targeting
  __wrap_CodePatch, not the old CodePatch implementation. ARM32's map also
  contains the wrapper and its link command uses --wrap=CodePatch.

These are isolated correctness/build/linkage checks. Android memory protection,
concurrent runtime hook installation, game startup and the affected Xiaomi
device remain unverified. No device installation, GitHub reply, commit, push,
release or version bump is performed in this step. Issue 139 remains separate.
