cmake_minimum_required(VERSION 3.22.1)

project(whispercpp_flutter)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Development wiring: link against the locally cloned whisper.cpp checkout.
set(WHISPER_CPP_DIR "D:/Projects/whisper.cpp")

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_USE_SYSTEM_GGML OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_CPP_DIR} ${CMAKE_CURRENT_BINARY_DIR}/whispercpp)

add_library(
    whispercpp_flutter
    SHARED
    whispercpp_flutter.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_include_directories(
    whispercpp_flutter
    PRIVATE
    ${WHISPER_CPP_DIR}/include
    ${WHISPER_CPP_DIR}/ggml/include
)

target_link_libraries(
    whispercpp_flutter
    whisper
    ${log-lib}
    ${android-lib}
)
