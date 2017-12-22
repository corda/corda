#ifndef __LOGGING_HPP__
#define __LOGGING_HPP__

#include <cstdarg>

#include <sgx_key_exchange.h>
#include <sgx_urts.h>

#ifdef LOGGING
#define LOG(enclave_id, status, context, message, ...) \
    log(enclave_id, (sgx_status_t)(status), context, message, ##__VA_ARGS__)
#else
#define LOG(enclave_id, status, context, message, ...) ;
#endif

/**
 * Log message to standard output.
 *
 * @param enclave_id The enclave identifier.
 * @param status The outcome of the last SGX operation.
 * @param context The remote attestation context.
 * @param message The message.
 */
void log(
    sgx_enclave_id_t enclave_id,
    sgx_status_t status,
    sgx_ra_context_t context,
    const char *message,
    ...
);

#endif /* __LOGGING_HPP__ */
