/*
 * Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of Intel Corporation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */



#include <iostream>
#include "ErrorSupport.h"


typedef struct _sgx_errlist_t {
    sgx_status_t err;
    const char * msg;
} sgx_errlist_t;

static sgx_errlist_t sgx_errlist[] = {
    {SGX_ERROR_UNEXPECTED,               "Unexpected error occurred."},
    {SGX_ERROR_INVALID_PARAMETER,        "Invalid parameter."},
    {SGX_ERROR_OUT_OF_MEMORY,            "Out of memory."},
    {SGX_ERROR_ENCLAVE_LOST,             "Power transition occurred."},
    {SGX_ERROR_INVALID_ENCLAVE,          "Invalid enclave image."},
    {SGX_ERROR_INVALID_ENCLAVE_ID,       "Invalid enclave identification."},
    {SGX_ERROR_INVALID_SIGNATURE,        "Invalid enclave signature."},
    {SGX_ERROR_OUT_OF_EPC,               "Out of EPC memory."},
    {SGX_ERROR_NO_DEVICE,                "Invalid SGX device."},
    {SGX_ERROR_MEMORY_MAP_CONFLICT,      "Memory map conflicted."},
    {SGX_ERROR_INVALID_METADATA,         "Invalid encalve metadata."},
    {SGX_ERROR_DEVICE_BUSY,              "SGX device is busy."},
    {SGX_ERROR_INVALID_VERSION,          "Enclave metadata version is invalid."},
    {SGX_ERROR_ENCLAVE_FILE_ACCESS,      "Can't open enclave file."},

    {SGX_ERROR_INVALID_FUNCTION,         "Invalid function name."},
    {SGX_ERROR_OUT_OF_TCS,               "Out of TCS."},
    {SGX_ERROR_ENCLAVE_CRASHED,          "The enclave is crashed."},

    {SGX_ERROR_MAC_MISMATCH,             "Report varification error occurred."},
    {SGX_ERROR_INVALID_ATTRIBUTE,        "The enclave is not authorized."},
    {SGX_ERROR_INVALID_CPUSVN,           "Invalid CPUSVN."},
    {SGX_ERROR_INVALID_ISVSVN,           "Invalid ISVSVN."},
    {SGX_ERROR_INVALID_KEYNAME,          "The requested key name is invalid."},

    {SGX_ERROR_SERVICE_UNAVAILABLE,          "AESM service is not responsive."},
    {SGX_ERROR_SERVICE_TIMEOUT,              "Request to AESM is time out."},
    {SGX_ERROR_SERVICE_INVALID_PRIVILEGE,    "Error occurred while getting launch token."},
};


void ret_error_support(sgx_status_t ret)
{
    size_t idx = 0;
    size_t ttl = sizeof sgx_errlist/sizeof sgx_errlist[0];

    for (idx = 0; idx < ttl; idx++) {
        if(ret == sgx_errlist[idx].err) {
            std::cout << "Error: "<< sgx_errlist[idx].msg << std::endl;
            break;
        }
    }    
    if (idx == ttl)
        std::cout << "Error: Unexpected error occurred." <<std::endl;
    return;
}
