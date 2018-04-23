#include "simple_u.h"

#include <sgx_urts.h>
#include <sgx.h>

#include <cstdlib>
#include <cstdio>

#include "sgx_error_list.h"

/* Check error conditions for loading enclave */
void print_error_message(sgx_status_t ret)
{
    size_t idx = 0;
    size_t ttl = sizeof sgx_errlist/sizeof sgx_errlist[0];

    for (idx = 0; idx < ttl; idx++) {
        if (ret == sgx_errlist[idx].err) {
            if (NULL != sgx_errlist[idx].suggestion)
                printf("Info: %s\n", sgx_errlist[idx].suggestion);
            printf("Error: %s\n", sgx_errlist[idx].message);
            break;
        }
    }

    if (idx == ttl)
        printf("Error: Unexpected error occurred.\n");
}

inline bool check_sgx_return_value(sgx_status_t ret)
{
    if (ret == SGX_SUCCESS)
    {
        return true;
    }
    else
    {
        print_error_message(ret);
        return false;
    }
}

void ocall_print(const char* str)
{
    printf("ENCLAVE: %s\n", str);
}

int main(int argc, char **argv)
{
    printf("SGX_DEBUG_FLAG = %d\n", SGX_DEBUG_FLAG);

    if (argc != 2)
    {
        puts("Usage: <binary> <signed.enclave.so>");
        return 1;
    }

    const char *enclave_path = argv[1];
    sgx_launch_token_t token = {0};
    sgx_enclave_id_t enclave_id = {0};
    int updated = 0;
    int returned_int = 0;

    if (false == check_sgx_return_value(sgx_create_enclave(enclave_path, SGX_DEBUG_FLAG, &token, &updated, &enclave_id, NULL))) {
        return 1;
    }

    if (false == check_sgx_return_value(get_number(enclave_id, &returned_int))) {
        return 1;
    }

    printf("get_number() = %d\n", returned_int);
    puts("Enclave ran successfully!");

    return 0;
}
