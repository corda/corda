include_directories (${BLOB-INSPECTOR_SOURCE_DIR}/libs)

link_directories (${BLOB-INSPECTOR_BINARY_DIR}/libs/amqp)
link_directories (${BLOB-INSPECTOR_BINARY_DIR}/libs/proton-wrapper)

add_executable (schema-dumper main)

target_link_libraries (schema-dumper amqp proton qpid-proton)
