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

#ifndef _IPP_WRAPPER_H
#define _IPP_WRAPPER_H

#include "ippcp.h"

#ifndef SAFE_FREE_MM
#define SAFE_FREE_MM(ptr) {\
    if(ptr != NULL)     \
    {                   \
       free(ptr);       \
       ptr = NULL;      \
    }}
#endif

#ifndef ERROR_BREAK
#define ERROR_BREAK(x)  if(x != ippStsNoErr){break;}
#endif
#ifndef NULL_BREAK
#define NULL_BREAK(x)   if(!x){break;}
#endif

#ifdef  __cplusplus
extern "C" {
#endif

IppStatus newBN(const Ipp32u *data, int size_in_bytes, IppsBigNumState **p_new_BN);

IppStatus create_rsa_priv1_key(int n_byte_size, int d_byte_size, const Ipp32u *n, const Ipp32u *d, IppsRSAPrivateKeyState **new_pri_key1);

IppStatus create_rsa_priv2_key(int p_byte_size, const Ipp32u *p, const Ipp32u *q,
                                           const Ipp32u *dmp1, const Ipp32u *dmq1, const Ipp32u *iqmp,
                                           IppsRSAPrivateKeyState **new_pri_key2);

IppStatus create_rsa_pub_key(int n_byte_size, int e_byte_size, const Ipp32u *n, const Ipp32u *e, IppsRSAPublicKeyState **new_pub_key);

IppStatus create_validate_rsa_key_pair(int n_byte_size, int e_byte_size, const Ipp32u *n, const Ipp32u *d, const Ipp32u *e, const Ipp32u *p, const Ipp32u *q, 
                                                     const Ipp32u *dmp1, const Ipp32u *dmq1, const Ipp32u *iqmp,
                                                     IppsRSAPrivateKeyState **new_pri_key, IppsRSAPublicKeyState **new_pub_key, int *validate_result);

IppStatus get_pub_key(const IppsRSAPublicKeyState *pub_key, int *e_byte_size, Ipp32u *e, int *n_byte_size, Ipp32u *n);

void secure_free_BN(IppsBigNumState *pBN, int size_in_bytes);

void secure_free_rsa_pri1_key(int n_byte_size, int d_byte_size, IppsRSAPrivateKeyState *pri_key1);

void secure_free_rsa_pri2_key(int p_byte_size, IppsRSAPrivateKeyState *pri_key2);

void secure_free_rsa_pub_key(int n_byte_size, int e_byte_size, IppsRSAPublicKeyState *pub_key);



#ifdef  __cplusplus
}
#endif

#endif


