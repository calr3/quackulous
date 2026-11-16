cmake_minimum_required(VERSION 3.22)

project(quackulous_jni)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
# The engine is far too slow to use unoptimised, even in a debug build of the app.
set(CMAKE_CXX_FLAGS_DEBUG "-O2 -g")

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../../engineapi ${CMAKE_CURRENT_BINARY_DIR}/engineapi)

add_library(quackulous SHARED quackulous_jni.cpp)
target_link_libraries(quackulous quackleapi)

if (ANDROID)
	target_link_libraries(quackulous log)
else()
	# A desktop build lets JVM unit tests load the same library.
	find_package(JNI REQUIRED)
	target_include_directories(quackulous PRIVATE ${JNI_INCLUDE_DIRS})
endif()
