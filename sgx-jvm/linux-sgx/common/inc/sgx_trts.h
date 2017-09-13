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

#ifndef _SGX_TRTS_H_
#define _SGX_TRTS_H_

#include "sgx_error.h"
#include "stddef.h"
#include "sgx_defs.h"

#ifdef __cplusplus
extern "C" {
#endif

/* sgx_is_within_enclave()
 * Parameters:
 *      addr - the start address of the buffer
 *      size - the size of the buffer
 * Return Value:
 *      1 - the buffer is strictly within the enclave
 *      0 - the whole buffer or part of the buffer is not within the enclave,
 *          or the buffer is wrap around
*/
int SGXAPI sgx_is_within_enclave(const void *addr, size_t size);

/* sgx_is_outside_enclave()
 * Parameters:
 *      addr - the start address of the buffer
 *      size - the size of the buffer
 * Return Value:
 *      1 - the buffer is strictly outside the enclave
 *      0 - the whole buffer or part of the buffer is not outside the enclave,
 *          or the buffer is wrap around
*/
int SGXAPI sgx_is_outside_enclave(const void *addr, size_t size);


/* sgx_read_rand()
 * Parameters:
 *      rand - the buffer to receive the random number
 *      length_in_bytes - the number of bytes to read the random number
 * Return Value:
 *      SGX_SUCCESS - success
 *      SGX_ERROR_INVALID_PARAMETER - the parameter is invalid
 *      SGX_ERROR_UNEXPECTED - HW failure of RDRAND instruction
*/
sgx_status_t SGXAPI sgx_read_rand(unsigned char *rand, size_t length_in_bytes);

#ifdef __cplusplus
}
#endif

#endif
