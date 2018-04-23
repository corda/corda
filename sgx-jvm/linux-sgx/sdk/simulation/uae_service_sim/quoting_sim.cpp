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


#include <stdlib.h>
#include "uae_service_sim.h"
#include "epid/common/types.h"
#include "se_sig_rl.h"
#include "se_quote_internal.h"
#include "deriv.h"
#include "cpusvn_util.h"
#include "crypto_wrapper.h"

/* The EPID group certificate */ 
static const uint8_t EPID_GROUP_CERT[] = {
    0x00, 0x00, 0x00, 0x0B, 0xB3, 0x6F, 0xFF, 0x81, 0xE2, 0x1B, 0x17, 0xEB,
    0x3D, 0x75, 0x3D, 0x61, 0x7E, 0x27, 0xB0, 0xCB, 0xD0, 0x6D, 0x8F, 0x9D,
    0x64, 0xCE, 0xE3, 0xCE, 0x43, 0x4C, 0x62, 0xFD, 0xB5, 0x80, 0xE0, 0x99,
    0x3A, 0x07, 0x56, 0x80, 0xE0, 0x88, 0x59, 0xA4, 0xFD, 0xB5, 0xB7, 0x9D,
    0xE9, 0x4D, 0xAE, 0x9C, 0xEE, 0x3D, 0x66, 0x42, 0x82, 0x45, 0x7E, 0x7F,
    0xD8, 0x69, 0x3E, 0xA1, 0x74, 0xF4, 0x59, 0xEE, 0xD2, 0x74, 0x2E, 0x9F,
    0x63, 0xC2, 0x51, 0x8E, 0xD5, 0xDB, 0xCA, 0x1C, 0x54, 0x74, 0x10, 0x7B,
    0xDC, 0x99, 0xED, 0x42, 0xD5, 0x5B, 0xA7, 0x04, 0x29, 0x66, 0x61, 0x63,
    0xBC, 0xDD, 0x7F, 0xE1, 0x76, 0x5D, 0xC0, 0x6E, 0xE3, 0x14, 0xAC, 0x72,
    0x48, 0x12, 0x0A, 0xA6, 0xE8, 0x5B, 0x08, 0x7B, 0xDA, 0x3F, 0x51, 0x7D,
    0xDE, 0x4C, 0xEA, 0xCB, 0x93, 0xA5, 0x6E, 0xCC, 0xE7, 0x8E, 0x10, 0x84,
    0xBD, 0x19, 0x5A, 0x95, 0xE2, 0x0F, 0xCA, 0x1C, 0x50, 0x71, 0x94, 0x51,
    0x40, 0x1B, 0xA5, 0xB6, 0x78, 0x87, 0x53, 0xF6, 0x6A, 0x95, 0xCA, 0xC6,
    0x8D, 0xCD, 0x36, 0x88, 0x07, 0x28, 0xE8, 0x96, 0xCA, 0x78, 0x11, 0x5B,
    0xB8, 0x6A, 0xE7, 0xE5, 0xA6, 0x65, 0x7A, 0x68, 0x15, 0xD7, 0x75, 0xF8,
    0x24, 0x14, 0xCF, 0xD1, 0x0F, 0x6C, 0x56, 0xF5, 0x22, 0xD9, 0xFD, 0xE0,
    0xE2, 0xF4, 0xB3, 0xA1, 0x90, 0x21, 0xA7, 0xE0, 0xE8, 0xB3, 0xC7, 0x25,
    0xBC, 0x07, 0x72, 0x30, 0x5D, 0xEE, 0xF5, 0x6A, 0x89, 0x88, 0x46, 0xDD,
    0x89, 0xC2, 0x39, 0x9C, 0x0A, 0x3B, 0x58, 0x96, 0x57, 0xE4, 0xF3, 0x3C,
    0x79, 0x51, 0x69, 0x36, 0x1B, 0xB6, 0xF7, 0x05, 0x5D, 0x0A, 0x88, 0xDB,
    0x1F, 0x3D, 0xEA, 0xA2, 0xBA, 0x6B, 0xF0, 0xDA, 0x8E, 0x25, 0xC6, 0xAD,
    0x83, 0x7D, 0x3E, 0x31, 0xEE, 0x11, 0x40, 0xA9
};

/* The report key is the same as BASE_REPORT_KEY in
   /trunk/sdk/simulation/tinst/deriv.cpp, which is used in simulation
   create_report and verify_report. deriv.cpp is used inside enclave.
   So only import this structure. */
static const uint8_t BASE_REPORT_KEY[] = {
    0xff, 0x00, 0xff, 0x00, 0xff, 0x00, 0xff, 0x00,
    0xff, 0x00, 0xff, 0x00, 0xff, 0x00, 0xff, 0x00,
};
// The hard-coded OwnerEpoch.
static const se_owner_epoch_t SIMU_OWNER_EPOCH_MSR = {
    0x54, 0x48, 0x49, 0x53, 0x49, 0x53, 0x4f, 0x57,
    0x4e, 0x45, 0x52, 0x45, 0x50, 0x4f, 0x43, 0x48,
};

//simulated QE ISVSVN
static const sgx_isv_svn_t QE_ISVSVN = 0XEF;
static const sgx_isv_svn_t PCE_ISVSVN = 0xEF;
static const uint32_t EXT_EPID_GID = 0xEFEFEFEF;

#if !defined(ntohl)
#define ntohl(u32)                                        \
  ((uint32_t)(((((unsigned char*)&(u32))[0]) << 24)       \
              + ((((unsigned char*)&(u32))[1]) << 16)     \
              + ((((unsigned char*)&(u32))[2]) << 8)      \
              + (((unsigned char*)&(u32))[3])))
#endif


sgx_status_t sgx_init_quote(
    sgx_target_info_t *p_target_info,
    sgx_epid_group_id_t *p_gid)
{
    if(!p_target_info || !p_gid){
        return SGX_ERROR_INVALID_PARAMETER;
    }

    p_target_info->attributes.flags = SGX_FLAGS_INITTED;
    p_target_info->attributes.xfrm = SGX_XFRM_LEGACY;
    memset(&(p_target_info->mr_enclave), 0xEE, sizeof(sgx_measurement_t));

    //Make sure the size of prebuilt data are the same with target buffer.
    se_static_assert(sizeof(EPID_GROUP_CERT) == sizeof(GroupPubKey)); /* "Group cert size changed*/

    //Copy hard coded gid into output buffer.
    GroupPubKey *p_epid_group_cert = (GroupPubKey *)const_cast<uint8_t*>(EPID_GROUP_CERT);

    ((uint8_t *)p_gid)[0] = p_epid_group_cert->gid.data[3];
    ((uint8_t *)p_gid)[1] = p_epid_group_cert->gid.data[2];
    ((uint8_t *)p_gid)[2] = p_epid_group_cert->gid.data[1];
    ((uint8_t *)p_gid)[3] = p_epid_group_cert->gid.data[0];

    return SGX_SUCCESS;
}

static sgx_status_t create_qe_report(const sgx_report_t *p_report,
                                    const sgx_quote_nonce_t* p_quote_nonce,
                                    const uint8_t* p_quote,
                                    uint32_t quote_size,
                                    const sgx_cpu_svn_t* cpusvn,
                                    sgx_report_t *p_qe_report)
{
    sgx_report_t temp_qe_report;
    // assemble REPORT
    memset(&temp_qe_report, 0, sizeof(sgx_report_t));
    //QE_REPORT.BODY.CPUSVN = CPUSVN
    if(memcpy_s(&temp_qe_report.body.cpu_svn,
                sizeof(temp_qe_report.body.cpu_svn),
                cpusvn, sizeof(sgx_cpu_svn_t)))
        return SGX_ERROR_UNEXPECTED;
    //ProdID same as QE
    temp_qe_report.body.isv_prod_id = 1;
    //set ISVSVN
    temp_qe_report.body.isv_svn = QE_ISVSVN;
    //QE_REPORT.BODY.ATTRIBUTES = 0x30000000000000001
    temp_qe_report.body.attributes.flags = SGX_FLAGS_INITTED;
    temp_qe_report.body.attributes.xfrm = SGX_XFRM_LEGACY;
    //QE_REPORT.BODY.MRENCLAVE = 64 0xEE bytes
    memset(&temp_qe_report.body.mr_enclave, 0xEE, sizeof(sgx_measurement_t));
    //QE_REPORT.BODY.MRSIGNER = random value
    if(SGX_SUCCESS != sgx_read_rand((uint8_t *)(&temp_qe_report.body.mr_signer),
                                    sizeof(sgx_measurement_t)))
        return SGX_ERROR_UNEXPECTED;
    //QE_REPORT.KEYID = <random>
    if(SGX_SUCCESS != sgx_read_rand((unsigned char *)&temp_qe_report.key_id,
                                    sizeof(sgx_key_id_t)))
        return SGX_ERROR_UNEXPECTED;

    //QE_REPORT.BODY.REPORTDATA = SHA256(NONCE || QUOTE)
    sgx_status_t sgx_ret = SGX_ERROR_UNEXPECTED;

    //prepare reprot_data
    size_t msg_size = sizeof(sgx_quote_nonce_t) + quote_size;
    uint8_t * p_msg = (uint8_t *)malloc(msg_size);
    if(!p_msg)
        return SGX_ERROR_OUT_OF_MEMORY;
    if(memcpy_s(p_msg, msg_size, p_quote_nonce, sizeof(sgx_quote_nonce_t)))
    {
        free(p_msg);
        return sgx_ret;
    }
    if(memcpy_s(p_msg + sizeof(sgx_quote_nonce_t), msg_size - sizeof(sgx_quote_nonce_t), p_quote, quote_size))
    {
        free(p_msg);
        return sgx_ret;
    }

    unsigned int report_data_len = sizeof(temp_qe_report.body.report_data);

    if(SGX_SUCCESS != (sgx_ret = sgx_EVP_Digest(EVP_sha256(), p_msg, (unsigned int)msg_size, 
                    (uint8_t *)&temp_qe_report.body.report_data, &report_data_len)))
    {
        if(sgx_ret != SGX_ERROR_OUT_OF_MEMORY)
            sgx_ret = SGX_ERROR_UNEXPECTED;
        free(p_msg);
        return sgx_ret;
    }
    
    free(p_msg);

    /* calculate CMAC using the report key, same as BASE_REPORT_KEY in
       sdk/simulation/tinst/deriv.cpp */
    derivation_data_t   dd;
    memset(&dd, 0, sizeof(dd));
    dd.size = sizeof(dd_report_key_t);

    dd.key_name = SGX_KEYSELECT_REPORT;
    if(memcpy_s(&dd.ddrk.mrenclave,sizeof(dd.ddrk.mrenclave),
                &p_report->body.mr_enclave, sizeof(sgx_measurement_t)))
        return SGX_ERROR_UNEXPECTED;
    if(memcpy_s(&dd.ddrk.attributes, sizeof(dd.ddrk.attributes),
                &p_report->body.attributes, sizeof(sgx_attributes_t)))
        return SGX_ERROR_UNEXPECTED;
    if(memcpy_s(&dd.ddrk.csr_owner_epoch, sizeof(dd.ddrk.csr_owner_epoch),
                SIMU_OWNER_EPOCH_MSR, sizeof(se_owner_epoch_t)))
        return SGX_ERROR_UNEXPECTED;
    if(memcpy_s(&dd.ddrk.cpu_svn, sizeof(dd.ddrk.cpu_svn),
                cpusvn, sizeof(sgx_cpu_svn_t)))
        return SGX_ERROR_UNEXPECTED;
    if(memcpy_s(&dd.ddrk.key_id, sizeof(dd.ddrk.key_id),
                &temp_qe_report.key_id, sizeof(sgx_key_id_t)))
        return SGX_ERROR_UNEXPECTED;
    
    sgx_key_128bit_t tmp_report_key;
    if(SGX_SUCCESS != (sgx_ret = sgx_cmac128_msg(BASE_REPORT_KEY, dd.ddbuf, dd.size, &tmp_report_key)))
    {
        if(sgx_ret != SGX_ERROR_OUT_OF_MEMORY)
            sgx_ret = SGX_ERROR_UNEXPECTED;
        return sgx_ret;
    }
    
    // call cryptographic CMAC function
    // CMAC data are *NOT* including MAC and KEYID
    if(SGX_SUCCESS != (sgx_ret = sgx_cmac128_msg(tmp_report_key, (const uint8_t *)&temp_qe_report.body, 
                    sizeof(temp_qe_report.body), &temp_qe_report.mac)))
    {
        if(sgx_ret != SGX_ERROR_OUT_OF_MEMORY)
            sgx_ret = SGX_ERROR_UNEXPECTED;
        return sgx_ret;
    }

    if(memcpy_s(p_qe_report, sizeof(*p_qe_report),
                &temp_qe_report, sizeof(temp_qe_report)))
    {
        sgx_ret = SGX_ERROR_UNEXPECTED;
    }
    return sgx_ret;
}


/*
* For quote with SIG-RL
* |--------------------------------------------------------------------|
* |sgx_quote_t|wrap_key_t|iv|payload_size|basic_sig|rl_ver|n2|nrp..|mac|
* |--------------------------------------------------------------------|
* For quote without SIG-RL
* |--------------------------------------------------------------|
* |sgx_quote_t|wrap_key_t|iv|payload_size|basic_sig|rl_ver|n2|mac|
* |--------------------------------------------------------------|
*/
sgx_status_t sgx_get_quote(
    const sgx_report_t *p_report,
    sgx_quote_sign_type_t quote_type,
    const sgx_spid_t *p_spid,
    const sgx_quote_nonce_t *p_nonce,
    const uint8_t *p_sig_rl,
    uint32_t sig_rl_size,
    sgx_report_t *p_qe_report,
    sgx_quote_t *p_quote,
    uint32_t quote_size)
{
    sgx_status_t ret = SGX_SUCCESS;
    GroupPubKey *p_epid_group_cert = (GroupPubKey *)const_cast<uint8_t*>(EPID_GROUP_CERT);
    unsigned int rl_entry_count = 0;
    sgx_basename_t basename = {{0}};
    uint64_t required_buffer_size = 0;
    se_encrypted_sign_t *p_signature = NULL;
    sgx_cpu_svn_t cpusvn = {{0}};
    uint8_t *p_mac = NULL;


    if(!p_report || !p_spid || !p_quote || !quote_size)
        return SGX_ERROR_INVALID_PARAMETER;
    if(!p_nonce && p_qe_report)
        return SGX_ERROR_INVALID_PARAMETER;
    if(p_nonce && !p_qe_report)
        return SGX_ERROR_INVALID_PARAMETER;
    if(p_sig_rl && sig_rl_size < sizeof(se_sig_rl_t))
        return SGX_ERROR_INVALID_PARAMETER;
    if(!p_sig_rl && sig_rl_size)
        return SGX_ERROR_INVALID_PARAMETER;
    if(quote_type != SGX_UNLINKABLE_SIGNATURE
        && quote_type != SGX_LINKABLE_SIGNATURE)
        return SGX_ERROR_INVALID_PARAMETER;

    if(p_sig_rl){
        //Check the size of SIG-RL.
        se_sig_rl_t *p_sig_rl_temp = (se_sig_rl_t *)const_cast<uint8_t*>(p_sig_rl);
        uint64_t required_sig_rl_size = se_get_sig_rl_size(p_sig_rl_temp);
        rl_entry_count = ntohl(p_sig_rl_temp->sig_rl.n2);
        if(required_sig_rl_size > sig_rl_size)
        {
            ret = SGX_ERROR_INVALID_PARAMETER;
            goto CLEANUP;
        }
    }

    se_static_assert(sizeof(basename) > sizeof(sgx_spid_t));
    /* Because basename has already been zerod,
       so we don't need to concatenating with 0s.*/
    if(memcpy_s(&basename, sizeof(basename), p_spid, sizeof(sgx_spid_t)))
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }
    if(SGX_UNLINKABLE_SIGNATURE == quote_type)
    {
        uint8_t *p = (uint8_t *)&basename + sizeof(sgx_spid_t);
        if(SGX_SUCCESS != sgx_read_rand(p,
            sizeof(basename) - sizeof(sgx_spid_t)))
        {
            ret = SGX_ERROR_UNEXPECTED;
            goto CLEANUP;
        }
    }

    /* sign_size returned from epidMember_signMessage including the RLver
       and n2. */
    required_buffer_size = sizeof(sgx_quote_t)
                           + sizeof(se_wrap_key_t)
                           + 12 // size of payload_iv
                           + 4 // size of payload_size
                           + sizeof(BasicSignature)
                           + sizeof(RLver_t)
                           + sizeof(RLCount)
                           + 16; // size of payload_mac
    if(p_sig_rl){
        required_buffer_size += (sizeof(NrProof) * rl_entry_count);
    }

    /* If the p_quote is not NULL, then we should make sure the buffer size is
    * correct. */
    if(quote_size < required_buffer_size){
        ret = SGX_ERROR_INVALID_PARAMETER;
        goto CLEANUP;
    }

    if(SGX_SUCCESS != get_cpusvn(&cpusvn))
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }
    if(memcmp(&cpusvn, &((const sgx_report_t *)p_report)->body.cpu_svn,
              sizeof(sgx_cpu_svn_t)))
    {
        ret = SGX_ERROR_INVALID_PARAMETER;
        goto CLEANUP;
    }

    /* Copy the data in the report into quote body. */ 
    memset(p_quote, 0xEE, quote_size);
    p_quote->version = 2;
    p_quote->sign_type = (uint16_t)quote_type;

    p_quote->epid_group_id[0] = p_epid_group_cert->gid.data[3];
    p_quote->epid_group_id[1] = p_epid_group_cert->gid.data[2];
    p_quote->epid_group_id[2] = p_epid_group_cert->gid.data[1];
    p_quote->epid_group_id[3] = p_epid_group_cert->gid.data[0];

    p_quote->qe_svn = QE_ISVSVN;
    p_quote->pce_svn = PCE_ISVSVN;
    p_quote->xeid = EXT_EPID_GID;
    if(memcpy_s(&p_quote->basename, sizeof(sgx_basename_t),
             &basename, sizeof(basename))){
            ret = SGX_ERROR_UNEXPECTED;
            goto CLEANUP;
    }
    if(memcpy_s(&p_quote->report_body, sizeof(p_quote->report_body),
             &((const sgx_report_t *)p_report)->body, sizeof(sgx_report_body_t)))
    {
            ret = SGX_ERROR_UNEXPECTED;
            goto CLEANUP;
    }
    p_quote->signature_len = (uint32_t)(required_buffer_size - sizeof(sgx_quote_t));

    // Set the payload_size
    p_signature = (se_encrypted_sign_t *)(p_quote->signature);
    p_signature->payload_size = (uint32_t)(sizeof(BasicSignature)
                                + sizeof(RLver_t)
                                + sizeof(RLCount)
                                + (sizeof(NrProof) * rl_entry_count));

    if(SGX_SUCCESS != sgx_read_rand(p_signature->iv, sizeof(p_signature->iv)))
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }
    p_mac = (uint8_t *)(&p_signature->basic_sign) + p_signature->payload_size;
    if(SGX_SUCCESS != sgx_read_rand(p_mac, 16))
    {
        ret = SGX_ERROR_UNEXPECTED;
        goto CLEANUP;
    }

    if(p_qe_report)
        ret = create_qe_report(p_report, p_nonce, (uint8_t*)p_quote,
                               quote_size, &cpusvn, p_qe_report);

CLEANUP:
    return ret;
} //sgx_get_quote

sgx_status_t SGXAPI sgx_report_attestation_status(
    const sgx_platform_info_t *p_platform_info,
    int attestation_status,
    sgx_update_info_bit_t *p_update_info)
{
    UNUSED(p_platform_info);
    UNUSED(attestation_status);
    memset(p_update_info, 0, sizeof(sgx_update_info_bit_t));
    return SGX_SUCCESS;
}

sgx_status_t SGXAPI sgx_get_extended_epid_group_id(uint32_t* p_extended_epid_group_id)
{
    *p_extended_epid_group_id = 0;
    return SGX_SUCCESS;
}

sgx_status_t SGXAPI sgx_get_whitelist_size(uint32_t* p_whitelist_size)
{
    *p_whitelist_size = 0;
    return SGX_SUCCESS;
}

sgx_status_t SGXAPI sgx_get_whitelist(uint8_t* p_whitelist, uint32_t whitelist_size)
{
    UNUSED(p_whitelist);
    if(whitelist_size!=0){
          return SGX_ERROR_INVALID_PARAMETER;
    }else{
          return SGX_SUCCESS;
    }
}

