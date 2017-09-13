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

#include "arch.h"
#include "pce_cert.h"
#include "aeerror.h"
#include "sgx_utils.h"
#include "ipp_wrapper.h"
#include <assert.h>
#include <string.h>
#include <stdlib.h>


//Function to get provisioning key using the provided PSVN
//If the psvn is NULL, both CPUSVN and ISVSVN is set to 0 (used for PPID generation only)
//Input: psvn, the psvn used to generate provisioning key
//Output: key, the provisioning key to return
//        return PVEC_SUCCESS on success
static ae_error_t get_provision_key(sgx_key_128bit_t *key, const psvn_t *psvn)
{
    sgx_status_t se_ret = SGX_SUCCESS;
    sgx_key_request_t wrap_key_req;

    //memset here will also set cpusvn isvsvn to 0 for the case when psvn==NULL
    memset(&wrap_key_req, 0, sizeof(sgx_key_request_t));
    if(psvn==NULL){
    }else{
        memcpy(&wrap_key_req.cpu_svn, &psvn->cpu_svn, sizeof(wrap_key_req.cpu_svn));
        memcpy(&wrap_key_req.isv_svn, &psvn->isv_svn, sizeof(wrap_key_req.isv_svn));
    }
    wrap_key_req.key_name = SGX_KEYSELECT_PROVISION; //provisioning key
    wrap_key_req.attribute_mask.xfrm = 0;
    wrap_key_req.misc_mask = 0xFFFFFFFF;
    wrap_key_req.attribute_mask.flags = ~SGX_FLAGS_MODE64BIT; //set all bits except the SGX_FLAGS_MODE64BIT

    se_ret = sgx_get_key(&wrap_key_req, key);
    if(SGX_SUCCESS != se_ret)
    {
        return AE_FAILURE;
    }
    return AE_SUCCESS;
}


ae_error_t get_ppid(ppid_t* ppid)
{
    sgx_key_128bit_t key_tmp;
    sgx_status_t sgx_status = SGX_SUCCESS;
    memset(&key_tmp, 0, sizeof(key_tmp));

    //get Provisioning Key with both CPUSVN and ISVSVN set to 0
    ae_error_t status = get_provision_key(&key_tmp, NULL);

    if(status != AE_SUCCESS){
        (void)memset_s(&key_tmp,sizeof(key_tmp), 0, sizeof(key_tmp));
        return status;
    }

    uint8_t content[16];
    memset(&content, 0, sizeof(content));
    //generate the mac as PPID
    se_static_assert(sizeof(sgx_cmac_128bit_key_t) == sizeof(sgx_key_128bit_t));
    se_static_assert(sizeof(sgx_cmac_128bit_tag_t) == sizeof(*ppid));
    if((sgx_status=sgx_rijndael128_cmac_msg(reinterpret_cast<const sgx_cmac_128bit_key_t *>(&key_tmp),  
        content, sizeof(content), reinterpret_cast<sgx_cmac_128bit_tag_t *>(ppid)))!=SGX_SUCCESS){
            if(sgx_status == SGX_ERROR_OUT_OF_MEMORY){
                status = AE_OUT_OF_MEMORY_ERROR;
            }else{
                status = AE_FAILURE;
            }
    }else{
        status = AE_SUCCESS;
    }
    (void)memset_s(&key_tmp,sizeof(key_tmp), 0, sizeof(key_tmp));//clear provisioning key in stack
    return status;
}


const uint32_t sgx_nistp256_r_m1[] = {//hard-coded value for n-1 where n is order of the ECC group used
    0xFC632550, 0xF3B9CAC2, 0xA7179E84, 0xBCE6FAAD, 0xFFFFFFFF, 0xFFFFFFFF,
    0x00000000, 0xFFFFFFFF};

#define HASH_DRBG_OUT_LEN 40 //320 bits
const char PAK_STRING[] = "PAK_KEY_DER";

ae_error_t get_pce_priv_key(
    const psvn_t* psvn,
    sgx_ec256_private_t* wrap_key)
{

    if( psvn == NULL)
        return AE_FAILURE;
    uint8_t content[16];
    sgx_cmac_128bit_tag_t block;
    sgx_status_t sgx_status = SGX_SUCCESS;
    ae_error_t status = AE_SUCCESS;
    sgx_key_128bit_t key_tmp;
    IppStatus ipp_status = ippStsNoErr;
    IppsBigNumState *bn_d=NULL;
    IppsBigNumState *bn_m=NULL;
    IppsBigNumState *bn_o=NULL;
    IppsBigNumState *bn_one=NULL;
    uint8_t hash_drg_output[HASH_DRBG_OUT_LEN];

    memset(&content, 0, sizeof(content));
    memset(&block, 0, sizeof(block));
    memset(&key_tmp, 0, sizeof(key_tmp));
    //1-11bytes: "PAK_KEY_DER"(ascii encoded)
    memcpy(content+1, PAK_STRING, 11);
    //14-15bytes: 0x0140 (Big Endian)
    content[14]=0x01;
    content[15]=0x40;

    status = get_provision_key(&key_tmp, psvn); //Generate Provisioning Key with respect to the psvn
    if(status != AE_SUCCESS){
        goto ret_point;
    }
    se_static_assert(sizeof(sgx_cmac_128bit_key_t) == sizeof(sgx_key_128bit_t));
    se_static_assert(2*sizeof(sgx_cmac_128bit_tag_t) <= HASH_DRBG_OUT_LEN && 3*sizeof(sgx_cmac_128bit_tag_t) >= HASH_DRBG_OUT_LEN);

    //Block 1 = AES-CMAC(Provisioning Key, PAK string with Counter = 0x01)
    content[0] = 0x01;
    if((sgx_status=sgx_rijndael128_cmac_msg(reinterpret_cast<const sgx_cmac_128bit_key_t *>(key_tmp),  
        content, sizeof(content), &block))!=SGX_SUCCESS){
            if(sgx_status == SGX_ERROR_OUT_OF_MEMORY){
                status = AE_OUT_OF_MEMORY_ERROR;
            }else{
                status = AE_FAILURE;
            }
            goto ret_point;
    }
    memcpy(hash_drg_output, block, sizeof(sgx_cmac_128bit_tag_t));

    //Block 2 = AES-CMAC(Provisioning Key, PAK string with Counter = 0x02)
    content[0] = 0x02;
    if((sgx_status=sgx_rijndael128_cmac_msg(reinterpret_cast<const sgx_cmac_128bit_key_t *>(key_tmp),  
        content, sizeof(content), &block))!=SGX_SUCCESS){
            if(sgx_status == SGX_ERROR_OUT_OF_MEMORY){
                status = AE_OUT_OF_MEMORY_ERROR;
            }else{
                status = AE_FAILURE;
            }
            goto ret_point;
    }
    memcpy(hash_drg_output + sizeof(sgx_cmac_128bit_tag_t), block, sizeof(sgx_cmac_128bit_tag_t));

    //Block 3 = AES-CMAC(Provisioning Key, PAK string with Counter = 0x03)
    content[0] = 0x03;
    if((sgx_status=sgx_rijndael128_cmac_msg(reinterpret_cast<const sgx_cmac_128bit_key_t *>(key_tmp),  
        content, sizeof(content), &block))!=SGX_SUCCESS){
            if(sgx_status == SGX_ERROR_OUT_OF_MEMORY){
                status = AE_OUT_OF_MEMORY_ERROR;
            }else{
                status = AE_FAILURE;
            }
            goto ret_point;
    }
    //PAK Seed = most significant 320 bits of (Block 1 || Block 2 || Block 3).
    memcpy(hash_drg_output + 2*sizeof(sgx_cmac_128bit_tag_t), block, HASH_DRBG_OUT_LEN - 2*sizeof(sgx_cmac_128bit_tag_t));

    Ipp32u i;
    for (i = 0; i<HASH_DRBG_OUT_LEN / 2; i++){//big endian to little endian
        hash_drg_output[i] ^= hash_drg_output[HASH_DRBG_OUT_LEN - 1 - i];
        hash_drg_output[HASH_DRBG_OUT_LEN-1-i] ^= hash_drg_output[i];
        hash_drg_output[i] ^= hash_drg_output[HASH_DRBG_OUT_LEN - 1 - i];
    }

#define IPP_ERROR_TRANS(ipp_status) \
    if ( ippStsMemAllocErr == ipp_status) \
    { \
        status = AE_OUT_OF_MEMORY_ERROR; \
        goto ret_point; \
    } \
    else if(ippStsNoErr != ipp_status){ \
        status = PCE_UNEXPECTED_ERROR; \
        goto ret_point; \
    }

    ipp_status = newBN(reinterpret_cast<Ipp32u *>(hash_drg_output), HASH_DRBG_OUT_LEN, &bn_d);
    IPP_ERROR_TRANS(ipp_status);
    ipp_status = newBN(reinterpret_cast<const Ipp32u *>(sgx_nistp256_r_m1), sizeof(sgx_nistp256_r_m1), &bn_m);//generate mod to be n-1 where n is order of ECC Group
    IPP_ERROR_TRANS(ipp_status);
    ipp_status = newBN(NULL, sizeof(sgx_nistp256_r_m1), &bn_o);//alloc memory for output
    IPP_ERROR_TRANS(ipp_status);
    ipp_status = ippsMod_BN(bn_d, bn_m, bn_o);
    IPP_ERROR_TRANS(ipp_status);
    i=1;
    ipp_status = newBN(&i, sizeof(uint32_t), &bn_one);//create big number 1
    IPP_ERROR_TRANS(ipp_status);

    ipp_status = ippsAdd_BN(bn_o, bn_one, bn_o);//added by 1
    IPP_ERROR_TRANS(ipp_status);

    se_static_assert(sizeof(sgx_nistp256_r_m1)==sizeof(sgx_ec256_private_t)); /*Unmatched size*/
    ipp_status = ippsGetOctString_BN(reinterpret_cast<Ipp8u *>(wrap_key), sizeof(sgx_nistp256_r_m1), bn_o);//output data in bigendian order
    IPP_ERROR_TRANS(ipp_status);
ret_point:
    if(NULL!=bn_d){
        secure_free_BN(bn_d, HASH_DRBG_OUT_LEN);
    }
    if(NULL!=bn_m){
        secure_free_BN(bn_m, sizeof(sgx_nistp256_r_m1));
    }
    if(NULL!=bn_o){
        secure_free_BN(bn_o, sizeof(sgx_nistp256_r_m1));
    }
    if(NULL!=bn_one){
        secure_free_BN(bn_one, sizeof(uint32_t));
    }
    (void)memset_s(&key_tmp, sizeof(key_tmp), 0, sizeof(key_tmp));
    (void)memset_s(&hash_drg_output, sizeof(hash_drg_output), 0, sizeof(hash_drg_output));
    (void)memset_s(&block, sizeof(block), 0, sizeof(block));
    if(status!=AE_SUCCESS){
        (void)memset_s(wrap_key,sizeof(sgx_ec256_private_t), 0 ,sizeof(sgx_ec256_private_t)); //clear private key in stack
    }
    return status;
}
