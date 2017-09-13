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



/**
 * File: sgx_utils.h
 * Description:
 *     Trusted library for SGX instructions
 */

#ifndef _SGX_UTILS_H_
#define _SGX_UTILS_H_

#include "sgx.h"
#include "sgx_defs.h"

#ifdef __cplusplus
extern "C" {
#endif

/*sgx_create_report
 *  Purpose: Create a cryptographic report of the enclave using the input information if any.
 *
 *  Parameters:
 *      target_info - [IN] pointer to the information of the target enclave.
 *      report_data - [IN] pointer to a set of data used for communication between the enclaves.
 *      report - [OUT] pointer to the cryptographic report of the enclave
 *
 *  Return value:
 *     sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h.
*/
sgx_status_t SGXAPI sgx_create_report(const sgx_target_info_t *target_info, const sgx_report_data_t *report_data, sgx_report_t *report);

/* sgx_verify_report
 * Purpose: Software verification for  the input report
 *
 *  Paramters:
 *      report - [IN] ponter to the cryptographic report to be verified.
 *
 *  Return value:
 *      sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h.
*/
sgx_status_t SGXAPI sgx_verify_report(const sgx_report_t *report);

/*sgx_get_key
 *  Purpose: Generate a 128-bit secret key with the input information.
 *
 *  Parameters:
 *      key_request - [IN] pointer to the sgx_key_request_t object used for selecting the appropriate key.
 *      key  - [OUT] Pointer to the buffer that receives the cryptographic key output.
 *
 *  Return value:
 *       sgx_status_t  - SGX_SUCCESS or failure as defined in sgx_error.h.
*/
sgx_status_t SGXAPI sgx_get_key(const sgx_key_request_t *key_request, sgx_key_128bit_t *key);

#ifdef __cplusplus
}
#endif

#endif
