include_directories (${BLOB-INSPECTOR_SOURCE_DIR}/src)
include_directories (${BLOB-INSPECTOR_SOURCE_DIR}/src/amqp)

link_directories (${BLOB-INSPECTOR_BINARY_DIR}/src/amqp)
link_directories (${BLOB-INSPECTOR_BINARY_DIR}/src/proton)

add_executable (schema-dumper main)

target_link_libraries (schema-dumper amqp proton qpid-proton)
