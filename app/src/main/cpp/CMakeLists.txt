cmake_minimum_required(VERSION 3.22.1)
project(inkloom_engine_v5 LANGUAGES C CXX)

add_library(inkloom_engine_v5 SHARED
    v5_color_management.cpp
    v5_composite.cpp
    v5_raster_ops.cpp
    v5_stamp_coverage.cpp
)

target_compile_features(inkloom_engine_v5 PRIVATE cxx_std_20)
# The native raster engine is part of the realtime production path even in debug builds. CMake's
# default Debug preset uses -O0, which turns bounded pixel loops into multi-frame operations and
# makes the debug APK exercise a materially different engine from Profile. Keep symbols/debugging,
# but compile the hot native implementation with the same baseline optimizer used in production.
target_compile_options(inkloom_engine_v5 PRIVATE -fvisibility=hidden -O2)

find_library(android_log log)
find_library(android_bitmap jnigraphics)
target_link_libraries(inkloom_engine_v5 PRIVATE ${android_log} ${android_bitmap})

file(GLOB LCMS_CORE CONFIGURE_DEPENDS vendor/lcms2/src/*.c)
add_library(inkloom_lcms STATIC ${LCMS_CORE})
set_target_properties(inkloom_lcms PROPERTIES POSITION_INDEPENDENT_CODE ON C_STANDARD 99)
target_include_directories(inkloom_lcms PUBLIC vendor/lcms2/include)
target_compile_definitions(inkloom_lcms PRIVATE HasTHREADS=1 HAVE_GMTIME_R=1)
target_compile_options(inkloom_lcms PRIVATE -O2 -fvisibility=hidden)
target_link_libraries(inkloom_engine_v5 PRIVATE inkloom_lcms m)
