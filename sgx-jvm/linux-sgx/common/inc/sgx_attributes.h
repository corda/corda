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

#ifndef _SGX_ATTRIBUTES_H_
#define _SGX_ATTRIBUTES_H_

#include <stdint.h>

/* Enclave Flags Bit Masks */
#define SGX_FLAGS_INITTED        0x0000000000000001ULL     /* If set, then the enclave is initialized */
#define SGX_FLAGS_DEBUG          0x0000000000000002ULL     /* If set, then the enclave is debug */
#define SGX_FLAGS_MODE64BIT      0x0000000000000004ULL     /* If set, then the enclave is 64 bit */
#define SGX_FLAGS_PROVISION_KEY  0x0000000000000010ULL     /* If set, then the enclave has access to provision key */
#define SGX_FLAGS_EINITTOKEN_KEY 0x0000000000000020ULL     /* If set, then the enclave has access to EINITTOKEN key */
#define SGX_FLAGS_RESERVED       (~(SGX_FLAGS_INITTED | SGX_FLAGS_DEBUG | SGX_FLAGS_MODE64BIT | SGX_FLAGS_PROVISION_KEY | SGX_FLAGS_EINITTOKEN_KEY))

/* XSAVE Feature Request Mask */
#define SGX_XFRM_LEGACY          0x0000000000000003ULL     /* Legacy XFRM which includes the basic feature bits required by SGX, x87 state(0x01) and SSE state(0x02) */
#define SGX_XFRM_AVX             0x0000000000000006ULL     /* AVX XFRM which includes AVX state(0x04) and SSE state(0x02) required by AVX */
#define SGX_XFRM_AVX512          0x00000000000000E6ULL     /* AVX-512 XFRM - not supported */
#define SGX_XFRM_MPX             0x0000000000000018ULL     /* MPX XFRM - not supported */

#define SGX_XFRM_RESERVED        (~(SGX_XFRM_LEGACY | SGX_XFRM_AVX))

typedef struct _attributes_t
{
    uint64_t      flags;
    uint64_t      xfrm;
} sgx_attributes_t;

/* define MISCSELECT - all bits are currently reserved */
typedef uint32_t    sgx_misc_select_t;

typedef struct _sgx_misc_attribute_t {
    sgx_attributes_t    secs_attr;
    sgx_misc_select_t   misc_select;
} sgx_misc_attribute_t;

#endif/* _SGX_ATTRIBUTES_H_ */
