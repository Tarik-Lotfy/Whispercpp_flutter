cmake_minimum_required(VERSION 3.22.1)

project(whispercpp_flutter)

add_library(
    whispercpp_flutter
    SHARED
    whispercpp_flutter.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    whispercpp_flutter
    ${log-lib}
)
