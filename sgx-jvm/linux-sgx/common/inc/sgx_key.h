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
 *	This file is to define Enclave's keys
*/

#ifndef _SGX_KEY_H_
#define _SGX_KEY_H_

#include <stdint.h>
#include "sgx_attributes.h"

/* Key Name */
#define SGX_KEYSELECT_EINITTOKEN       0x0000
#define SGX_KEYSELECT_PROVISION        0x0001
#define SGX_KEYSELECT_PROVISION_SEAL   0x0002
#define SGX_KEYSELECT_REPORT           0x0003
#define SGX_KEYSELECT_SEAL             0x0004

/* Key Policy */
#define SGX_KEYPOLICY_MRENCLAVE        0x0001      /* Derive key using the enclave's ENCLAVE measurement register */
#define SGX_KEYPOLICY_MRSIGNER         0x0002      /* Derive key using the enclave's SINGER measurement register */

#define SGX_KEYID_SIZE    32
#define SGX_CPUSVN_SIZE   16

typedef uint8_t                    sgx_key_128bit_t[16];
typedef uint16_t                   sgx_isv_svn_t;

typedef struct _sgx_cpu_svn_t
{
    uint8_t                        svn[SGX_CPUSVN_SIZE];
} sgx_cpu_svn_t;

typedef struct _sgx_key_id_t
{
    uint8_t                        id[SGX_KEYID_SIZE];
} sgx_key_id_t;

#define SGX_KEY_REQUEST_RESERVED2_BYTES 436

typedef struct _key_request_t
{
    uint16_t                        key_name;        /* Identifies the key required */
    uint16_t                        key_policy;      /* Identifies which inputs should be used in the key derivation */
    sgx_isv_svn_t                   isv_svn;         /* Security Version of the Enclave */
    uint16_t                        reserved1;       /* Must be 0 */
    sgx_cpu_svn_t                   cpu_svn;         /* Security Version of the CPU */
    sgx_attributes_t                attribute_mask;  /* Mask which ATTRIBUTES Seal keys should be bound to */
    sgx_key_id_t                    key_id;          /* Value for key wear-out protection */
    sgx_misc_select_t               misc_mask;       /* Mask what MISCSELECT Seal keys bound to */
    uint8_t                         reserved2[SGX_KEY_REQUEST_RESERVED2_BYTES];  /* Struct size is 512 bytes */
} sgx_key_request_t;


#endif
