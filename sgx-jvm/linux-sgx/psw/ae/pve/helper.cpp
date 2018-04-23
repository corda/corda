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
* File: helper.cpp
* Description: Cpp file to some helper function to extract some enclave information
*
* Wrap functions to get PPID, PWK, PSID, PSVN, PSK and seal/unseal function
*/

#include "helper.h"
#include "string.h"
#include "sgx_error.h"
#include "sgx_utils.h"
#include "cipher.h"
#include "sgx_trts.h"
#include "sgx_tcrypto.h"
#include <stdlib.h>
#include "byte_order.h"
#include "util.h"


//Function to get provisioning key using the provided PSVN
//If the psvn is NULL, both CPUSVN and ISVSVN is set to 0 (used for PPID generation only)
//Input: psvn, the psvn used to generate provisioning key
//Output: key, the provisioning key to return
//        return PVEC_SUCCESS on success
static pve_status_t get_provision_key(sgx_key_128bit_t *key, const psvn_t *psvn)
{
    sgx_status_t se_ret = SGX_SUCCESS;
    sgx_key_request_t wrap_key_req;

    //memset here will also set cpusvn isvsvn to 0 for the case when psvn==NULL
    memset(&wrap_key_req, 0, sizeof(sgx_key_request_t));
    if(psvn==NULL){
        //keeping isv_svn and cpu_svn all 0 according to spec (this is for calcuation of PPID)
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
        return sgx_error_to_pve_error(se_ret);
    }
    return PVEC_SUCCESS;
}

pve_status_t get_ppid(ppid_t* ppid)
{
    sgx_key_128bit_t key_tmp;
    sgx_status_t sgx_status = SGX_SUCCESS;
    memset(&key_tmp, 0, sizeof(key_tmp));

    //get Provisioning Key with both CPUSVN and ISVSVN set to 0
    pve_status_t status = get_provision_key(&key_tmp, NULL);

    if(status != PVEC_SUCCESS){
        (void)memset_s(&key_tmp,sizeof(key_tmp), 0, sizeof(key_tmp));
        return status;
    }

    uint8_t content[16];
    memset(&content, 0, sizeof(content));
    

    //generate the mac as PPID
    se_static_assert(sizeof(sgx_cmac_128bit_key_t) == sizeof(sgx_key_128bit_t)); /*size of sgx_cmac_128bit_key_t and sgx_key_128bit_t should be same*/
    se_static_assert(sizeof(sgx_cmac_128bit_tag_t) == sizeof(ppid_t)); /*size of sgx_cmac_128bit_tag_t and ppit_t should be same*/
    if((sgx_status=sgx_rijndael128_cmac_msg(reinterpret_cast<const sgx_cmac_128bit_key_t *>(&key_tmp),  
        content, sizeof(content), reinterpret_cast<sgx_cmac_128bit_tag_t *>(ppid)))!=SGX_SUCCESS){
            status = sgx_error_to_pve_error(sgx_status);
    }else{
        status = PVEC_SUCCESS;
    }
    (void)memset_s(&key_tmp,sizeof(key_tmp), 0, sizeof(key_tmp));//clear provisioning key in stack
    return status;
}

#define PROV_WRAP_2            "PROV_WRAP_2"
#define PROV_WRAP_2_LEN        11
#define START_OFF_PROV_WRAP_2  1
#define START_OFF_NONCE_2      14
#define OFF_BYTE_ZERO          30
#define OFF_BYTE_0X80          31
//Get Provisioning Wrap2 Key with respect to the PSVN
pve_status_t get_pwk2(
    const psvn_t* psvn,
    const uint8_t  n2[NONCE_2_SIZE],
    sgx_key_128bit_t* wrap_key)
{

    if( psvn == NULL)
        return PVEC_PARAMETER_ERROR;
    uint8_t content[32];
    sgx_status_t sgx_status = SGX_SUCCESS;
    sgx_key_128bit_t key_tmp;
    pve_status_t status = PVEC_SUCCESS;

    memset(&key_tmp, 0, sizeof(key_tmp));
    status = get_provision_key(&key_tmp, psvn); //Generate Provisioning Key with respect to the psvn
    if(status != PVEC_SUCCESS)
        goto ret_point;

    memset(&content, 0, sizeof(content));
    content[0] = 0x01;    
    memcpy(&content[START_OFF_PROV_WRAP_2], PROV_WRAP_2, PROV_WRAP_2_LEN); // byte 1-11 : "PROV_WRAP_2" (ascii encoded)
    memcpy(&content[START_OFF_NONCE_2], n2, NONCE_2_SIZE);
    content[OFF_BYTE_ZERO] = 0x00; //fill zero in byte offset 30
    content[OFF_BYTE_0X80] = 0x80; //fill 0x80 in byte offset 31

    //get the cmac of provision key as PWK2
    se_static_assert(sizeof(sgx_cmac_128bit_key_t)==sizeof(key_tmp)); /*size of sgx_cmac_128bit_key_t should be same as sgx_key_128bit_t*/
    se_static_assert(sizeof(sgx_cmac_128bit_tag_t)==sizeof(sgx_key_128bit_t)); /*size of sgx_cmac_128bit_tag_t should be same as sgx_key_128bit_t*/
    if((sgx_status = sgx_rijndael128_cmac_msg(reinterpret_cast<const sgx_cmac_128bit_key_t *>(&key_tmp), 
        reinterpret_cast<const uint8_t *>(content), sizeof(content),
        reinterpret_cast<sgx_cmac_128bit_tag_t *>(wrap_key)))!=SGX_SUCCESS){
            status = sgx_error_to_pve_error(sgx_status);
    }else{
        status = PVEC_SUCCESS;
    }
ret_point:
    (void)memset_s(&key_tmp,sizeof(key_tmp), 0 ,sizeof(key_tmp)); //clear provisioninig key in stack
    return status;
}

//Function to generate Provisioning Sealing Key given the psvn
//The key is used to seal the private parameter f before sending to backend server
pve_status_t get_pve_psk(
    const psvn_t* psvn,
    sgx_key_128bit_t* seal_key)
{

    sgx_status_t se_ret = SGX_SUCCESS;
    sgx_key_request_t seal_key_req;

    if(psvn == NULL)
        return PVEC_PARAMETER_ERROR;

    memset(&seal_key_req, 0, sizeof(sgx_key_request_t));
    memcpy(&seal_key_req.cpu_svn, &psvn->cpu_svn, SGX_CPUSVN_SIZE);
    memcpy(&seal_key_req.isv_svn, &psvn->isv_svn, sizeof(psvn->isv_svn));
    seal_key_req.key_name = SGX_KEYSELECT_PROVISION_SEAL; //provisioning sealling key

    seal_key_req.attribute_mask.xfrm = 0;
    seal_key_req.attribute_mask.flags = ~SGX_FLAGS_MODE64BIT;

    se_ret = sgx_get_key(&seal_key_req, seal_key);
    if(SGX_SUCCESS != se_ret)
    {
        return sgx_error_to_pve_error(se_ret);
    }

    return PVEC_SUCCESS;
}

//simple wrapper for memcpy but checking type of parameter
void pve_memcpy_out(external_memory_byte_t *dst, const void *src, uint32_t size)
{
    memcpy(dst, src, size);
}

void pve_memcpy_in(void *dst, const external_memory_byte_t *src, uint32_t size)
{
    memcpy(dst, src, size);
}

pve_status_t se_read_rand_error_to_pve_error(sgx_status_t error)
{
    if(error == SGX_SUCCESS)return PVEC_SUCCESS;
    else if(error == SGX_ERROR_INVALID_PARAMETER) return PVEC_UNEXPECTED_ERROR;
    else return PVEC_READ_RAND_ERROR; //read rand hardware error
}

pve_status_t epid_error_to_pve_error(EpidStatus epid_result)
{
    if(kEpidNoErr == epid_result)
        return PVEC_SUCCESS;
    switch(epid_result){
    case kEpidMemAllocErr:
    case kEpidNoMemErr:
        return PVEC_MALLOC_ERROR;
    case kEpidSigInvalid:
        return PVEC_INVALID_EPID_KEY;
    default:
        return PVEC_EPID_ERROR;
    }
}

pve_status_t sgx_error_to_pve_error(sgx_status_t status)
{
    switch(status){
    case SGX_SUCCESS:
        return PVEC_SUCCESS;
    case SGX_ERROR_OUT_OF_MEMORY:
        return PVEC_MALLOC_ERROR;
    case SGX_ERROR_INVALID_CPUSVN:
    case SGX_ERROR_INVALID_ISVSVN:
        return PVEC_INVALID_CPU_ISV_SVN;
    default:
        return PVEC_SE_ERROR;
    }
}
