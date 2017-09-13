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
*     ipp_rsa_key.cpp
*Description: 
*     Wrapper for rsa key operation functions (public key generation and free excluded)
* 
*/

#include "ipp_wrapper.h"
#include "util.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

extern "C" int memset_s(void *s, size_t smax, int c, size_t n);

static IppStatus newPRNG(IppsPRNGState **pRandGen)
{
    if(pRandGen == NULL)
        return ippStsBadArgErr;
    int ctxSize = 0;
    IppStatus error_code = ippsPRNGGetSize(&ctxSize);
    if(error_code != ippStsNoErr)
        return error_code;
    IppsPRNGState* pCtx = (IppsPRNGState *) malloc(ctxSize);
    if(pCtx == NULL)
        return ippStsMemAllocErr; 

    error_code = ippsPRNGInit(160, pCtx);
    if(error_code != ippStsNoErr)
    {
        free(pCtx);
        return error_code;
    }

    *pRandGen = pCtx;
    return error_code;
}

static IppStatus newPrimeGen(int nMaxBits, IppsPrimeState ** pPrimeG)
{
    if(pPrimeG == NULL || nMaxBits <= 0 )
        return ippStsBadArgErr;
    int ctxSize = 0;
    IppStatus error_code = ippsPrimeGetSize(nMaxBits, &ctxSize);
    if(error_code != ippStsNoErr)
        return error_code;
    IppsPrimeState* pCtx = (IppsPrimeState *) malloc(ctxSize);
    if(pCtx == NULL)
        return ippStsMemAllocErr; 

    error_code = ippsPrimeInit(nMaxBits, pCtx);
    if(error_code != ippStsNoErr)
    {
        free(pCtx);
        return error_code;
    }

    *pPrimeG = pCtx;
    return error_code;
}


extern "C" IppStatus create_rsa_priv2_key(int p_byte_size, const Ipp32u *p, const Ipp32u *q,
                                          const Ipp32u *dmp1, const Ipp32u *dmq1, const Ipp32u *iqmp,
                                          IppsRSAPrivateKeyState **new_pri_key2)
{
    IppsRSAPrivateKeyState *p_rsa2 = NULL;
    IppsBigNumState *p_p = NULL, *p_q = NULL, *p_dmp1 = NULL, *p_dmq1 = NULL, *p_iqmp = NULL;
    int rsa2_size = 0;

    if(p_byte_size <= 0 || p == NULL || q == NULL || dmp1 == NULL || dmq1 == NULL || iqmp == NULL || new_pri_key2 == NULL)
    {
        return ippStsBadArgErr;
    }

    IppStatus error_code = ippStsNoErr;
    do{
        error_code = newBN(p, p_byte_size, &p_p);
        ERROR_BREAK(error_code);
        error_code = newBN(q, p_byte_size, &p_q);
        ERROR_BREAK(error_code);
        error_code = newBN(dmp1, p_byte_size, &p_dmp1);
        ERROR_BREAK(error_code);
        error_code = newBN(dmq1, p_byte_size, &p_dmq1);
        ERROR_BREAK(error_code);
        error_code = newBN(iqmp, p_byte_size, &p_iqmp);
        ERROR_BREAK(error_code);
        error_code = ippsRSA_GetSizePrivateKeyType2(p_byte_size * 8, p_byte_size * 8, &rsa2_size);
        ERROR_BREAK(error_code);
        p_rsa2 = (IppsRSAPrivateKeyState *)malloc(rsa2_size);
        NULL_BREAK(p_rsa2);

        error_code = ippsRSA_InitPrivateKeyType2(p_byte_size * 8, p_byte_size * 8, p_rsa2, rsa2_size);
        ERROR_BREAK(error_code);
        error_code = ippsRSA_SetPrivateKeyType2(p_p, p_q, p_dmp1, p_dmq1, p_iqmp, p_rsa2);
        ERROR_BREAK(error_code);
    }while(0);

    secure_free_BN(p_p, p_byte_size);
    secure_free_BN(p_q, p_byte_size);
    secure_free_BN(p_dmp1, p_byte_size);
    secure_free_BN(p_dmq1, p_byte_size);
    secure_free_BN(p_iqmp, p_byte_size);

    if(error_code != ippStsNoErr || p_rsa2 == NULL)
    {
        if(error_code == ippStsNoErr )
            error_code = ippStsMemAllocErr;

        /* Clear sensitive data before free */
        secure_free_rsa_pri2_key(p_byte_size, p_rsa2);
        return error_code;
    }

    *new_pri_key2 = p_rsa2;
    return error_code;
}

extern "C" IppStatus create_rsa_priv1_key(int n_byte_size, int d_byte_size, const Ipp32u *n, const Ipp32u *d, IppsRSAPrivateKeyState **new_pri_key1)
{
    IppsRSAPrivateKeyState *p_rsa1 = NULL;
    IppsBigNumState *p_n = NULL, *p_d = NULL;
    int rsa1_size = 0;
    if(n_byte_size <= 0 || d_byte_size <= 0 || n == NULL || d == NULL || new_pri_key1 == NULL)
    {
        return ippStsBadArgErr;
    }
    IppStatus error_code = ippStsNoErr;
    do{
        error_code = newBN(n, n_byte_size, &p_n);
        ERROR_BREAK(error_code);
        error_code = newBN(d, d_byte_size, &p_d);
        ERROR_BREAK(error_code);

        error_code = ippsRSA_GetSizePrivateKeyType1(n_byte_size * 8, d_byte_size * 8, &rsa1_size);
        ERROR_BREAK(error_code);
        p_rsa1 = (IppsRSAPrivateKeyState *)malloc(rsa1_size);
        NULL_BREAK(p_rsa1);
        error_code = ippsRSA_InitPrivateKeyType1(n_byte_size * 8, d_byte_size * 8, p_rsa1, rsa1_size);
        ERROR_BREAK(error_code);
        error_code = ippsRSA_SetPrivateKeyType1(p_n, p_d, p_rsa1);
        ERROR_BREAK(error_code);
    }while(0);
    secure_free_BN(p_n, n_byte_size);
    secure_free_BN(p_d, d_byte_size);
    if(error_code != ippStsNoErr || p_rsa1 == NULL)
    {
        if(error_code == ippStsNoErr )
            error_code = ippStsMemAllocErr;

        /* Clear sensitive data before free */
        secure_free_rsa_pri1_key(n_byte_size, d_byte_size, p_rsa1);
        return error_code;
    }

    *new_pri_key1 = p_rsa1;
    return error_code;
}


extern "C" IppStatus create_validate_rsa_key_pair(int n_byte_size, int e_byte_size, const Ipp32u *n, const Ipp32u *d, const Ipp32u *e, const Ipp32u *p, const Ipp32u *q, 
                                                  const Ipp32u *dmp1, const Ipp32u *dmq1, const Ipp32u *iqmp,
                                                  IppsRSAPrivateKeyState **new_pri_key, IppsRSAPublicKeyState **new_pub_key, int *validate_result)
{
    if(n_byte_size <= 0 || e_byte_size <= 0 || n == NULL || d == NULL ||  e == NULL || 
        p == NULL ||  q == NULL ||  dmp1 == NULL ||  dmq1 == NULL ||  iqmp == NULL || new_pri_key == NULL || 
        new_pub_key == NULL || validate_result == NULL)
    {
        return ippStsBadArgErr;
    }
    IppsRSAPrivateKeyState *p_pri_key1 = NULL, *p_pri_key2 = NULL;
    IppsRSAPublicKeyState *p_pub_key = NULL;
    IppStatus error_code = ippStsNoErr;
    IppsPRNGState *p_rand = NULL;
    IppsPrimeState *p_prime = NULL;
    Ipp8u * scratch_buffer = NULL;
    int result = IPP_IS_VALID;
    int max_size = 0, pri1_size = 0, pri2_size = 0, pub_size = 0; 

    do
    {
        /* Generate the pri_key1, pri_key2 and pub_key */
        error_code = create_rsa_priv1_key(n_byte_size, n_byte_size, n, d, &p_pri_key1);
        ERROR_BREAK(error_code);
        error_code = create_rsa_priv2_key(n_byte_size/2, p, q, dmp1, dmq1, iqmp, &p_pri_key2);
        ERROR_BREAK(error_code);
        error_code = create_rsa_pub_key(n_byte_size, e_byte_size, n, e, &p_pub_key);
        ERROR_BREAK(error_code);

        /* Generate random state and prime state */
        error_code = newPRNG(&p_rand);
        ERROR_BREAK(error_code);
        error_code = newPrimeGen(n_byte_size * 8 / 2, &p_prime);
        ERROR_BREAK(error_code);

        /* Allocate scratch buffer */
        error_code = ippsRSA_GetBufferSizePrivateKey(&pri1_size, p_pri_key1);
        ERROR_BREAK(error_code);
        error_code = ippsRSA_GetBufferSizePrivateKey(&pri2_size, p_pri_key2);
        ERROR_BREAK(error_code);
        max_size = MAX(pri1_size, pri2_size);
        error_code = ippsRSA_GetBufferSizePublicKey(&pub_size, p_pub_key);
        ERROR_BREAK(error_code);
        max_size = MAX(max_size, pub_size);
        scratch_buffer = (Ipp8u *)malloc(max_size);
        NULL_BREAK(scratch_buffer);
        memset(scratch_buffer, 0, max_size);

        /* Validate keys */
        error_code = ippsRSA_ValidateKeys(&result, p_pub_key, p_pri_key2, p_pri_key1, scratch_buffer, 10, p_prime, ippsPRNGen, p_rand);
        ERROR_BREAK(error_code);
    }while(0);
    SAFE_FREE_MM(p_rand);
    SAFE_FREE_MM(p_prime);
    secure_free_rsa_pri2_key(n_byte_size/2, p_pri_key2); 

    if(error_code != ippStsNoErr || scratch_buffer == NULL)
    {
        if(error_code == ippStsNoErr)
            error_code = ippStsMemAllocErr;

        SAFE_FREE_MM(scratch_buffer);
        secure_free_rsa_pri1_key(n_byte_size, n_byte_size, p_pri_key1);
        secure_free_rsa_pub_key(n_byte_size, e_byte_size, p_pub_key);
        return error_code;
    }
    SAFE_FREE_MM(scratch_buffer);
    *new_pri_key = p_pri_key1;
    *new_pub_key = p_pub_key;
    *validate_result = result;
    return error_code;
}

extern "C" IppStatus get_pub_key(const IppsRSAPublicKeyState *pub_key, int *e_byte_size, Ipp32u *e, int *n_byte_size, Ipp32u *n)
{
    IppStatus error_code = ippStsNoErr;
    IppsBigNumState *p_n=NULL, *p_e=NULL;

    if(!pub_key || !e_byte_size || !e || !n_byte_size || !n)
    {
        return ippStsBadArgErr;
    }
    do
    {
        error_code = newBN(NULL, SE_KEY_SIZE, &p_n);
        ERROR_BREAK(error_code);
        error_code = newBN(NULL, sizeof(Ipp32u), &p_e);
        ERROR_BREAK(error_code);

        error_code = ippsRSA_GetPublicKey(p_n, p_e, pub_key);
        ERROR_BREAK(error_code);

        IppsBigNumSGN sgn = IppsBigNumPOS;
        Ipp32u *pdata = NULL;
        int length_in_bit = 0;


        error_code = ippsRef_BN(&sgn, &length_in_bit, &pdata, p_n);
        ERROR_BREAK(error_code);
        *n_byte_size = ROUND_TO(length_in_bit, 8)/8;
        memset(n, 0, *n_byte_size);
        memcpy(n, pdata, ROUND_TO(length_in_bit, 8)/8);

        error_code = ippsRef_BN(&sgn, &length_in_bit, &pdata, p_e);
        ERROR_BREAK(error_code);
        *e_byte_size = ROUND_TO(length_in_bit, 8)/8;
        memset(e, 0, *e_byte_size);
        memcpy(e, pdata, ROUND_TO(length_in_bit, 8)/8);
    } while(0);

    secure_free_BN(p_n, SE_KEY_SIZE);
    secure_free_BN(p_e, sizeof(Ipp32u));
    return error_code;
}

extern "C" void secure_free_rsa_pri1_key(int n_byte_size, int d_byte_size, IppsRSAPrivateKeyState *pri_key1)
{
    if(n_byte_size <= 0 || d_byte_size <= 0 || pri_key1 == NULL)
    {
        if(pri_key1)
            free(pri_key1);
        return;
    }

    int rsa1_size = 0;
    if(ippsRSA_GetSizePrivateKeyType1(n_byte_size * 8, d_byte_size * 8, &rsa1_size) != ippStsNoErr)
    {
        free(pri_key1);
        return;
    }
    /* Clear the buffer before free. */
    memset_s(pri_key1, rsa1_size, 0, rsa1_size);
    free(pri_key1);
    return;
}

extern "C" void secure_free_rsa_pri2_key(int p_byte_size, IppsRSAPrivateKeyState *pri_key2)
{
    if(p_byte_size <= 0 || pri_key2 == NULL)
    {
        if(pri_key2)
            free(pri_key2);
        return;
    }

    int rsa2_size = 0;
    if(ippsRSA_GetSizePrivateKeyType2(p_byte_size * 8, p_byte_size * 8, &rsa2_size) != ippStsNoErr)
    {
        free(pri_key2);
        return;
    }
    /* Clear the buffer before free. */
    memset_s(pri_key2, rsa2_size, 0, rsa2_size);
    free(pri_key2);
    return;
}
