#include "Dobby/dobby.h"
#include <cstring>
#include <limits>
#include <sys/mman.h>
#include <unistd.h>

// The bundled Dobby archive protects only the first page. Interpose its shared
// writer so a trampoline at the end of a page cannot write into a read-only page.
extern "C" MemoryOperationError __wrap_CodePatch(void *address, uint8_t *buffer, uint32_t size) {
    const long pageSize = sysconf(_SC_PAGESIZE);
    const uintptr_t start = reinterpret_cast<uintptr_t>(address);
    if (!address || !buffer || !size || pageSize <= 0 ||
        size > std::numeric_limits<uintptr_t>::max() - start) return kMemoryOperationError;
    const uintptr_t page = static_cast<uintptr_t>(pageSize);
    const uintptr_t first = start - start % page;
    const uintptr_t last = (start + size - 1) - (start + size - 1) % page;
    if (last - first > std::numeric_limits<size_t>::max() - page) return kMemoryOperationError;
    const size_t length = last - first + page;
    if (mprotect(reinterpret_cast<void *>(first), length, PROT_READ | PROT_WRITE | PROT_EXEC) != 0)
        return kMemoryOperationError;
    std::memcpy(address, buffer, size);
    __builtin___clear_cache(reinterpret_cast<char *>(address), reinterpret_cast<char *>(address) + size);
    // Preserve the archive's RX postcondition, and propagate permission failures.
    return mprotect(reinterpret_cast<void *>(first), length, PROT_READ | PROT_EXEC) == 0
            ? kMemoryOperationSuccess : kMemoryOperationError;
}
