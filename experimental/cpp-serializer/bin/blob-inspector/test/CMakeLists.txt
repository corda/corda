set (EXE "blob-inspector-test")

set (blob-inspector-test-sources
        main.cxx
        blob-inspector-test.cxx
)

link_directories (${BLOB-INSPECTOR_BINARY_DIR}/bin/blob-inspector)
include_directories (${BLOB-INSPECTOR_BINARY_DIR}/bin/blob-inspector)

add_executable (${EXE} ${blob-inspector-test-sources})

target_link_libraries (${EXE} gtest amqp blob-inspector-lib)

if (UNIX)
    target_link_libraries (${EXE} pthread qpid-proton proton)
endif (UNIX)
