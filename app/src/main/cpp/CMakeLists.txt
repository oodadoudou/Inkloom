cmake_minimum_required(VERSION 3.22.1)
project(inkloom_engine_v2 LANGUAGES CXX)

add_library(inkloom_engine_v2 SHARED
    stroke_input_modeler.cpp
)

target_compile_features(inkloom_engine_v2 PRIVATE cxx_std_20)
target_compile_options(inkloom_engine_v2 PRIVATE -fvisibility=hidden)

find_library(android_log log)
target_link_libraries(inkloom_engine_v2 PRIVATE ${android_log})
