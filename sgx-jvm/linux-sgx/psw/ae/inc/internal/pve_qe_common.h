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


#ifndef _PVE_QE_COMMON_H_
#define _PVE_QE_COMMON_H_

#include "se_types.h"
#include "ae_ipp.h"
#include "sgx_tcrypto.h"

#ifdef  __cplusplus
extern "C" {
#endif

IppStatus new_std_256_ecp(IppsECCPState **pp_new_ecp);

void secure_free_std_256_ecp(IppsECCPState *p_ecp);


int __STDCALL epid_random_func(
 unsigned int *p_random_data,
 int bits,
 void* p_user_data);


#ifdef __cplusplus
 }
#endif

#if !defined(SWAP_ENDIAN_DW)
#define SWAP_ENDIAN_DW(dw)    ((((dw) & 0x000000ff) << 24)                  \
    | (((dw) & 0x0000ff00) << 8)                                            \
    | (((dw) & 0x00ff0000) >> 8)                                            \
    | (((dw) & 0xff000000) >> 24))
#endif

#if !defined(SWAP_ENDIAN_32B)
#define SWAP_ENDIAN_32B(ptr)                                                \
{                                                                           \
    unsigned int temp = 0;                                                  \
    temp = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[0]);                       \
    ((unsigned int*)(ptr))[0] = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[7]);  \
    ((unsigned int*)(ptr))[7] = temp;                                       \
    temp = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[1]);                       \
    ((unsigned int*)(ptr))[1] = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[6]);  \
    ((unsigned int*)(ptr))[6] = temp;                                       \
    temp = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[2]);                       \
    ((unsigned int*)(ptr))[2] = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[5]);  \
    ((unsigned int*)(ptr))[5] = temp;                                       \
    temp = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[3]);                       \
    ((unsigned int*)(ptr))[3] = SWAP_ENDIAN_DW(((unsigned int*)(ptr))[4]);  \
    ((unsigned int*)(ptr))[4] = temp;                                       \
}
#endif


#endif

