cmake_minimum_required(VERSION 3.5)
project(sgx_rpc)

add_executable(edger8r IMPORTED)
set_target_properties(edger8r PROPERTIES IMPORTED_LOCATION ${SGX_LIBRARY_PATH}/sgx_edger8r)
