#include <sys/mman.h>
#include <cstddef>

// Non-root flavor boundary: reserve private process-local space for a module
// runtime. It does not cross process boundaries or alter another application.
extern "C" int menu_initialize(const char* package_name, const char* module_path) {
    (void)module_path;
    if (package_name == nullptr || *package_name == '\0') return 0;
    void* arena = mmap(nullptr, 4096, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (arena == MAP_FAILED) return 0;
    munmap(arena, 4096);
    return 1;
}
