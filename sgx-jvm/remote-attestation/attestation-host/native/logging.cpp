#include <cstdio>

#include "logging.hpp"

void log(
    sgx_enclave_id_t enclave_id,
    sgx_status_t status,
    sgx_ra_context_t context,
    const char *message,
    ...
) {
    char mode[4] = { 0 };
    mode[0] = (SGX_SIM == 0) ? 'H' : 'S';
    mode[1] = (SGX_DEBUG == 0) ? 'R' : 'D';
    mode[2] = (SGX_PRERELEASE == 0) ? 'x' : 'P';
    mode[3] = 0;

    char buffer[1024];
    va_list args;
    va_start(args, message);
    vsnprintf(buffer, sizeof(buffer), message, args);
    va_end(args);

    printf(
        "SGX(id=%lx,status=%x,ctx=%u,mode=%s): %s\n",
        (uint64_t)enclave_id,
        (uint32_t)status,
        (uint32_t)context,
        mode,
        buffer
    );
}
