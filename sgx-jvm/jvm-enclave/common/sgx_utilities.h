#pragma once

#include <cstdlib>

typedef struct {
    sgx_status_t err;
    const char *message;
    const char *suggestion;
} sgx_errlist_t;

/* Check error conditions for loading enclave */
void print_error_message(sgx_status_t ret);

#define CHECK_SGX(cmd) { sgx_status_t ret = cmd; \
if (ret != SGX_SUCCESS) { \
print_error_message(ret); \
exit(-1); \
} \
}
