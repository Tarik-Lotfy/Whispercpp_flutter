cmake_minimum_required(VERSION 3.22.1)

project(whispercpp_flutter)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# whisper.cpp location (in order):
#   1) -DWHISPER_CPP_DIR=... from Gradle (whisperCppDir / WHISPER_CPP_DIR env) or manual CMake
#   2) WHISPER_CPP_DIR environment variable
#   3) Default: third_party/whisper.cpp under this directory (clone the repo there)
if(NOT DEFINED WHISPER_CPP_DIR OR WHISPER_CPP_DIR STREQUAL "")
  if(DEFINED ENV{WHISPER_CPP_DIR} AND NOT "$ENV{WHISPER_CPP_DIR}" STREQUAL "")
    file(TO_CMAKE_PATH "$ENV{WHISPER_CPP_DIR}" WHISPER_CPP_DIR)
  else()
    set(WHISPER_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/whisper.cpp")
  endif()
else()
  file(TO_CMAKE_PATH "${WHISPER_CPP_DIR}" WHISPER_CPP_DIR)
endif()

if(NOT EXISTS "${WHISPER_CPP_DIR}/CMakeLists.txt")
  message(FATAL_ERROR
    "whisper.cpp not found at WHISPER_CPP_DIR='${WHISPER_CPP_DIR}'. "
    "Clone https://github.com/ggerganov/whisper.cpp into "
    "'${CMAKE_CURRENT_SOURCE_DIR}/third_party/whisper.cpp', or set Gradle property "
    "whisperCppDir / environment WHISPER_CPP_DIR / CMake -DWHISPER_CPP_DIR to your checkout.")
endif()

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
