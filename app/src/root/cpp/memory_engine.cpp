#include <sys/types.h>
#include <unistd.h>
#include <fstream>
#include <string>

// Root flavor boundary: exposes mapping discovery only. Plugin-specific memory
// operations remain outside the generic launcher and require independent policy.
extern "C" int menu_initialize(const char* package_name, const char* module_path) {
    (void)module_path;
    if (package_name == nullptr || *package_name == '\0') return 0;

    // A real root module may use its own privileged bridge. The host does not
    // open /proc/<pid>/mem or perform patch operations on behalf of a module.
    std::ifstream maps("/proc/self/maps");
    return maps.good() ? 1 : 0;
}
