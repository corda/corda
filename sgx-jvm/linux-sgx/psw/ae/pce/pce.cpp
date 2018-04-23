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

#include "pce_cert.h"
#include "pce_t.c"
#include "aeerror.h"
#include "sgx_utils.h"
#include "ipp_wrapper.h"
#include "byte_order.h"
#include "pve_qe_common.h"
#include "arch.h"
#include <assert.h>

ae_error_t get_ppid(ppid_t* ppid);
ae_error_t get_pce_priv_key(const psvn_t* psvn, sgx_ec256_private_t* wrap_key);
#define PCE_RSA_SEED_SIZE 32

#define RSA_MOD_SIZE 384 //hardcode n size to be 384
#define RSA_E_SIZE 4 //hardcode e size to be 4

se_static_assert(RSA_MOD_SIZE == PEK_MOD_SIZE);

//Function to generate Current isvsvn from REPORT
static ae_error_t get_isv_svn(sgx_isv_svn_t* isv_svn)
{
    sgx_status_t se_ret = SGX_SUCCESS;

    sgx_report_t report;
    memset(&report, 0, sizeof(report));
    se_ret = sgx_create_report(NULL, NULL, &report);
    if(SGX_SUCCESS != se_ret){
        (void)memset_s(&report,sizeof(report), 0, sizeof(report));
        return PCE_UNEXPECTED_ERROR;
    }
    memcpy(isv_svn, &report.body.isv_svn, sizeof(report.body.isv_svn));
    (void)memset_s(&report, sizeof(report), 0, sizeof(report));
    return AE_SUCCESS;
}

//always assume the format of public_key is module n of RSA public key followed by 4 bytes e and both n and e are in Big Endian
uint32_t get_pc_info(const sgx_report_t* report,
    const uint8_t *public_key, uint32_t key_size,
    uint8_t crypto_suite,
    uint8_t *encrypted_ppid, uint32_t encrypted_ppid_buf_size,
    uint32_t *encrypted_ppid_out_size,
    pce_info_t *pce_info,
    uint8_t *signature_scheme)
{
    if (report == NULL ||
        public_key == NULL ||
        encrypted_ppid == NULL ||
        encrypted_ppid_out_size == NULL ||
        pce_info == NULL||
        signature_scheme == NULL){
            return AE_INVALID_PARAMETER;
    }
    if(ALG_RSA_OAEP_3072!=crypto_suite){//The only crypto suite supported in RSA 3072 where 384 bytes module n is used
        return AE_INVALID_PARAMETER;
    }

    //RSA public key is mod || e
    if (RSA_MOD_SIZE + RSA_E_SIZE != key_size)
    {
        return AE_INVALID_PARAMETER;
    }

    *encrypted_ppid_out_size = RSA_MOD_SIZE;//output size is same as public key module size
    if (encrypted_ppid_buf_size < RSA_MOD_SIZE){
        return AE_INSUFFICIENT_DATA_IN_BUFFER;
    }
    if(SGX_SUCCESS != sgx_verify_report(report)){
        return PCE_INVALID_REPORT;
    }
    if((report->body.attributes.flags & SGX_FLAGS_PROVISION_KEY) != SGX_FLAGS_PROVISION_KEY ||
        (report->body.attributes.flags & SGX_FLAGS_DEBUG) != 0){
        return PCE_INVALID_PRIVILEGE;
    }
    uint8_t hash_buf[SGX_REPORT_DATA_SIZE];//hash value only use 32 bytes but data in report has 64 bytes size
    se_static_assert(sizeof(hash_buf)>=sizeof(sgx_sha256_hash_t));
    memset(hash_buf, 0, sizeof(hash_buf));

    sgx_sha_state_handle_t sha_handle = NULL;
    sgx_status_t sgx_ret = SGX_ERROR_UNEXPECTED;
    do
    {
        sgx_ret = sgx_sha256_init(&sha_handle);
        if (SGX_SUCCESS != sgx_ret)
            break;
        sgx_ret = sgx_sha256_update(&crypto_suite, sizeof(uint8_t), sha_handle);
        if (SGX_SUCCESS != sgx_ret)
            break;
        sgx_ret = sgx_sha256_update(public_key, RSA_MOD_SIZE + RSA_E_SIZE, sha_handle);
        if (SGX_SUCCESS != sgx_ret)
            break;
        sgx_ret = sgx_sha256_get_hash(sha_handle, reinterpret_cast<sgx_sha256_hash_t *>(hash_buf));
    } while (0);
    if (sha_handle != NULL)
        sgx_sha256_close(sha_handle);
    if (SGX_ERROR_OUT_OF_MEMORY == sgx_ret){
        return AE_OUT_OF_MEMORY_ERROR;
    }
    else if (SGX_SUCCESS != sgx_ret){
        return AE_FAILURE;
    }

    //verify the report data is SHA256(crypto_suite||public_key)||0-padding
    if(memcmp(hash_buf, &report->body.report_data, sizeof(report->body.report_data))!=0){
        return AE_INVALID_PARAMETER;
    }

    ppid_t ppid_buf;

    IppsRSAPublicKeyState *pub_key = NULL;
    int pub_key_size = 0;
    Ipp8u seeds[PCE_RSA_SEED_SIZE] = { 0 };
    uint8_t *pub_key_buffer = NULL;
    IppStatus ipp_ret;

    uint32_t little_endian_e = 0;
    uint8_t *le_n = NULL;

    ae_error_t ae_ret = get_ppid(&ppid_buf);
    if(ae_ret!=AE_SUCCESS){
        goto RETURN_POINT;
    }

    little_endian_e = lv_ntohl(*(public_key + RSA_MOD_SIZE));
    le_n = (uint8_t *)malloc(RSA_MOD_SIZE);
    if (le_n == NULL){
        ae_ret = AE_OUT_OF_MEMORY_ERROR;
        goto RETURN_POINT;
    }

    for (size_t i = 0; i<RSA_MOD_SIZE; i++){
        le_n[i] = *(public_key + RSA_MOD_SIZE - 1 - i);//create little endian n
    }

    ipp_ret = create_rsa_pub_key(RSA_MOD_SIZE, RSA_E_SIZE,
        reinterpret_cast<const Ipp32u *>(le_n),
        &little_endian_e,
        &pub_key);
    free(le_n);
    if (ippStsMemAllocErr == ipp_ret){
        ae_ret = AE_OUT_OF_MEMORY_ERROR;
        goto RETURN_POINT;
    }
    else if(ippStsNoErr != ipp_ret){//possible invalid rsa public key
        ae_ret = AE_FAILURE;
        goto RETURN_POINT;
    }
    ipp_ret = ippsRSA_GetBufferSizePublicKey(&pub_key_size, pub_key);
    if (ipp_ret != ippStsNoErr){
        ae_ret = AE_FAILURE;
        goto RETURN_POINT;
    }
    if (SGX_SUCCESS != sgx_read_rand(seeds, PCE_RSA_SEED_SIZE)){
        ae_ret = AE_READ_RAND_ERROR;
        goto RETURN_POINT;
    }

    pub_key_buffer = (uint8_t *)malloc(pub_key_size);
    if (pub_key_buffer == NULL){
        ae_ret = AE_OUT_OF_MEMORY_ERROR;
        goto RETURN_POINT;
    }
    ipp_ret = ippsRSAEncrypt_OAEP(reinterpret_cast<const Ipp8u *>(&ppid_buf), sizeof(ppid_buf), NULL, 0, seeds,
        encrypted_ppid, pub_key, IPP_ALG_HASH_SHA256, pub_key_buffer);
    if (ipp_ret != ippStsNoErr){
        ae_ret = AE_FAILURE;
        goto RETURN_POINT;
    }

    ae_ret = get_isv_svn(&pce_info->pce_isvn);
    if (ae_ret != AE_SUCCESS){
        goto RETURN_POINT;
    }

    pce_info->pce_id = CUR_PCE_ID;
    *signature_scheme = NIST_P256_ECDSA_SHA256;
    ae_ret = AE_SUCCESS;
RETURN_POINT:
    memset_s(&ppid_buf, sizeof(ppid_buf), 0, sizeof(ppid_t));
    if(NULL != pub_key)
        secure_free_rsa_pub_key(RSA_MOD_SIZE, RSA_E_SIZE, pub_key);
    if (NULL != pub_key_buffer)
        free(pub_key_buffer);

    if (AE_SUCCESS != ae_ret)
        memset_s(encrypted_ppid, encrypted_ppid_buf_size, 0, *encrypted_ppid_out_size);
    return ae_ret;
}

uint32_t certify_enclave(const psvn_t* cert_psvn,
                         const sgx_report_t* report,
                         uint8_t *signature,
                         uint32_t signature_buf_size,
                         uint32_t *signature_out_size)
{
    if(cert_psvn==NULL||
        report==NULL||
        signature == NULL||
        signature_out_size == NULL){
            return AE_INVALID_PARAMETER;
    }
    if(signature_buf_size < sizeof(sgx_ec256_signature_t)){
        *signature_out_size = sizeof(sgx_ec256_signature_t);
        return AE_INSUFFICIENT_DATA_IN_BUFFER;
    }

    ae_error_t ae_ret = AE_FAILURE;
    sgx_ecc_state_handle_t handle=NULL;
    sgx_ec256_private_t ec_prv_key = {0};
    sgx_status_t sgx_status = SGX_SUCCESS;

    if(SGX_SUCCESS != sgx_verify_report(report)){
        return PCE_INVALID_REPORT;
    }
    //only PvE could use the interface which has flag SGX_FLAGS_PROVISION_KEY
    if((report->body.attributes.flags & SGX_FLAGS_PROVISION_KEY) != SGX_FLAGS_PROVISION_KEY ||
        (report->body.attributes.flags & SGX_FLAGS_DEBUG) != 0){
        return PCE_INVALID_PRIVILEGE;
    }
    ae_ret = get_pce_priv_key(cert_psvn, &ec_prv_key);
    if(AE_SUCCESS!=ae_ret){
        goto ret_point;
    }
    SWAP_ENDIAN_32B(&ec_prv_key);
    sgx_status = sgx_ecc256_open_context(&handle);
    if (SGX_ERROR_OUT_OF_MEMORY == sgx_status)
    {
        ae_ret = AE_OUT_OF_MEMORY_ERROR;
        goto ret_point;
    }
    else if (SGX_SUCCESS != sgx_status) {
        ae_ret = AE_FAILURE;
        goto ret_point;
    }

    sgx_status = sgx_ecdsa_sign(reinterpret_cast<const uint8_t *>(&report->body), sizeof(report->body),
        &ec_prv_key, reinterpret_cast<sgx_ec256_signature_t *>(signature), handle);
    if (SGX_ERROR_OUT_OF_MEMORY == sgx_status)
    {
        ae_ret = AE_OUT_OF_MEMORY_ERROR;
        goto ret_point;
    }
    else if (SGX_SUCCESS != sgx_status) {
        ae_ret = AE_FAILURE;
        goto ret_point;
    }
    //swap from little endian used in sgx_crypto to big endian used in network byte order
    SWAP_ENDIAN_32B(reinterpret_cast<sgx_ec256_signature_t *>(signature)->x);
    SWAP_ENDIAN_32B(reinterpret_cast<sgx_ec256_signature_t *>(signature)->y);

    *signature_out_size = sizeof(sgx_ec256_signature_t);
    ae_ret = AE_SUCCESS;
ret_point:
    (void)memset_s(&ec_prv_key, sizeof(ec_prv_key),0,sizeof(ec_prv_key));
    if(handle!=NULL){
        sgx_ecc256_close_context(handle);
    }
    if(AE_SUCCESS != ae_ret){
        (void)memset_s(signature, signature_buf_size, 0, sizeof(sgx_ec256_signature_t));
    }
    return ae_ret;
}
