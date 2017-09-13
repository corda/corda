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



/*
 * Here contains functions intended to be used by `sgx_edger8r' only.
 *
 * -------------------------------------
 * Be warned: use them at your own risk.
 * -------------------------------------
 *
 */

#ifndef _SGX_EDGER8R_H_
#define _SGX_EDGER8R_H_

#include "sgx_defs.h"
#include "sgx_error.h"
#include "sgx_eid.h"
#include <stddef.h>         /* for size_t */

/* The `sgx_edger8r' tool will generate C interfaces. */
#ifdef __cplusplus
#    define SGX_EXTERNC extern "C"
#else
#    define SGX_EXTERNC
#endif

#ifdef __cplusplus
extern "C" {
#endif

/* sgx_ocalloc()
 * Parameters:
 *     size - bytes to allocate on the outside stack
 * Return Value:
 *     the pointer to the allocated space on the outside stack
 *     NULL - fail to allocate
*/
void* SGXAPI sgx_ocalloc(size_t size);

/* sgx_ocfree()
 * Parameters:
 *      N/A
 * Return Value:
 *      N/A
*/
void SGXAPI sgx_ocfree(void);

/* sgx_ecall()
 * Parameters:
 *     eid         - the enclave id
 *     index       - the index of the trusted function
 *     ocall_table - the address of the OCALL table
 *     ms          - the pointer to the marshaling struct
 * Return Value:
 *     SGX_SUCCESS on success
*/
sgx_status_t SGXAPI sgx_ecall(const sgx_enclave_id_t eid,
                              const int index,
                              const void* ocall_table,
                              void* ms);

/* sgx_ocall()
 * Parameters:
 *     index       - the index of the untrusted function
 *     ms          - the pointer to the marshaling struct
 * Return Value:
 *     SGX_SUCCESS on success
*/
sgx_status_t SGXAPI sgx_ocall(const unsigned int index,
                              void* ms);

#ifdef __cplusplus
}
#endif

#endif /* !_SGX_EDGER8R_H_ */
