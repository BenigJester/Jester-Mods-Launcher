cmake_minimum_required(VERSION 3.22.1)
project(ModMenuTemplate LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(Keystone STATIC IMPORTED)
set_target_properties(Keystone PROPERTIES
        IMPORTED_LOCATION
        "${CMAKE_CURRENT_SOURCE_DIR}/KittyMemory/Deps/Keystone/libs-android/${ANDROID_ABI}/libkeystone.a")

add_library(Dobby STATIC IMPORTED)
set_target_properties(Dobby PROPERTIES
        IMPORTED_LOCATION
        "${CMAKE_CURRENT_SOURCE_DIR}/Dobby/${ANDROID_ABI}/libdobby.a")

add_library(YourSaviour SHARED
        Main.cpp
        Menu/Jni.cpp
        Menu/Menu.cpp
        Menu/Setup.cpp
        Includes/Utils.cpp
        KittyMemory/KittyAsm.cpp
        KittyMemory/KittyIOFile.cpp
        KittyMemory/KittyMemory.cpp
        KittyMemory/KittyPtrValidator.cpp
        KittyMemory/KittyScanner.cpp
        KittyMemory/KittyUtils.cpp
        KittyMemory/MemoryBackup.cpp
        KittyMemory/MemoryPatch.cpp
        xDL/xdl.c
        xDL/xdl_iterate.c
        xDL/xdl_linker.c
        xDL/xdl_lzma.c
        xDL/xdl_util.c)

target_include_directories(YourSaviour PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}"
        "${CMAKE_CURRENT_SOURCE_DIR}/Includes"
        "${CMAKE_CURRENT_SOURCE_DIR}/Dobby")

target_compile_options(YourSaviour PRIVATE
        -w
        -fvisibility=hidden
        -fpermissive
        -fexceptions
        -Wno-error=format-security
        -Wno-error=c++11-narrowing)

target_link_options(YourSaviour PRIVATE
        -Wl,--gc-sections
        -Wl,--strip-all)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv2)

target_link_libraries(YourSaviour
        Keystone
        Dobby
        ${log-lib}
        ${android-lib}
        ${egl-lib}
        ${gles-lib})
