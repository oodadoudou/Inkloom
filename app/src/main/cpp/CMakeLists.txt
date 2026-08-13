cmake_minimum_required(VERSION 3.22.1)
project(inkloom_engine_v3 LANGUAGES CXX)

add_library(inkloom_engine_v3 SHARED
    v3_input_session.cpp
    v3_raster_ops.cpp
)

target_compile_features(inkloom_engine_v3 PRIVATE cxx_std_20)
target_compile_options(inkloom_engine_v3 PRIVATE -fvisibility=hidden)

find_library(android_log log)
find_library(android_bitmap jnigraphics)
target_link_libraries(inkloom_engine_v3 PRIVATE ${android_log} ${android_bitmap})
