cmake_minimum_required(VERSION 3.22.1)
project(stronghold_node_bridge LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(stronghold_node_bridge SHARED
    src/main/cpp/native-lib.cpp
)

target_include_directories(stronghold_node_bridge PRIVATE
    ${CMAKE_SOURCE_DIR}/libnode/include/node
)

add_library(node SHARED IMPORTED)
set_target_properties(node PROPERTIES
    IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/libnode/bin/${ANDROID_ABI}/libnode.so"
)

find_library(log-lib log)

target_link_libraries(stronghold_node_bridge
    node
    ${log-lib}
)
