cmake_minimum_required(VERSION 3.22.1)

project(vkturn_loader)

add_library(vkturn_loader SHARED vkturn_loader.cpp)

target_compile_features(vkturn_loader PRIVATE cxx_std_17)

target_link_options(
    vkturn_loader
    PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

target_link_libraries(vkturn_loader PRIVATE android log)
