cmake_minimum_required(VERSION 3.22)
project(gsdfr_app C ASM)

set(DFR_SRC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../exploit/src/chains/dirtyfrag/src")
if(NOT EXISTS "${DFR_SRC_DIR}/dfr_core.c")
    message(FATAL_ERROR "missing dirtyfrag single source (exploit tree): ${DFR_SRC_DIR}")
endif()

add_library(gsdfr_app SHARED
    gsdfr_app_jni.c
    ${DFR_SRC_DIR}/dfr_core.c
    ${DFR_SRC_DIR}/dfr_crypto.c
    ${DFR_SRC_DIR}/dfr_kmi.c
    ${DFR_SRC_DIR}/dfr_pin_probe.c
    ${DFR_SRC_DIR}/elf_hook.c
    ${DFR_SRC_DIR}/dfr_loader.c
    ${DFR_SRC_DIR}/hook_blob.S)

target_include_directories(gsdfr_app PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${DFR_SRC_DIR})
target_compile_options(gsdfr_app PRIVATE -O2 -Wall -Wextra -Wno-unused-parameter
    -DDFR_WITH_EXEC_NOVERIFY
    -fvisibility=hidden -ffunction-sections -fdata-sections)
target_link_options(gsdfr_app PRIVATE -Wl,--gc-sections)
target_link_libraries(gsdfr_app log)
