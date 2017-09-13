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
* File: cipher.cpp
* Description: Cpp file to wrap cipher related function from IPP for Provision Enclave 
*
* Wrap for ipp function like aes-gcm, epid private key parameter f generation.
*/

#include "cipher.h"
#include "helper.h"
#include "string.h"
#include "sgx_trts.h"
#include "ipp_wrapper.h"
#include "epid/common/errors.h"
#include "epid/member/api.h"
#include "provision_msg.h"
#include "ae_ipp.h"
#include "util.h"
#include "internal/se_memcpy.h"
#include <stdlib.h>

//Use macro to transform ipp error code into pve error code
#define IPP_ERROR_BREAK(x)  if((x) != ippStsNoErr){ret=ipp_error_to_pve_error(x);goto ret_point;}

#define PRIV_F_LOWER_BOUND      1LL
#define PRIV_F_EXTRA_RAND_BYTES 12
#define PRIV_F_RAND_SIZE        (PRIV_F_EXTRA_RAND_BYTES+sizeof(FpElemStr))
//Generate a random value for f to be part of EPID private key 
//The function will be called in ProvMsg3 of PvE 
//f should be a random value between PRIV_F_LOWER_BOUND and p_data-PRIV_F_LOWER_BOUND
//The f is set to PRIV_F_LOWER_BOUND + rand_num % (p_data-2*PRIV_F_LOWER_BOUND+1) 
//  where the rand_num is big enough so that the output is uniform distributed
//@f, buffer to hold the output value of f, the output will be in big endian
pve_status_t gen_epid_priv_f(
    FpElemStr* f)
{
    static uint8_t p_data[] = {//Parameter P in Epid2Params in big endian which is order(number of elements) of the ECC group used in EPID2 library
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0xF0, 0xCD,
        0x46, 0xE5, 0xF2, 0x5E, 0xEE, 0x71, 0xA4, 0x9E,
        0x0C, 0xDC, 0x65, 0xFB, 0x12, 0x99, 0x92, 0x1A,
        0xF6, 0x2D, 0x53, 0x6C, 0xD1, 0x0B, 0x50, 0x0D
    };

    pve_status_t ret = PVEC_SUCCESS;
    IppsBigNumState* f_BN = NULL;
    IppsBigNumState* p_BN = NULL;
    IppsBigNumState* r_BN = NULL;
    IppsBigNumState *h_BN = NULL;
    IppsBigNumState *d_BN = NULL;
    IppStatus ipp_status = ippStsNoErr;

    uint8_t f_temp_buf[PRIV_F_RAND_SIZE]; //buffer to hold random bits, it has 96 more bits than f or p
    uint64_t lower_bound = PRIV_F_LOWER_BOUND;
    uint64_t diff = 2*lower_bound-1;
    se_static_assert(sizeof(FpElemStr)%4==0); /*sizeof FpElemStr should be multiple of 4*/
    se_static_assert(PRIV_F_RAND_SIZE%4==0); /*the number of bytes of random number should be multiple of 4*/
    //First create the mod P which is in little endian
    ipp_status = newBN(NULL, sizeof(FpElemStr), &p_BN);//initialize integer buffer
    IPP_ERROR_BREAK(ipp_status);
    ipp_status = ippsSetOctString_BN(p_data, sizeof(FpElemStr), p_BN);//Input data in Bigendian format
    IPP_ERROR_BREAK(ipp_status);
    ipp_status = newBN(NULL, sizeof(FpElemStr), &r_BN);//create buffer to hold temp and output result
    IPP_ERROR_BREAK(ipp_status);
    //initialize a lower bound
    ipp_status = newBN(reinterpret_cast<Ipp32u *>(&lower_bound), sizeof(lower_bound), &h_BN);
    IPP_ERROR_BREAK(ipp_status);
    ipp_status = newBN(reinterpret_cast<Ipp32u *>(&diff), sizeof(diff), &d_BN);//2*PRIV_F_LOWER_BOUND-1
    IPP_ERROR_BREAK(ipp_status);
    //random generate a number f with 96 bits extra data
    //   to make sure the output result f%(p_data-(2*PRIV_F_LOWER_BOUND-1)) is uniform distributed
    //   the extra bits should be at least 80 bits while ipps functions requires the bits to be time of 32 bits
    if((ret=pve_rng_generate(static_cast<uint32_t>(PRIV_F_RAND_SIZE*8) , f_temp_buf))!=PVEC_SUCCESS){
        goto ret_point;
    }
    ipp_status = ippsSub_BN(p_BN, d_BN, r_BN);// r = p_data - (2*PRIV_F_LOWER_BOUND-1)
    IPP_ERROR_BREAK(ipp_status);
    ipp_status = newBN(reinterpret_cast<Ipp32u*>(f_temp_buf), static_cast<uint32_t>(PRIV_F_RAND_SIZE), &f_BN);//create big number by f
    IPP_ERROR_BREAK(ipp_status);
    ipp_status = ippsMod_BN(f_BN, r_BN, p_BN); //calculate p_BN = f (mod r_BN=(p_data - (2*PRIV_F_LOWER_BOUND-1)))
    IPP_ERROR_BREAK(ipp_status);
    ipp_status = ippsAdd_BN(p_BN, h_BN, r_BN); //r_BN = f (mod p_data - (2*PRIV_F_LOWER_BOUND-1)) + PRIV_F_LOWER_BOUND;
    IPP_ERROR_BREAK(ipp_status);
    //output the result and transform it into big endian
    ipp_status = ippsGetOctString_BN(reinterpret_cast<uint8_t *>(f), sizeof(FpElemStr),r_BN);
    IPP_ERROR_BREAK(ipp_status);
ret_point:
    (void)memset_s(f_temp_buf, sizeof(f_temp_buf), 0, sizeof(f_temp_buf));
    secure_free_BN(h_BN, sizeof(lower_bound));//free big integer securely (The function will also memset_s the buffer)
    secure_free_BN(f_BN, static_cast<uint32_t>(PRIV_F_RAND_SIZE));
    secure_free_BN(p_BN, sizeof(FpElemStr));
    secure_free_BN(r_BN,sizeof(FpElemStr));
    secure_free_BN(d_BN, sizeof(diff));

    return ret;
}

pve_status_t pve_aes_gcm_encrypt_init(
    const uint8_t *key,
    const uint8_t *iv, //input initial vector. randomly generated value and encryption of different msg should use different iv
    uint32_t iv_len,   //length of initial vector, usually IV_SIZE
    const uint8_t *aad,//AAD of AES-GCM, it could be NULL
    uint32_t aad_len,  //length of bytes of AAD
    IppsAES_GCMState **aes_gcm_state, //state buffer to return
    uint32_t *state_buffer_size)  //return buffer size which will be used by fini function
{
    int state_size = 0;
    pve_status_t ret = PVEC_SUCCESS;
    IppStatus status = ippStsNoErr;
    IppsAES_GCMState *p_state = NULL;
    status=ippsAES_GCMGetSize(&state_size);
    IPP_ERROR_BREAK(status);
    p_state = reinterpret_cast<IppsAES_GCMState *>(malloc(state_size));
    if(p_state == NULL){
        ret = PVEC_MALLOC_ERROR;
        goto ret_point;
    }
    status=ippsAES_GCMInit(key, 16, p_state, state_size);
    IPP_ERROR_BREAK(status);
    status=ippsAES_GCMStart(iv, iv_len, aad, aad_len, p_state);
    IPP_ERROR_BREAK(status);
ret_point:
    if(ret != PVEC_SUCCESS && p_state != NULL){
        (void)memset_s(p_state, state_size, 0, state_size);
        free(p_state);
    }else{
        *state_buffer_size = state_size;
        *aes_gcm_state = p_state;
    }
    return ret;
}

#define BLOCK_SIZE 64

pve_status_t pve_aes_gcm_encrypt_inplace_update(
    IppsAES_GCMState *aes_gcm_state, //pointer to a state
    uint8_t *buf,   //start address to data before/after encryption
    uint32_t buf_len) //length of data
{
    uint32_t off = 0;
    uint8_t block[BLOCK_SIZE];
    IppStatus status = ippStsNoErr;
    memset(block, 0, sizeof(block));

    //In PvE, we should only use code with buf_len not too large.
    //The code in following loop will have integer overflow if buf_len is larger than or equal to 2^32-BLOCK_SIZE. (off+=BLOCK_SIZE)
    //For defense in depth, we add extra constrain that buf_len should not be too large
    if (buf_len > (1U << 31)){
        return PVEC_UNEXPECTED_ERROR;
    }

    for(off=0;off<buf_len; off+=BLOCK_SIZE){
        int enc_len = BLOCK_SIZE;
        if(off+BLOCK_SIZE>buf_len)
            enc_len = buf_len - off;
        if((status =ippsAES_GCMEncrypt(buf+off, block, enc_len, aes_gcm_state))!=ippStsNoErr){
            (void)memset_s(block, sizeof(block), 0, BLOCK_SIZE);
            return ipp_error_to_pve_error(status);
        }
        memcpy(buf+off, block, enc_len);
    }
    (void)memset_s(block,sizeof(block), 0, BLOCK_SIZE );
    return PVEC_SUCCESS;
}

pve_status_t pve_aes_gcm_get_mac(IppsAES_GCMState *aes_gcm_state,uint8_t *mac)
{
    IppStatus status=ippsAES_GCMGetTag(mac, MAC_SIZE, aes_gcm_state);
    return ipp_error_to_pve_error(status);
}

//aes_gcm encryption fini function
void pve_aes_gcm_encrypt_fini(
    IppsAES_GCMState *aes_gcm_state, //the state buffer
    uint32_t state_buffer_size)//size of the buffer, the function need it to free the memory
{
    if(aes_gcm_state!=NULL){
        (void)memset_s(aes_gcm_state, state_buffer_size, 0, state_buffer_size);
        free(aes_gcm_state);
    }
}

pve_status_t ipp_error_to_pve_error(IppStatus status)
{
    if(status == ippStsNoErr) return PVEC_SUCCESS;
    else if(status == ippStsMemAllocErr||
        status == ippStsNoMemErr) return PVEC_MALLOC_ERROR;
    else return PVEC_IPP_ERROR;//unknown or unexpected ipp error
}

