cmake_minimum_required(VERSION 3.22.1)

project(blackbox LANGUAGES C CXX)

add_library(dobby STATIC IMPORTED)
set_target_properties(
    dobby
    PROPERTIES IMPORTED_LOCATION
    "${CMAKE_CURRENT_SOURCE_DIR}/Dobby/${ANDROID_ABI}/libdobby.a"
)

add_library(
    xdl
    STATIC
    xdl/xdl.c
    xdl/xdl_iterate.c
    xdl/xdl_linker.c
    xdl/xdl_lzma.c
    xdl/xdl_util.c
)
target_include_directories(xdl PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}")

add_library(
    blackbox
    SHARED
    BoxCore.cpp
    hidden_api.cpp
    IO.cpp
    Utils/elf_util.cpp
    Hook/DexFileHook.cpp
    Hook/FileSystemHook.cpp
    Utils/VirtualSpoof.cpp
    Utils/HexDump.cpp
    Utils/AntiDetection.cpp
    Hook/VMClassLoaderHook.cpp
    Hook/UnixFileSystemHook.cpp
    Hook/BinderHook.cpp
    Hook/BaseHook.cpp
    JniHook/JniHook.cpp
)

target_compile_features(blackbox PRIVATE cxx_std_17)
target_compile_options(
    blackbox
    PRIVATE
    -Wno-error=format-security
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    -w
    $<$<COMPILE_LANGUAGE:CXX>:-Werror>
    $<$<COMPILE_LANGUAGE:CXX>:-fms-extensions>
    $<$<AND:$<COMPILE_LANGUAGE:CXX>,$<STREQUAL:${ANDROID_ABI},armeabi-v7a>>:-marm>
)
target_include_directories(blackbox PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
target_link_options(
    blackbox
    PRIVATE
    -Wl,--gc-sections,--strip-all,-z,max-page-size=16384
)
target_link_libraries(blackbox PRIVATE dobby xdl log android z)
