PROJECT (BLOB-INSPECTOR)

cmake_minimum_required(VERSION 3.10)

SET (${PROJECT_NAME}_MAJOR_VERSION 0)
SET (${PROJECT_NAME}_MINOR_VERSION 0)
SET (${PROJECT_NAME}_PATCH_LEVEL 1)

#ADD_DEFINITIONS ("-Wall -g -ansi -pedantic")
ADD_DEFINITIONS ("-Wall -g -ansi")

#ADD_DEFINITIONS ("-DSRC_DEBUG")

#
#
#
set (CMAKE_VERBOSE_MAKEFILE "FALSE" CACHE BOOL "" FORCE)

#
# For "reasons" something to do with the way the comand line compiler
# is invoked on MacOS /usr/local/include is removed from the 
# system include path so add it back in or we will fail to find
# qpid-python files as brew installs it there
#
if(APPLE)
    include_directories (/usr/local/include)
    link_directories (/usr/local/lib)
endif()

#
# Interface include files
#
include_directories (${BLOB-INSPECTOR_SOURCE_DIR}/include)

add_compile_options(-std=c++17)

ADD_SUBDIRECTORY (src)
ADD_SUBDIRECTORY (bin)
