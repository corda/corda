include_directories (${BLOB-INSPECTOR_SOURCE_DIR}/src)
include_directories (${BLOB-INSPECTOR_SOURCE_DIR}/src/amqp)

link_directories (${BLOB-INSPECTOR_BINARY_DIR}/src/amqp)
link_directories (${BLOB-INSPECTOR_BINARY_DIR}/src/proton)

add_executable (blob-inspector main)

target_link_libraries (blob-inspector amqp proton qpid-proton)
