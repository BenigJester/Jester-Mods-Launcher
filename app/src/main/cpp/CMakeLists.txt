cmake_minimum_required(VERSION 3.22.1)
project(moodtools_hub_native)

function(harden_launcher_target target_name)
    target_compile_features(${target_name} PRIVATE cxx_std_17)
    target_compile_options(${target_name} PRIVATE
        $<$<NOT:$<CONFIG:Debug>>:-fvisibility=hidden>
        $<$<NOT:$<CONFIG:Debug>>:-fvisibility-inlines-hidden>
        $<$<NOT:$<CONFIG:Debug>>:-ffunction-sections>
        $<$<NOT:$<CONFIG:Debug>>:-fdata-sections>
        $<$<NOT:$<CONFIG:Debug>>:-fstack-protector-strong>
        $<$<NOT:$<CONFIG:Debug>>:-D_FORTIFY_SOURCE=2>)
    target_link_options(${target_name} PRIVATE
        $<$<NOT:$<CONFIG:Debug>>:-Wl,--gc-sections>
        $<$<NOT:$<CONFIG:Debug>>:-Wl,--exclude-libs,ALL>)
endfunction()

if(CMAKE_CXX_FLAGS MATCHES "EXECUTION_MODE_ROOT")
    set(MEMORY_ENGINE_SOURCE ../../root/cpp/memory_engine.cpp)
    add_library(menu_native SHARED native_linker.cpp ${MEMORY_ENGINE_SOURCE})
    target_compile_definitions(menu_native PRIVATE EXECUTION_MODE_ROOT=1)
    harden_launcher_target(menu_native)

    # Injected directly by the standalone launcher's temporary root helper.
    # This is an APK-owned runtime library, not a Magisk/APatch/Zygisk module.
    add_library(moodtools_root_runtime SHARED ../../root/cpp/root_runtime.cpp)
    harden_launcher_target(moodtools_root_runtime)
    target_link_libraries(moodtools_root_runtime log)
elseif(CMAKE_CXX_FLAGS MATCHES "EXECUTION_MODE_NONROOT")
    set(MEMORY_ENGINE_SOURCE ../../nonroot/cpp/memory_engine.cpp)
    add_library(menu_native SHARED native_linker.cpp ${MEMORY_ENGINE_SOURCE})
    target_compile_definitions(menu_native PRIVATE EXECUTION_MODE_NONROOT=1)
    harden_launcher_target(menu_native)
else()
    message(FATAL_ERROR "An execution mode flavor must be selected")
endif()

find_library(log-lib log)
target_link_libraries(menu_native ${log-lib} dl)
