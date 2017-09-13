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
* Do NOT use this library in your actual product.
* The purpose of this sample library is to aid the debugging of a
* remote attestation service.
* To achieve that goal, the sample remote attestation application
* will use this sample library to generate reproducible messages.
* If you have still not decided on whether you should use this library in a
* released product, please refer to the implementation of __do_get_rand32.
**/

#include <string.h>
#include <stdlib.h>
#include <stdint.h>

#include "ippcp.h"


#include "sgx_memset_s.h"
#include "se_memcpy.h"

#include "sample_libcrypto.h"
#include <errno.h>

#ifdef __linux__
/*
 * __memset_vp is a volatile pointer to a function.
 * It is initialised to point to memset, and should never be changed.
 */
static void * (* const volatile __memset_vp)(void *, int, size_t)
    = (memset);

#undef memset_s /* in case it was defined as a macro */

extern "C" int memset_s(void *s, size_t smax, int c, size_t n)
{
    int err = 0;

    if (s == NULL) {
        err = EINVAL;
        goto out;
    }

    if (n > smax) {
        err = EOVERFLOW;
        n = smax;
    }

    /* Calling through a volatile pointer should never be optimised away. */
    (*__memset_vp)(s, c, n);

    out:
    if (err == 0)
        return 0;
    else {
        errno = err;
        /* XXX call runtime-constraint handler */
        return err;
    }
}
#endif

#ifndef ERROR_BREAK
#define ERROR_BREAK(x)  if(x){break;}
#endif

#ifndef SAFE_FREE
#define SAFE_FREE(ptr) {if (NULL != (ptr)) {free(ptr); (ptr) = NULL;}}
#endif

#ifndef ROUND_TO
#define ROUND_TO(x, align)	(((x) + (align-1)) & ~(align-1))
#endif

#ifndef UNUSED
#define UNUSED(val) (void)(val)
#endif



static uint32_t seed = (uint32_t)(9);

// We are using this very non-random definition for reproducibility / debugging purposes.
static inline sample_status_t  __do_get_rand32(uint32_t* rand_num)
{
    *rand_num = seed;
    return SAMPLE_SUCCESS;
}

static inline IppStatus check_copy_size(size_t target_size, size_t source_size)
{
    if(target_size < source_size)
        return ippStsSizeErr;
    return ippStsNoErr;
}

/* The function should generate a random number properly, and the pseudo-rand
     implementation is only for demo purpose. */
sample_status_t sample_read_rand(unsigned char *rand, size_t length_in_bytes)
{
    // check parameters
    if(!rand || !length_in_bytes)
    {
        return SAMPLE_ERROR_INVALID_PARAMETER;
    }
    // loop to rdrand
    while(length_in_bytes > 0)
    {
        uint32_t rand_num = 0;
        sample_status_t status = __do_get_rand32(&rand_num);
        if(status != SAMPLE_SUCCESS)
        {
            return status;
        }
        size_t size = (length_in_bytes < sizeof(rand_num))
            ? length_in_bytes : sizeof(rand_num);
        if(memcpy_s(rand, size, &rand_num, size))
        {
            return status;
        }

        rand += size;
        length_in_bytes -= size;
    }
    return SAMPLE_SUCCESS;
}

static IppStatus sgx_ipp_newBN(const Ipp32u *p_data, int size_in_bytes, IppsBigNumState **p_new_BN)
{
    IppsBigNumState *pBN=0;
    int bn_size = 0;

    if(p_new_BN == NULL || (size_in_bytes <= 0) || ((size_in_bytes % sizeof(Ipp32u)) != 0))
        return ippStsBadArgErr;

    // Get the size of the IppsBigNumState context in bytes
    IppStatus error_code = ippsBigNumGetSize(size_in_bytes/(int)sizeof(Ipp32u), &bn_size);
    if(error_code != ippStsNoErr)
    {
        *p_new_BN = 0;
        return error_code;
    }
    pBN = (IppsBigNumState *) malloc(bn_size);
    if(!pBN)
    {
        error_code = ippStsMemAllocErr;
        *p_new_BN = 0;
        return error_code;
    }
    // Initialize context and partition allocated buffer
    error_code = ippsBigNumInit(size_in_bytes/(int)sizeof(Ipp32u), pBN);
    if(error_code != ippStsNoErr)
    {
        free(pBN);
        *p_new_BN = 0;
        return error_code;
    }
    if(p_data)
    {
        error_code = ippsSet_BN(IppsBigNumPOS, size_in_bytes/(int)sizeof(Ipp32u), p_data, pBN);
        if(error_code != ippStsNoErr)
        {
            *p_new_BN = 0;
            free(pBN);
            return error_code;
        }
    }


    *p_new_BN = pBN;
    return error_code;
}

static void sample_ipp_secure_free_BN(IppsBigNumState *pBN, int size_in_bytes)
{
    if(pBN == NULL || size_in_bytes <= 0 || size_in_bytes/sizeof(Ipp32u) <= 0)
    {
        if(pBN)
        {
            free(pBN);
        }
        return;
    }
    int bn_size = 0;

    // Get the size of the IppsBigNumState context in bytes
    // Since we have checked the size_in_bytes before and the &bn_size is not NULL, ippsBigNumGetSize never returns failure
    ippsBigNumGetSize(size_in_bytes/(int)sizeof(Ipp32u), &bn_size);
    if (bn_size <= 0)
    {
        free(pBN);
        return;
    }
    // Clear the buffer before free.
    memset_s(pBN, bn_size, 0, bn_size);
    free(pBN);
    return;
}

IppStatus __STDCALL sample_ipp_DRNGen(Ipp32u* pRandBNU, int nBits, void* pCtx_unused)
{
    sample_status_t sample_ret;
    UNUSED(pCtx_unused);

    if(0 != nBits%8)
    {
        // Must be byte aligned
        return ippStsSizeErr;
    }

    if(!pRandBNU)
    {
        return ippStsNullPtrErr;
    }
    sample_ret = sample_read_rand((uint8_t*)pRandBNU, (uint32_t)nBits/8);
    if(SAMPLE_SUCCESS != sample_ret)
    {
        return ippStsErr;
    }
    return ippStsNoErr;
}



/* Rijndael AES-GCM
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Inputs: sample_aes_gcm_128bit_key_t *p_key - Pointer to key used in encryption/decryption operation
*           uint8_t *p_src - Pointer to input stream to be encrypted/decrypted
*           uint32_t src_len - Length of input stream to be encrypted/decrypted
*           uint8_t *p_iv - Pointer to initialization vector to use
*           uint32_t iv_len - Length of initialization vector
*           uint8_t *p_aad - Pointer to input stream of additional authentication data
*           uint32_t aad_len - Length of additional authentication data stream
*           sample_aes_gcm_128bit_tag_t *p_in_mac - Pointer to expected MAC in decryption process
*   Output: uint8_t *p_dst - Pointer to cipher text. Size of buffer should be >= src_len.
*           sample_aes_gcm_128bit_tag_t *p_out_mac - Pointer to MAC generated from encryption process
* NOTE: Wrapper is responsible for confirming decryption tag matches encryption tag */
sample_status_t sample_rijndael128GCM_encrypt(const sample_aes_gcm_128bit_key_t *p_key, const uint8_t *p_src, uint32_t src_len,
                                        uint8_t *p_dst, const uint8_t *p_iv, uint32_t iv_len, const uint8_t *p_aad, uint32_t aad_len,
                                        sample_aes_gcm_128bit_tag_t *p_out_mac)
{
    IppStatus error_code = ippStsNoErr;
    IppsAES_GCMState* pState = NULL;
    int ippStateSize = 0;

    if ((p_key == NULL) || ((src_len > 0) && (p_dst == NULL)) || ((src_len > 0) && (p_src == NULL))
        || (p_out_mac == NULL) || (iv_len != SAMPLE_AESGCM_IV_SIZE) || ((aad_len > 0) && (p_aad == NULL))
        || (p_iv == NULL) || ((p_src == NULL) && (p_aad == NULL)))
    {
        return SAMPLE_ERROR_INVALID_PARAMETER;
    }
    error_code = ippsAES_GCMGetSize(&ippStateSize);
    if (error_code != ippStsNoErr)
    {
        return SAMPLE_ERROR_UNEXPECTED;
    }
    pState = (IppsAES_GCMState*)malloc(ippStateSize);
    if(pState == NULL)
    {
        return SAMPLE_ERROR_OUT_OF_MEMORY;
    }
    error_code = ippsAES_GCMInit((const Ipp8u *)p_key, SAMPLE_AESGCM_KEY_SIZE, pState, ippStateSize);
    if (error_code != ippStsNoErr)
    {
        // Clear temp State before free.
        memset_s(pState, ippStateSize, 0, ippStateSize);
        free(pState);
        switch (error_code) 
        {
        case ippStsMemAllocErr: return SAMPLE_ERROR_OUT_OF_MEMORY; 
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SAMPLE_ERROR_INVALID_PARAMETER;
        default: return SAMPLE_ERROR_UNEXPECTED;
        } 
    }
    error_code = ippsAES_GCMStart(p_iv, SAMPLE_AESGCM_IV_SIZE, p_aad, aad_len, pState);
    if (error_code != ippStsNoErr)
    {
        // Clear temp State before free.
        memset_s(pState, ippStateSize, 0, ippStateSize);
        free(pState);
        switch (error_code) 
        { 
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SAMPLE_ERROR_INVALID_PARAMETER;
        default: return SAMPLE_ERROR_UNEXPECTED;
        } 
    }
	if (src_len > 0) {
        error_code = ippsAES_GCMEncrypt(p_src, p_dst, src_len, pState);
        if (error_code != ippStsNoErr)
        {
            // Clear temp State before free.
            memset_s(pState, ippStateSize, 0, ippStateSize);
            free(pState);
            switch (error_code) 
            { 
            case ippStsNullPtrErr: return SAMPLE_ERROR_INVALID_PARAMETER;
            default: return SAMPLE_ERROR_UNEXPECTED;
            } 
        }
    }
    error_code = ippsAES_GCMGetTag((Ipp8u *)p_out_mac, SAMPLE_AESGCM_MAC_SIZE, pState);
    if (error_code != ippStsNoErr)
    {
        // Clear temp State before free.
        memset_s(pState, ippStateSize, 0, ippStateSize);
        free(pState);
        switch (error_code) 
        { 
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SAMPLE_ERROR_INVALID_PARAMETER;
        default: return SAMPLE_ERROR_UNEXPECTED;
        } 
    }
    // Clear temp State before free.
    memset_s(pState, ippStateSize, 0, ippStateSize);
    free(pState);
    return SAMPLE_SUCCESS;
}


/* Message Authentication - Rijndael 128 CMAC
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Inputs: sample_cmac_128bit_key_t *p_key - Pointer to key used in encryption/decryption operation
*           uint8_t *p_src - Pointer to input stream to be MACed
*           uint32_t src_len - Length of input stream to be MACed
*   Output: sample_cmac_gcm_128bit_tag_t *p_mac - Pointer to resultant MAC */
sample_status_t sample_rijndael128_cmac_msg(const sample_cmac_128bit_key_t *p_key, const uint8_t *p_src,
                                      uint32_t src_len, sample_cmac_128bit_tag_t *p_mac)
{
    IppsAES_CMACState* pState = NULL;
    int ippStateSize = 0;
    IppStatus error_code = ippStsNoErr;

    if ((p_key == NULL) || (p_src == NULL) || (p_mac == NULL))
    {
        return SAMPLE_ERROR_INVALID_PARAMETER;
    }
    error_code = ippsAES_CMACGetSize(&ippStateSize);
    if (error_code != ippStsNoErr)
    {
        return SAMPLE_ERROR_UNEXPECTED;
    }
    pState = (IppsAES_CMACState*)malloc(ippStateSize);
    if(pState == NULL)
    {
        return SAMPLE_ERROR_OUT_OF_MEMORY;
    }
    error_code = ippsAES_CMACInit((const Ipp8u *)p_key, SAMPLE_CMAC_KEY_SIZE, pState, ippStateSize);
    if (error_code != ippStsNoErr)
    {
        // Clear temp State before free.
        memset_s(pState, ippStateSize, 0, ippStateSize);
        free(pState);
        switch (error_code) 
        {
        case ippStsMemAllocErr: return SAMPLE_ERROR_OUT_OF_MEMORY; 
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SAMPLE_ERROR_INVALID_PARAMETER;
        default: return SAMPLE_ERROR_UNEXPECTED;
        }       
    }
    error_code = ippsAES_CMACUpdate((const Ipp8u *)p_src, src_len, pState);
    if (error_code != ippStsNoErr)
    {
        // Clear temp State before free.
        memset_s(pState, ippStateSize, 0, ippStateSize);
        free(pState);
        switch (error_code) 
        { 
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SAMPLE_ERROR_INVALID_PARAMETER;
        default: return SAMPLE_ERROR_UNEXPECTED;
        } 
    }
    error_code = ippsAES_CMACFinal((Ipp8u *)p_mac, SAMPLE_CMAC_MAC_SIZE, pState);
    if (error_code != ippStsNoErr)
    {
        // Clear temp State before free.
        memset_s(pState, ippStateSize, 0, ippStateSize);
        free(pState);
        switch (error_code) 
        { 
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SAMPLE_ERROR_INVALID_PARAMETER;
        default: return SAMPLE_ERROR_UNEXPECTED;
        } 
    }
    // Clear temp State before free.
    memset_s(pState, ippStateSize, 0, ippStateSize);
    free(pState);
    return SAMPLE_SUCCESS;
}

extern "C" int some_function()
{
  return 1234;
}

/*
* Elliptic Curve Crytpography - Based on GF(p), 256 bit
*/
/* Allocates and initializes ecc context
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Output: sample_ecc_state_handle_t ecc_handle - Handle to ECC crypto system  */
sample_status_t sample_ecc256_open_context(sample_ecc_state_handle_t* ecc_handle)
{
    IppStatus ipp_ret = ippStsNoErr;
    IppsECCPState* p_ecc_state = NULL;
    // default use 256r1 parameter
    int ctx_size = 0;

    if (ecc_handle == NULL)
        return SAMPLE_ERROR_INVALID_PARAMETER;
    ipp_ret = ippsECCPGetSize(256, &ctx_size);
    if (ipp_ret != ippStsNoErr)
        return SAMPLE_ERROR_UNEXPECTED;
    p_ecc_state = (IppsECCPState*)(malloc(ctx_size));
    if (p_ecc_state == NULL)
        return SAMPLE_ERROR_OUT_OF_MEMORY;
    ipp_ret = ippsECCPInit(256, p_ecc_state);
    if (ipp_ret != ippStsNoErr)
    {
        SAFE_FREE(p_ecc_state);
        *ecc_handle = NULL;
        return SAMPLE_ERROR_UNEXPECTED;
    }
    ipp_ret = ippsECCPSetStd(IppECCPStd256r1, p_ecc_state);
    if (ipp_ret != ippStsNoErr)
    {
        SAFE_FREE(p_ecc_state);
        *ecc_handle = NULL;
        return SAMPLE_ERROR_UNEXPECTED;
    }
    *ecc_handle = p_ecc_state;
    return SAMPLE_SUCCESS;
}

/* Cleans up ecc context
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Output: sample_ecc_state_handle_t ecc_handle - Handle to ECC crypto system  */
sample_status_t sample_ecc256_close_context(sample_ecc_state_handle_t ecc_handle)
{
    if (ecc_handle == NULL)
    {
        return SAMPLE_ERROR_INVALID_PARAMETER;
    }
    IppsECCPState* p_ecc_state = (IppsECCPState*)ecc_handle;
    int ctx_size = 0;
    IppStatus ipp_ret = ippsECCPGetSize(256, &ctx_size);
    if (ipp_ret != ippStsNoErr)
    {
        free(p_ecc_state);
        return SAMPLE_SUCCESS;
    }
    memset_s(p_ecc_state, ctx_size, 0, ctx_size);
    free(p_ecc_state);
    return SAMPLE_SUCCESS;
}

/* Populates private/public key pair - caller code allocates memory
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Inputs: sample_ecc_state_handle_t ecc_handle - Handle to ECC crypto system
*   Outputs: sample_ec256_private_t *p_private - Pointer to the private key
*            sample_ec256_public_t *p_public - Pointer to the public key  */
sample_status_t sample_ecc256_create_key_pair(sample_ec256_private_t *p_private,
                                        sample_ec256_public_t *p_public,
                                        sample_ecc_state_handle_t ecc_handle)
{
    if ((ecc_handle == NULL) || (p_private == NULL) || (p_public == NULL))
    {
        return SAMPLE_ERROR_INVALID_PARAMETER;
    }

    IppsBigNumState*    dh_priv_BN = NULL;
    IppsECCPPointState* point_pub = NULL;
    IppsBigNumState*    pub_gx = NULL;
    IppsBigNumState*    pub_gy = NULL;
    IppStatus           ipp_ret = ippStsNoErr;
    int                 ecPointSize = 0;
    IppsECCPState* p_ecc_state = (IppsECCPState*)ecc_handle;

    do
    {
        //init eccp point
        ipp_ret = ippsECCPPointGetSize(256, &ecPointSize);
        ERROR_BREAK(ipp_ret);
        point_pub = (IppsECCPPointState*)( malloc(ecPointSize) );
        if(!point_pub)
        {
            ipp_ret = ippStsNoMemErr;
            break;
        }
        ipp_ret = ippsECCPPointInit(256, point_pub);
        ERROR_BREAK(ipp_ret);

        ipp_ret = sgx_ipp_newBN(NULL, SAMPLE_ECP256_KEY_SIZE, &dh_priv_BN);
        ERROR_BREAK(ipp_ret);
        // Use the true random number (DRNG)
        ipp_ret = ippsECCPGenKeyPair(dh_priv_BN, point_pub, p_ecc_state, (IppBitSupplier)sample_ipp_DRNGen, NULL);
        ERROR_BREAK(ipp_ret);

        //convert point_result to oct string
        ipp_ret = sgx_ipp_newBN(NULL, SAMPLE_ECP256_KEY_SIZE, &pub_gx);
        ERROR_BREAK(ipp_ret);
        ipp_ret = sgx_ipp_newBN(NULL, SAMPLE_ECP256_KEY_SIZE, &pub_gy);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsECCPGetPoint(pub_gx, pub_gy, point_pub, p_ecc_state);
        ERROR_BREAK(ipp_ret);

        IppsBigNumSGN sgn = IppsBigNumPOS;
        Ipp32u *pdata = NULL;
        // ippsRef_BN is in bits not bytes (versus old ippsGet_BN)
        int length = 0;
        ipp_ret = ippsRef_BN(&sgn, &length, &pdata, pub_gx);
        ERROR_BREAK(ipp_ret);
        memset(p_public->gx, 0, sizeof(p_public->gx));
        ipp_ret = check_copy_size(sizeof(p_public->gx), ROUND_TO(length, 8)/8);
        ERROR_BREAK(ipp_ret);
        memcpy(p_public->gx, pdata, ROUND_TO(length, 8)/8);
        ipp_ret = ippsRef_BN(&sgn, &length, &pdata, pub_gy);
        ERROR_BREAK(ipp_ret);
        memset(p_public->gy, 0, sizeof(p_public->gy));
        ipp_ret = check_copy_size(sizeof(p_public->gy), ROUND_TO(length, 8)/8);
        ERROR_BREAK(ipp_ret);
        memcpy(p_public->gy, pdata, ROUND_TO(length, 8)/8);
        ipp_ret = ippsRef_BN(&sgn, &length, &pdata, dh_priv_BN);
        ERROR_BREAK(ipp_ret);
        memset(p_private->r, 0, sizeof(p_private->r));
        ipp_ret = check_copy_size(sizeof(p_private->r), ROUND_TO(length, 8)/8);
        ERROR_BREAK(ipp_ret);
        memcpy(p_private->r, pdata, ROUND_TO(length, 8)/8);
    }while(0);

    //Clear temp buffer before free.
    if(point_pub) memset_s(point_pub, ecPointSize, 0, ecPointSize);
    SAFE_FREE(point_pub);
    sample_ipp_secure_free_BN(pub_gx, SAMPLE_ECP256_KEY_SIZE);
    sample_ipp_secure_free_BN(pub_gy, SAMPLE_ECP256_KEY_SIZE);
    sample_ipp_secure_free_BN(dh_priv_BN, SAMPLE_ECP256_KEY_SIZE);

    switch (ipp_ret)
    {
    case ippStsNoErr: return SAMPLE_SUCCESS;
    case ippStsNoMemErr:
    case ippStsMemAllocErr: return SAMPLE_ERROR_OUT_OF_MEMORY;
    case ippStsNullPtrErr:
    case ippStsLengthErr:
    case ippStsOutOfRangeErr:
    case ippStsSizeErr:
    case ippStsBadArgErr: return SAMPLE_ERROR_INVALID_PARAMETER;
    default: return SAMPLE_ERROR_UNEXPECTED;
    }
}




/* Computes DH shared key based on private B key (local) and remote public Ga Key
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Inputs: sample_ecc_state_handle_t ecc_handle - Handle to ECC crypto system
*           sample_ec256_private_t *p_private_b - Pointer to the local private key - LITTLE ENDIAN
*           sample_ec256_public_t *p_public_ga - Pointer to the remote public key - LITTLE ENDIAN
*   Output: sample_ec256_dh_shared_t *p_shared_key - Pointer to the shared DH key - LITTLE ENDIAN
*x-coordinate of (privKeyB - pubKeyA) */
sample_status_t sample_ecc256_compute_shared_dhkey(sample_ec256_private_t *p_private_b,
                                             sample_ec256_public_t *p_public_ga,
                                             sample_ec256_dh_shared_t *p_shared_key,
                                             sample_ecc_state_handle_t ecc_handle)
{
    if ((ecc_handle == NULL) || (p_private_b == NULL) || (p_public_ga == NULL) || (p_shared_key == NULL))
    {
        return SAMPLE_ERROR_INVALID_PARAMETER;
    }

    IppsBigNumState*    BN_dh_privB = NULL;
    IppsBigNumState*    BN_dh_share = NULL;
    IppsBigNumState*    pubA_gx = NULL;
    IppsBigNumState*    pubA_gy = NULL;
    IppsECCPPointState* point_pubA = NULL;
    IppStatus           ipp_ret = ippStsNoErr;
    int                 ecPointSize = 0;
    IppsECCPState* p_ecc_state = (IppsECCPState*)ecc_handle;
    IppECResult ipp_result = ippECValid;

    do
    {
        ipp_ret = sgx_ipp_newBN((Ipp32u*)p_private_b->r, sizeof(sample_ec256_private_t), &BN_dh_privB);
        ERROR_BREAK(ipp_ret);
        ipp_ret = sgx_ipp_newBN((uint32_t*)p_public_ga->gx, sizeof(p_public_ga->gx), &pubA_gx);
        ERROR_BREAK(ipp_ret);
        ipp_ret = sgx_ipp_newBN((uint32_t*)p_public_ga->gy, sizeof(p_public_ga->gy), &pubA_gy);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsECCPPointGetSize(256, &ecPointSize);
        ERROR_BREAK(ipp_ret);
        point_pubA = (IppsECCPPointState*)( malloc(ecPointSize) );
        if(!point_pubA)
        {
            ipp_ret = ippStsNoMemErr;
            break;
        }
        ipp_ret = ippsECCPPointInit(256, point_pubA);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsECCPSetPoint(pubA_gx, pubA_gy, point_pubA, p_ecc_state);
        ERROR_BREAK(ipp_ret);

        // Check to see if the point is a valid point on the Elliptic curve and is not infinity
        ipp_ret = ippsECCPCheckPoint(point_pubA, &ipp_result, p_ecc_state);
        if (ipp_result != ippECValid)
        {
            break;
        }
        ERROR_BREAK(ipp_ret);

        ipp_ret = sgx_ipp_newBN(NULL, sizeof(sample_ec256_dh_shared_t), &BN_dh_share);
        ERROR_BREAK(ipp_ret);
        /* This API generates shareA = x-coordinate of (privKeyB - pubKeyA) */
        ipp_ret = ippsECCPSharedSecretDH(BN_dh_privB, point_pubA, BN_dh_share, p_ecc_state);
        ERROR_BREAK(ipp_ret);
        IppsBigNumSGN sgn = IppsBigNumPOS;
        int length = 0;
        Ipp32u * pdata = NULL;
        ipp_ret = ippsRef_BN(&sgn, &length, &pdata, BN_dh_share);
        ERROR_BREAK(ipp_ret);
        memset(p_shared_key->s, 0, sizeof(p_shared_key->s));
        ipp_ret = check_copy_size(sizeof(p_shared_key->s), ROUND_TO(length, 8)/8);
        ERROR_BREAK(ipp_ret);
        memcpy(p_shared_key->s, pdata, ROUND_TO(length, 8)/8);
    }while(0);

    // Clear temp buffer before free.
    if(point_pubA)
        memset_s(point_pubA, ecPointSize, 0, ecPointSize);
    SAFE_FREE(point_pubA);
    sample_ipp_secure_free_BN(pubA_gx, sizeof(p_public_ga->gx));
    sample_ipp_secure_free_BN(pubA_gy, sizeof(p_public_ga->gy));
    sample_ipp_secure_free_BN(BN_dh_privB, sizeof(sample_ec256_private_t));
    sample_ipp_secure_free_BN(BN_dh_share, sizeof(sample_ec256_dh_shared_t));


    if (ipp_result != ippECValid)
    {
        return SAMPLE_ERROR_INVALID_PARAMETER;
    }
    switch (ipp_ret)
    {
    case ippStsNoErr: return SAMPLE_SUCCESS;
    case ippStsNoMemErr:
    case ippStsMemAllocErr: return SAMPLE_ERROR_OUT_OF_MEMORY;
    case ippStsNullPtrErr:
    case ippStsLengthErr:
    case ippStsOutOfRangeErr:
    case ippStsSizeErr:
    case ippStsBadArgErr: return SAMPLE_ERROR_INVALID_PARAMETER;
    default: return SAMPLE_ERROR_UNEXPECTED;
    }
}


const uint32_t sample_nistp256_r[] = {
    0xFC632551, 0xF3B9CAC2, 0xA7179E84, 0xBCE6FAAD, 0xFFFFFFFF, 0xFFFFFFFF,
    0x00000000, 0xFFFFFFFF};

#include <stdio.h>

/* Computes signature for data based on private key
* Parameters:
*   Return: sample_status_t - SAMPLE_SUCCESS, SAMPLE_SUCCESS on success, error code otherwise.
*   Inputs: sample_ecc_state_handle_t ecc_handle - Handle to ECC crypto system
*           sample_ec256_private_t *p_private - Pointer to the private key - LITTLE ENDIAN
*           sample_uint8_t *p_data - Pointer to the data to be signed
*           uint32_t data_size - Size of the data to be signed
*   Output: sample_ec256_signature_t *p_signature - Pointer to the signature - LITTLE ENDIAN  */
sample_status_t sample_ecdsa_sign(const uint8_t *p_data,
                            uint32_t data_size,
                            sample_ec256_private_t *p_private,
                            sample_ec256_signature_t *p_signature,
                            sample_ecc_state_handle_t ecc_handle)
{
    if ((ecc_handle == NULL) || (p_private == NULL) || (p_signature == NULL) || (p_data == NULL) || (data_size < 1))
    {
        return SAMPLE_ERROR_INVALID_PARAMETER;
    }

    IppStatus ipp_ret = ippStsNoErr;
    IppsECCPState* p_ecc_state = (IppsECCPState*)ecc_handle;
    IppsBigNumState* p_ecp_order = NULL;
    IppsBigNumState* p_hash_bn = NULL;
    IppsBigNumState* p_msg_bn = NULL;
    IppsBigNumState* p_eph_priv_bn = NULL;
    IppsECCPPointState* p_eph_pub = NULL;
    IppsBigNumState* p_reg_priv_bn = NULL;
    IppsBigNumState* p_signx_bn = NULL;
    IppsBigNumState* p_signy_bn = NULL;
    Ipp32u *p_sigx = NULL;
    Ipp32u *p_sigy = NULL;
    int ecp_size = 0;
    const int order_size = sizeof(sample_nistp256_r);
    uint32_t hash[8] = {0};

    do
    {

        ipp_ret = sgx_ipp_newBN(sample_nistp256_r, order_size, &p_ecp_order);
        ERROR_BREAK(ipp_ret);

        // Prepare the message used to sign.
        ipp_ret = ippsHashMessage(p_data, data_size, (Ipp8u*)hash, IPP_ALG_HASH_SHA256);
        ERROR_BREAK(ipp_ret);
        /* Byte swap in creation of Big Number from SHA256 hash output */
        ipp_ret = sgx_ipp_newBN(NULL, sizeof(hash), &p_hash_bn);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsSetOctString_BN((Ipp8u*)hash, sizeof(hash), p_hash_bn);
        ERROR_BREAK(ipp_ret);

        ipp_ret = sgx_ipp_newBN(NULL, order_size, &p_msg_bn);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsMod_BN(p_hash_bn, p_ecp_order, p_msg_bn);
        ERROR_BREAK(ipp_ret);

        // Get ephemeral key pair.
        ipp_ret = sgx_ipp_newBN(NULL, order_size, &p_eph_priv_bn);
        ERROR_BREAK(ipp_ret);
        //init eccp point
        ipp_ret = ippsECCPPointGetSize(256, &ecp_size);
        ERROR_BREAK(ipp_ret);
        p_eph_pub = (IppsECCPPointState*)(malloc(ecp_size));
        if(!p_eph_pub)
        {
            ipp_ret = ippStsNoMemErr;
            break;
        }
        ipp_ret = ippsECCPPointInit(256, p_eph_pub);
        ERROR_BREAK(ipp_ret);
        // generate ephemeral key pair for signing operation
        ipp_ret = ippsECCPGenKeyPair(p_eph_priv_bn, p_eph_pub, p_ecc_state,
            (IppBitSupplier)sample_ipp_DRNGen, NULL);
        ERROR_BREAK(ipp_ret);
        ipp_ret = ippsECCPSetKeyPair(p_eph_priv_bn, p_eph_pub, ippFalse, p_ecc_state);
        ERROR_BREAK(ipp_ret);

        // Set the regular private key.
        ipp_ret = sgx_ipp_newBN((uint32_t *)p_private->r, sizeof(p_private->r),
            &p_reg_priv_bn);
        ERROR_BREAK(ipp_ret);
        ipp_ret = sgx_ipp_newBN(NULL, order_size, &p_signx_bn);
        ERROR_BREAK(ipp_ret);
        ipp_ret = sgx_ipp_newBN(NULL, order_size, &p_signy_bn);
        ERROR_BREAK(ipp_ret);

        // Sign the message.
        ipp_ret = ippsECCPSignDSA(p_msg_bn, p_reg_priv_bn, p_signx_bn, p_signy_bn,
            p_ecc_state);
        ERROR_BREAK(ipp_ret);

        IppsBigNumSGN sign;
        int length;
        ipp_ret = ippsRef_BN(&sign, &length,(Ipp32u**) &p_sigx, p_signx_bn);
        ERROR_BREAK(ipp_ret);
        memset(p_signature->x, 0, sizeof(p_signature->x));
        ipp_ret = check_copy_size(sizeof(p_signature->x), ROUND_TO(length, 8)/8);
        ERROR_BREAK(ipp_ret);
        memcpy(p_signature->x, p_sigx, ROUND_TO(length, 8)/8);
        memset_s(p_sigx, sizeof(p_signature->x), 0, ROUND_TO(length, 8)/8);
        ipp_ret = ippsRef_BN(&sign, &length,(Ipp32u**) &p_sigy, p_signy_bn);
        ERROR_BREAK(ipp_ret);
        memset(p_signature->y, 0, sizeof(p_signature->y));
        ipp_ret = check_copy_size(sizeof(p_signature->y), ROUND_TO(length, 8)/8);
        ERROR_BREAK(ipp_ret);
        memcpy(p_signature->y, p_sigy, ROUND_TO(length, 8)/8);
        memset_s(p_sigy, sizeof(p_signature->y), 0, ROUND_TO(length, 8)/8);        

    }while(0);

    // Clear buffer before free.
    if(p_eph_pub)
        memset_s(p_eph_pub, ecp_size, 0, ecp_size);
    SAFE_FREE(p_eph_pub);
    sample_ipp_secure_free_BN(p_ecp_order, order_size);
    sample_ipp_secure_free_BN(p_hash_bn, sizeof(hash));
    sample_ipp_secure_free_BN(p_msg_bn, order_size);
    sample_ipp_secure_free_BN(p_eph_priv_bn, order_size);
    sample_ipp_secure_free_BN(p_reg_priv_bn, sizeof(p_private->r));
    sample_ipp_secure_free_BN(p_signx_bn, order_size);
    sample_ipp_secure_free_BN(p_signy_bn, order_size);

    switch (ipp_ret)
    {
    case ippStsNoErr: return SAMPLE_SUCCESS;
    case ippStsNoMemErr:
    case ippStsMemAllocErr: return SAMPLE_ERROR_OUT_OF_MEMORY;
    case ippStsNullPtrErr:
    case ippStsLengthErr:
    case ippStsOutOfRangeErr:
    case ippStsSizeErr:
    case ippStsBadArgErr: return SAMPLE_ERROR_INVALID_PARAMETER;
    default: return SAMPLE_ERROR_UNEXPECTED;
    }
}

/* Allocates and initializes sha256 state
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Output: sample_sha_state_handle_t sha_handle - Handle to the SHA256 state  */
sample_status_t sample_sha256_init(sample_sha_state_handle_t* p_sha_handle)
{
    IppStatus ipp_ret = ippStsNoErr;
    IppsHashState* p_temp_state = NULL;

    if (p_sha_handle == NULL)
        return SAMPLE_ERROR_INVALID_PARAMETER;

    int ctx_size = 0;
    ipp_ret = ippsHashGetSize(&ctx_size);
    if (ipp_ret != ippStsNoErr)
        return SAMPLE_ERROR_UNEXPECTED;
    p_temp_state = (IppsHashState*)(malloc(ctx_size));
    if (p_temp_state == NULL)
        return SAMPLE_ERROR_OUT_OF_MEMORY;
    ipp_ret = ippsHashInit(p_temp_state, IPP_ALG_HASH_SHA256);
    if (ipp_ret != ippStsNoErr)
    {
        SAFE_FREE(p_temp_state);
        *p_sha_handle = NULL;
        switch (ipp_ret) 
        {
        case ippStsNullPtrErr:
        case ippStsLengthErr: return SAMPLE_ERROR_INVALID_PARAMETER;
        default: return SAMPLE_ERROR_UNEXPECTED;
        } 
    }

    *p_sha_handle = p_temp_state;
    return SAMPLE_SUCCESS;
}

/* Updates sha256 has calculation based on the input message
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Input:  sample_sha_state_handle_t sha_handle - Handle to the SHA256 state
*           uint8_t *p_src - Pointer to the input stream to be hashed
*           uint32_t src_len - Length of the input stream to be hashed  */
sample_status_t sample_sha256_update(const uint8_t *p_src, uint32_t src_len, sample_sha_state_handle_t sha_handle)
{
    if ((p_src == NULL) || (sha_handle == NULL))
    {
        return SAMPLE_ERROR_INVALID_PARAMETER;
    }
    IppStatus ipp_ret = ippStsNoErr;
    ipp_ret = ippsHashUpdate(p_src, src_len, (IppsHashState*)sha_handle);
    switch (ipp_ret) 
    {
    case ippStsNoErr: return SAMPLE_SUCCESS;
    case ippStsNullPtrErr:
    case ippStsLengthErr: return SAMPLE_ERROR_INVALID_PARAMETER;
    default: return SAMPLE_ERROR_UNEXPECTED;
    }
}

/* Returns Hash calculation
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Input:  sample_sha_state_handle_t sha_handle - Handle to the SHA256 state
*   Output: sample_sha256_hash_t *p_hash - Resultant hash from operation  */
sample_status_t sample_sha256_get_hash(sample_sha_state_handle_t sha_handle, sample_sha256_hash_t *p_hash)
{
    if ((sha_handle == NULL) || (p_hash == NULL))
    {
        return SAMPLE_ERROR_INVALID_PARAMETER;
    }
    IppStatus ipp_ret = ippStsNoErr;
    ipp_ret = ippsHashGetTag((Ipp8u*)p_hash, SAMPLE_SHA256_HASH_SIZE, (IppsHashState*)sha_handle);
    switch (ipp_ret) 
    {
    case ippStsNoErr: return SAMPLE_SUCCESS;
    case ippStsNullPtrErr:
    case ippStsLengthErr: return SAMPLE_ERROR_INVALID_PARAMETER;
    default: return SAMPLE_ERROR_UNEXPECTED;
    }
}

/* Cleans up sha state
* Parameters:
*   Return: sample_status_t  - SAMPLE_SUCCESS on success, error code otherwise.
*   Input:  sample_sha_state_handle_t sha_handle - Handle to the SHA256 state  */
sample_status_t sample_sha256_close(sample_sha_state_handle_t sha_handle)
{
    if (sha_handle == NULL)
    {
        return SAMPLE_ERROR_INVALID_PARAMETER;
    }
    SAFE_FREE(sha_handle);
    return SAMPLE_SUCCESS;
}


