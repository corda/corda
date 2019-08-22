# Corda Blob Inspection

This is a best effort attempt at a C++ implemention of the CORDA serialization framework in a non JVM language. The initial target is a working "blob inspector", that is an exe that can take a serialised blob from a vault (or other) and decode it using C++.

## Currently Working

An implementation of a "blob inspector" that can take a serialised blob and decode it into a printable JSON format where that blob contains a constrained set of types. The current limitation with this implementation is that it does not understand associative containers (maps).

## Fututre Work

 * Encode and decode of local C++ types
 * Decpdable encode of native types
 * Some schema generation from the JVM canonical source

## Dependencies

 * qpid-proton
 * C++17
 * gtest
 * cmake

## Setup

### MacOS

 * brew install cmake
 * brew install qpid-proton

Google Test

 * git clone https://github.com/google/googletest
 * cd googletest
 * mkdir build
 * cd build
 * cmake ..
 * make
 * make install

### Linux

 * sudo apt-get install cmake
 * sudo apt-get install libqpid-proton8-dev
 * sudi apt-get install libgtest-dev
