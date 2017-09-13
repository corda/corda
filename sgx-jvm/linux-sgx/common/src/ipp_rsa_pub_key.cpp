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
* File: 
*		ipp_rsa_pub_key.cpp
*Description: 
*		Wrapper for rsa public key generation and free
* 
*/

#include "ipp_wrapper.h"

#include <stdlib.h>
#include <string.h>

#ifndef _TLIBC_CDECL_
extern "C" int memset_s(void *s, size_t smax, int c, size_t n);
#endif

extern "C" IppStatus create_rsa_pub_key(int n_byte_size, int e_byte_size, const Ipp32u *n, const Ipp32u *e, IppsRSAPublicKeyState **new_pub_key)
{
    IppsRSAPublicKeyState *p_pub_key = NULL;
    IppsBigNumState *p_n = NULL, *p_e = NULL;
    int rsa_size = 0;
    if(n_byte_size <= 0 || e_byte_size <= 0 || n == NULL || e == NULL || new_pub_key == NULL)
    {
        return ippStsBadArgErr;
    }

    IppStatus error_code = ippStsNoErr;
    do{
        error_code = newBN(n, n_byte_size, &p_n);
        ERROR_BREAK(error_code);
        error_code = newBN(e, e_byte_size, &p_e);
        ERROR_BREAK(error_code);

        error_code = ippsRSA_GetSizePublicKey(n_byte_size * 8, e_byte_size * 8, &rsa_size);
        ERROR_BREAK(error_code);
        p_pub_key = (IppsRSAPublicKeyState *)malloc(rsa_size);
        NULL_BREAK(p_pub_key);
        error_code = ippsRSA_InitPublicKey(n_byte_size * 8, e_byte_size * 8, p_pub_key, rsa_size);
        ERROR_BREAK(error_code);
        error_code = ippsRSA_SetPublicKey(p_n, p_e, p_pub_key);
        ERROR_BREAK(error_code);
    }while(0);
    secure_free_BN(p_n, n_byte_size);
    secure_free_BN(p_e, e_byte_size);
    if(error_code != ippStsNoErr || p_pub_key == NULL)
    {
        if(error_code == ippStsNoErr )
            error_code = ippStsMemAllocErr;

        secure_free_rsa_pub_key(n_byte_size, e_byte_size, p_pub_key);
        return error_code;
    }

    *new_pub_key = p_pub_key;
    return error_code;

}

extern "C" void secure_free_rsa_pub_key(int n_byte_size, int e_byte_size, IppsRSAPublicKeyState *pub_key)
{
    if(n_byte_size <= 0 || e_byte_size <= 0 || pub_key == NULL)
    {
        if(pub_key)
            free(pub_key);
        return;
    }
    int rsa_size = 0;
    if(ippsRSA_GetSizePublicKey(n_byte_size * 8, e_byte_size * 8, &rsa_size) != ippStsNoErr)
    {
        free(pub_key);
        return;
    }
    /* Clear the buffer before free. */
    memset_s(pub_key, rsa_size, 0, rsa_size);
    free(pub_key);
    return;
}
