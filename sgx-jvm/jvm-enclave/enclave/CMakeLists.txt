# Build file for the enclave. A few notes:
#
# - We take control over the final link of the DSO because we need very precise control over the linker flags.
#   That's why we use add_custom_command to do the final link.
# - The enclave library must be signed. We self-sign here, which means the CPU won't close the debug ports. Thus it is
#   not secure.

# Compiler flags:
#
# -nostdinc: Prepare the compiler for a bare-metal style runtime environment.
# -fvisibility=hidden: Don't export symbols.
# -fpie: Resulting library is relocatable, can be loaded anywhere in the address space.
# -fstack-protector: Compiler generates extra code to protect against stack smashing exploits.
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../common ${CMAKE_CURRENT_SOURCE_DIR}/../common/build)
include_directories("/usr/include")
include_directories("${JAVA_HOME}/include")
include_directories("${JAVA_HOME}/include/linux")
include_directories(${GENERATED_RPC_DIR})

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fpie -fstack-protector")
set(ENCLAVE_UNSIGNED_OUTPUT_LIB cordaenclave.so)
set(ENCLAVE_SIGNED_OUTPUT_LIB cordaenclave.signed.so)
set(SGX_SIGN_TOOL ${SGX_SDK}/build/linux/sgx_sign)
set(PROGUARD_JAR_PATH /usr/share/proguard6.0beta1/lib/proguard.jar)
set(AVIAN_PATH ${PROJECT_SOURCE_DIR}/../../avian)
set(JDK_IMAGE ${PROJECT_SOURCE_DIR}/../../jdk8u/build/linux-x86_64-normal-server-release/images/j2re-image)
set(ENCLAVE_JAR_PATH ${PROJECT_SOURCE_DIR}/../../../verify-enclave/build/libs/corda-enclavelet.jar)
set(AVIAN_PROCESS "-debug-openjdk-src")

# C++11
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

if(NOT JAVA_HOME})
    set(JAVA_HOME $ENV{JAVA_HOME} CACHE STRING "")
endif()

if(SGX_USE_HARDWARE)
    set(URTS_LIB "sgx_urts" CACHE STRING "")
    set(TRTS_LIB "sgx_trts" CACHE STRING "")
    set(SGX_SERVICE_LIB "sgx_tservice" CACHE STRING "")
    # Avoid linking the binary against the stub libsgx_urts.so provided in the SDK rather than the real thing.
    # If the stub is used, we get a message like "Please use the correct libsgx_urts from the PSW"
    set(CMAKE_SKIP_BUILD_RPATH TRUE)
else()
    set(URTS_LIB "sgx_urts_sim" CACHE STRING "")
    set(TRTS_LIB "sgx_trts_sim" CACHE STRING "")
    set(SGX_SERVICE_LIB "sgx_tservice_sim" CACHE STRING "")
endif()

file(GLOB JAVA_FILES RELATIVE ${CMAKE_CURRENT_SOURCE_DIR} *.java)
string(REPLACE ".java" ".class" CLASS_FILES "${JAVA_FILES}")

# An RSA private key we can use to self-sign. The SGX code is very picky about the format. It must be generated like this:
# openssl genrsas -out selfsigning.pem -3 3072
set(PRIVATE_KEY_NAME ${CMAKE_CURRENT_SOURCE_DIR}/selfsigning.pem)

# Now add the compiled class files to our copy of the boot jar (as we need a single uni-jar for everything).
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/encjars/classpath.jar ${CMAKE_CURRENT_BINARY_DIR}/encjars/app.jar ${CMAKE_CURRENT_BINARY_DIR}/encjars/corda-enclavelet.jar ${CMAKE_CURRENT_BINARY_DIR}/encjars/jce.jar ${CMAKE_CURRENT_BINARY_DIR}/encjars/jsse.jar
    COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/create-boot-jar.sh ${AVIAN_PATH}/build/linux-x86_64${AVIAN_PROCESS} ${ENCLAVE_JAR_PATH} ${CMAKE_CURRENT_BINARY_DIR}/encjars ${PROGUARD_JAR_PATH} ${CMAKE_CURRENT_SOURCE_DIR}/proguard.pro ${JDK_IMAGE}
    DEPENDS ${ENCLAVE_JAR_PATH}
)

# Convert the boot jar and app jar into an object file we can statically link.
add_executable(binaryToObject IMPORTED)
set_target_properties(binaryToObject PROPERTIES IMPORTED_LOCATION ${AVIAN_PATH}/build/linux-x86_64${AVIAN_PROCESS}/binaryToObject/binaryToObject)

add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/boot-jar.o
    COMMAND binaryToObject ${CMAKE_CURRENT_BINARY_DIR}/encjars/classpath.jar ${CMAKE_CURRENT_BINARY_DIR}/boot-jar.o _binary_boot_jar_start _binary_boot_jar_end linux x86_64
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/encjars/classpath.jar
)
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/app-jar.o
    COMMAND binaryToObject ${CMAKE_CURRENT_BINARY_DIR}/encjars/app.jar ${CMAKE_CURRENT_BINARY_DIR}/app-jar.o _binary_app_jar_start _binary_app_jar_end linux x86_64
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/encjars/app.jar
)

# Find the Avian JVM as static objects.
set(PATH_TO_AVIAN ${AVIAN_PATH}/build/linux-x86_64${AVIAN_PROCESS}/libavian.a)

# Generate the static dispatch table
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/dispatch_table.txt ${CMAKE_CURRENT_BINARY_DIR}/dispatch_table.cpp
    COMMAND python2.7 ${CMAKE_CURRENT_SOURCE_DIR}/gen_dispatch_table.py ${PATH_TO_AVIAN} ${CMAKE_CURRENT_SOURCE_DIR}/dispatch_table.cpp.template ${CMAKE_CURRENT_BINARY_DIR}
    DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/dispatch_table.cpp.template
)

include_directories("/usr/include")
include_directories("${JAVA_HOME}/include")
include_directories("${JAVA_HOME}/include/linux")

# Compile os support, using system headers.

set_source_files_properties(${GENERATED_EDL_FILES} PROPERTIES GENERATED TRUE)
add_library(os_support os_support.cpp stubsyms.cpp ${CMAKE_CURRENT_BINARY_DIR}/dispatch_table.cpp ${GENERATED_RPC_DIR}/java_t.h)
target_include_directories(os_support PUBLIC ${SGX_SDK_INCLUDE} ${AVIAN_PATH}/include ${AVIAN_PATH}/src ${GENERATED_RPC_DIR})
add_dependencies(os_support GENERATED_EDL)

# Build the enclave into a static archive. We switch off standard headers to make sure SGX headers are used.
add_library(enclave_objects enclave.cpp enclave_start_thread.cpp sgx_thread_completion.cpp ${GENERATED_RPC_DIR}/java_t.c ${CMAKE_CURRENT_BINARY_DIR}/boot-jar.o ${CMAKE_CURRENT_BINARY_DIR}/app-jar.o)
add_dependencies(enclave_objects GENERATED_EDL)
target_include_directories(enclave_objects PUBLIC ${COMMON_INCLUDE} ${SGX_SDK_INCLUDE} ${SGX_SDK_INCLUDE}/tlibc ${SGX_SDK}/sdk/tlibstdcxx/stlport ${AVIAN_PATH}/include ${AVIAN_PATH}/src ${GENERATED_RPC_DIR})
target_compile_options(enclave_objects PUBLIC -nostdinc)

# Linker flags:
#
# - Don't use the standard libraries or expect any OS.
# - Link against the SGX trusted enclave runtime.
# - Use a custom linker script to restrict symbol exports.
#
# We could probably use pkgconfig for a few of these.
set(ENCLAVE_LINKER_FLAGS
        "-Wl,--no-undefined"
        "-nostdlib"
        "-nodefaultlibs"
        "-nostartfiles"
        "-L${SGX_LIBRARY_PATH}"
        "-Wl,--whole-archive"
            "-l${TRTS_LIB}"
        "-Wl,--no-whole-archive"
        "-Wl,--start-group"
            "${PATH_TO_AVIAN}"
            "libos_support.a"
            "libenclave_objects.a"
            "-lsgx_tstdc"
            "-lsgx_tstdcxx"
            "-lsgx_tcrypto"
            "-l${SGX_SERVICE_LIB}"
        "-Wl,--end-group"
        "${CMAKE_CURRENT_SOURCE_DIR}/libz.a"
        "-Wl,-Bstatic"
        "-Wl,-Bsymbolic"
        "-Wl,--no-undefined"
        "-Wl,-pie,-eenclave_entry"
        "-Wl,--export-dynamic"
        "-Wl,--defsym,__ImageBase=0"
        "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/linkerscript.lds"
)

# Convert to an unsigned ELF DSO.
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED_OUTPUT_LIB}
    COMMAND ${CMAKE_CXX_COMPILER} -o ${ENCLAVE_UNSIGNED_OUTPUT_LIB} ${ENCLAVE_LINKER_FLAGS}
    DEPENDS enclave_objects os_support ${SGX_LIBRARY_PATH} ${PATH_TO_AVIAN}
)

# Sign the enclave.
add_executable(sgx_sign IMPORTED)
set_target_properties(sgx_sign PROPERTIES IMPORTED_LOCATION ${SGX_SIGN_TOOL})
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_OUTPUT_LIB}
    COMMAND sgx_sign sign -key ${PRIVATE_KEY_NAME} -enclave ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED_OUTPUT_LIB} -out ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_OUTPUT_LIB} -config ${CMAKE_CURRENT_SOURCE_DIR}/enclave.xml
    DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_UNSIGNED_OUTPUT_LIB} ${PRIVATE_KEY_NAME} ${CMAKE_CURRENT_SOURCE_DIR}/enclave.xml
)

add_custom_target(enclave DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${ENCLAVE_SIGNED_OUTPUT_LIB})
