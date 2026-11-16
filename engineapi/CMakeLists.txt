cmake_minimum_required(VERSION 3.16)

project(quackleapi)

include("${CMAKE_CURRENT_SOURCE_DIR}/../Settings.cmake")

if (NOT TARGET libquackle)
	add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/.. ${CMAKE_CURRENT_BINARY_DIR}/libquackle)
endif()

add_library(quackleapi STATIC
	alphabetloader.cpp
	engine.cpp
	lexiconbuilder.cpp
	md5.cpp
)
target_include_directories(quackleapi PUBLIC ${CMAKE_CURRENT_SOURCE_DIR} ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(quackleapi PUBLIC libquackle)

if (NOT ANDROID)
	add_executable(quackleapi_tool tool/main.cpp)
	target_link_libraries(quackleapi_tool quackleapi)
endif()
