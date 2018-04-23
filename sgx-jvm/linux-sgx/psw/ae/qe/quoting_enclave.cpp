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


#ifndef __linux__
#include "targetver.h"
#endif
// Exclude rarely-used stuff from Windows headers
//#define WIN32_LEAN_AND_MEAN
// Windows Header Files:
//#include <windows.h>

#include "se_types.h"
#include "sgx_quote.h"
#include "aeerror.h"
#include "sgx_tseal.h"
#include "epid_pve_type.h"
#include "sgx_utils.h"
#include "ipp_wrapper.h"
#include "epid/common/errors.h"
#include "ae_ipp.h"
#include "epid/member/api.h"
#include "quoting_enclave_t.c"
#include "sgx_tcrypto.h"
#include "se_sig_rl.h"
#include "se_ecdsa_verify_internal.h"
#include "se_quote_internal.h"
#include "pve_qe_common.h"
#include "byte_order.h"
#include "util.h"
#include "qsdk_pub.hh"
#include "isk_pub.hh"

#if !defined(SWAP_4BYTES)
#define SWAP_4BYTES(u32)                                                    \
    ((uint32_t)(((((unsigned char*)&(u32))[0]) << 24)                       \
                + ((((unsigned char*)&(u32))[1]) << 16)                     \
                + ((((unsigned char*)&(u32))[2]) << 8)                      \
                + (((unsigned char*)&(u32))[3])))
#endif

// Start from 1, and it's little endian.
#define QE_QUOTE_VERSION        2

#define QE_AES_IV_SIZE          12
#define QE_AES_KEY_SIZE         16
#define QE_OAEP_SEED_SIZE       32

/* One field in sgx_quote_t(signature_len) is not part of the quote_body need
   to be signed by EPID. So we need to minus sizeof(uint32_t). */
#define QE_QUOTE_BODY_SIZE  (sizeof(sgx_quote_t) - sizeof(uint32_t))

/*
 * An internal function used to verify EPID Blob, get EPID Group Cert
 * and get EPID context, at the same time, you can check whether EPID blob has
 * been resealed.
 *
 * @param p_blob[in, out] Pointer to EPID Blob.
 * @param p_is_resealed[out] Whether the EPID Blob has been resealed.
 * @param create_context[in] Flag indicates create EPID context or not.
 * @param plaintext_epid_data[out] Used to get the plaintext part of epid blob
 * @param pp_epid_context[out] Used to get the pointer of the EPID context.
 * @return ae_error_t AE_SUCCESS or other error cases.
 */
static ae_error_t verify_blob_internal(
    uint8_t *p_blob,
    uint32_t blob_size,
    uint8_t *p_is_resealed,
    uint32_t create_context,
    se_plaintext_epid_data_sdk_t& plaintext_epid_data,
    MemberCtx **pp_epid_context)
{
    sgx_status_t se_ret = SGX_SUCCESS;
    uint8_t resealed= FALSE;
    se_secret_epid_data_sdk_t secret_epid_data;
    se_plaintext_epid_data_sik_t plaintext_old_format;
    uint32_t plaintext_length;
    int is_old_format = 0;
    uint32_t decryptedtext_length = sizeof(secret_epid_data);
    sgx_sealed_data_t *p_epid_blob = (sgx_sealed_data_t *)p_blob;
    uint8_t local_epid_blob[sizeof(*p_epid_blob)
                            + sizeof(secret_epid_data)
                            + sizeof(plaintext_epid_data)]
                            = {0};
    // We will use plaintext_old_format as buffer to hold the output of sgx_unseal_data.
    // It can be se_plaintext_epid_data_sik_t or se_plaintext_epid_data_sdk_t.
    // We use the static assert to reassure plaintext_old_format is big enough.
    // If someone changed the definition of these 2 structures and break current assumption,
    // it will report error in compile time.
    se_static_assert(sizeof(plaintext_old_format)>=sizeof(plaintext_epid_data));

    if (sgx_get_encrypt_txt_len(p_epid_blob) != sizeof(se_secret_epid_data_sdk_t)&&
        sgx_get_encrypt_txt_len(p_epid_blob) != sizeof(se_secret_epid_data_sik_t)){
        return QE_EPIDBLOB_ERROR;
    }
    plaintext_length = sgx_get_add_mac_txt_len(p_epid_blob);
    if(plaintext_length != sizeof(se_plaintext_epid_data_sik_t)&&
       plaintext_length != sizeof(se_plaintext_epid_data_sdk_t))
    {
        return QE_EPIDBLOB_ERROR;
    }

    memset(&secret_epid_data, 0, sizeof(secret_epid_data));
    memset(&plaintext_epid_data, 0, sizeof(plaintext_epid_data));
    memset(&plaintext_old_format, 0, sizeof(plaintext_old_format));

    se_ret = sgx_unseal_data(p_epid_blob,
        (uint8_t *)&plaintext_old_format, // The unsealed plaintext can be old or new format, the buffer is defined as old format because it is bigger
        &plaintext_length,
        (uint8_t *)&secret_epid_data,
        &decryptedtext_length);
    if(SGX_SUCCESS != se_ret)
    {
        memset_s(&secret_epid_data, sizeof(secret_epid_data), 0,
                 sizeof(secret_epid_data));
        return QE_EPIDBLOB_ERROR;
    }

    //QE will support both epid blob with/without member precomputation
    //If the epid blob without member precomputation is used, QE will generate member precomputation and reseal epid blob 
    if((plaintext_old_format.seal_blob_type != PVE_SEAL_EPID_KEY_BLOB)
       || (plaintext_old_format.epid_key_version != EPID_KEY_BLOB_VERSION_SDK&&
           plaintext_old_format.epid_key_version != EPID_KEY_BLOB_VERSION_SIK )) //blob_type and key_version are always first two fields of plaintext in both format
    {
        memset_s(&secret_epid_data, sizeof(secret_epid_data), 0,
            sizeof(secret_epid_data));
        return QE_EPIDBLOB_ERROR;
    }

    // Only 2 combinations are legitimate for the tuple epid_key_version|decryptedtext_length|plaintext_length:
    // EPID_KEY_BLOB_VERSION_SIK|sizeof(se_secret_epid_data_sik_t)|sizeof(se_plaintext_epid_data_sik_t)
    // EPID_KEY_BLOB_VERSION_SDK|sizeof(se_secret_epid_data_sdk_t)|sizeof(se_plaintext_epid_data_sdk_t) 
    if((plaintext_old_format.epid_key_version == EPID_KEY_BLOB_VERSION_SIK && 
        (decryptedtext_length!=sizeof(se_secret_epid_data_sik_t)||plaintext_length!=sizeof(se_plaintext_epid_data_sik_t)))||
       (plaintext_old_format.epid_key_version == EPID_KEY_BLOB_VERSION_SDK &&
        (decryptedtext_length!=sizeof(se_secret_epid_data_sdk_t)||plaintext_length!=sizeof(se_plaintext_epid_data_sdk_t)))){
        memset_s(&secret_epid_data, sizeof(secret_epid_data), 0,
            sizeof(secret_epid_data));
        return QE_EPIDBLOB_ERROR;
    }
    // If the input epid blob is in sik format, we will upgrade it to sdk version
    if(plaintext_old_format.epid_key_version == EPID_KEY_BLOB_VERSION_SIK){
        plaintext_epid_data.seal_blob_type = PVE_SEAL_EPID_KEY_BLOB;
        plaintext_epid_data.epid_key_version = EPID_KEY_BLOB_VERSION_SDK;
        memcpy(&plaintext_epid_data.equiv_cpu_svn, &plaintext_old_format.equiv_cpu_svn, sizeof(plaintext_old_format.equiv_cpu_svn));
        memcpy(&plaintext_epid_data.equiv_pve_isv_svn, &plaintext_old_format.equiv_pve_isv_svn, sizeof(plaintext_old_format.equiv_pve_isv_svn));
        memcpy(&plaintext_epid_data.epid_group_cert, &plaintext_old_format.epid_group_cert, sizeof(plaintext_old_format.epid_group_cert));
        memcpy(&plaintext_epid_data.qsdk_exp, &plaintext_old_format.qsdk_exp, sizeof(plaintext_old_format.qsdk_exp));
        memcpy(&plaintext_epid_data.qsdk_mod, &plaintext_old_format.qsdk_mod, sizeof(plaintext_old_format.qsdk_mod));
        memcpy(&plaintext_epid_data.epid_sk, &plaintext_old_format.epid_sk, sizeof(plaintext_old_format.epid_sk));
        plaintext_epid_data.xeid = plaintext_old_format.xeid;
        memset(&secret_epid_data.member_precomp_data, 0, sizeof(secret_epid_data.member_precomp_data));
        is_old_format = 1;
        //PrivKey of secret_epid_data are both in offset 0 so that we need not move it
    }else{//SDK version format
        memcpy(&plaintext_epid_data, &plaintext_old_format, sizeof(plaintext_epid_data));
    }

    /* Create report to get current cpu_svn and isv_svn. */
    sgx_report_t report;
    memset(&report, 0, sizeof(report));
    se_ret = sgx_create_report(NULL, NULL, &report);
    if(SGX_SUCCESS != se_ret)
    {
        memset_s(&secret_epid_data, sizeof(secret_epid_data), 0,
                 sizeof(secret_epid_data));
        return QE_UNEXPECTED_ERROR;
    }

    /* Get the random function pointer. */
    BitSupplier rand_func = epid_random_func;

    /* Create EPID member context if required. PvE is responsible for verifying
       the Cert signature before storing them in the EPID blob. */
    if(create_context)
    {
        EpidStatus epid_ret = kEpidNoErr;
        epid_ret = EpidMemberCreate(
            &(plaintext_epid_data.epid_group_cert),
            (PrivKey*)&(secret_epid_data.epid_private_key),
            is_old_format?NULL:&secret_epid_data.member_precomp_data,
            rand_func,
            NULL,
            pp_epid_context);
        if(kEpidNoErr != epid_ret)
        {
            memset_s(&secret_epid_data, sizeof(secret_epid_data), 0,
                     sizeof(secret_epid_data));
            // Make sure the pointer is not pointered to garbage. And according
            // to EPID SDK 2.0 API document of EpidMemberCreate, it will not return
            // error with memory allocated. So set the pointer to NULL will not
            // cause memory leak here.
            *pp_epid_context = NULL;
            return QE_UNEXPECTED_ERROR;
        }
        epid_ret = EpidMemberSetHashAlg(*pp_epid_context, kSha256);
        if(kEpidNoErr != epid_ret)
        {
            EpidMemberDelete(pp_epid_context);
            memset_s(&secret_epid_data, sizeof(secret_epid_data), 0 ,
                    sizeof(secret_epid_data));
            *pp_epid_context = NULL;
            return QE_UNEXPECTED_ERROR;
        }
        if(is_old_format)
        {
            epid_ret = EpidMemberWritePrecomp(*pp_epid_context, &secret_epid_data.member_precomp_data);
            if(kEpidNoErr != epid_ret)
            {
                EpidMemberDelete(pp_epid_context);
                memset_s(&secret_epid_data, sizeof(secret_epid_data), 0,
                        sizeof(secret_epid_data));
                *pp_epid_context = NULL;
                return QE_UNEXPECTED_ERROR;
            }
        }
    }

     /* Update the Key Blob using the SEAL Key for the current TCB if the TCB is
        upgraded after the Key Blob is generated. Here memcmp cpu_svns might be 
        different even though they're actually same, but for defense in depth we
        will keep this comparison here. And we will also upgrade old format EPID
        blob to new format here. */
    if((memcmp(&report.body.cpu_svn, &p_epid_blob->key_request.cpu_svn,
              sizeof(report.body.cpu_svn)))
    || (report.body.isv_svn != p_epid_blob->key_request.isv_svn)
    || plaintext_old_format.epid_key_version == EPID_KEY_BLOB_VERSION_SIK)
    {
        se_ret = sgx_seal_data(sizeof(plaintext_epid_data),
            (uint8_t *)&plaintext_epid_data,
            sizeof(secret_epid_data),
            (uint8_t *)&secret_epid_data,
            SGX_TRUSTED_EPID_BLOB_SIZE_SDK,
            (sgx_sealed_data_t *)local_epid_blob);
        if(SGX_SUCCESS != se_ret)
        {
            // Clear the output buffer to make sure nothing leaks.
            memset_s(&secret_epid_data, sizeof(secret_epid_data), 0,
                     sizeof(secret_epid_data));
            // *pp_epid_context contains pointers, so we cannot simply clear it.
            if(pp_epid_context)
            {
                EpidMemberDelete(pp_epid_context);
                *pp_epid_context = NULL;
            }
            return QE_UNEXPECTED_ERROR;
        }
        memcpy(p_epid_blob, local_epid_blob, blob_size);
        resealed = TRUE;
    }
    memset_s(&secret_epid_data, sizeof(secret_epid_data), 0,
            sizeof(secret_epid_data));
    *p_is_resealed = resealed;
    return AE_SUCCESS;
}

/*
 * External function used to verify EPID Blob and check whether QE has
 * been updated.
 *
 * @param p_blob[in, out] Pointer to EPID Blob.
 * @param blob_size[in] The size of EPID Blob, in bytes.
 * @param p_is_resealed[out] Whether the EPID Blob is resealed within this function call.
 * @return uint32_t AE_SUCCESS or other error cases.
 */
uint32_t verify_blob(
    uint8_t *p_blob,
    uint32_t blob_size,
    uint8_t *p_is_resealed)
{
    se_plaintext_epid_data_sdk_t plain_text;

    /* Actually, some cases here will be checked with code generated by
       edger8r. Here we just want to defend in depth. */
    if(NULL == p_blob || NULL == p_is_resealed)
        return QE_PARAMETER_ERROR;
    
    if(SGX_TRUSTED_EPID_BLOB_SIZE_SDK != blob_size)
        return QE_PARAMETER_ERROR;

    if(!sgx_is_within_enclave(p_blob, blob_size))
        return QE_PARAMETER_ERROR;

    return verify_blob_internal(p_blob, blob_size,
                                p_is_resealed, FALSE, plain_text, NULL);
}


/*
 * An internal function used to sign the EPID signature on the quote body.
 * Prefix "emp_" means it is a pointer points memory outside enclave.
 *
 * For quote with SIG-RL
 * |--------------------------------------------------------------------|
 * |sgx_quote_t|wrap_key_t|iv|payload_size|basic_sig|rl_ver|n2|nrp..|mac|
 * |--------------------------------------------------------------------|
 * For quote without SIG-RL
 * |--------------------------------------------------------------|
 * |sgx_quote_t|wrap_key_t|iv|payload_size|basic_sig|rl_ver|n2|mac|
 * |--------------------------------------------------------------|
 *
 * @param p_epid_context[in] Pointer to the EPID context.
 * @param plaintext[in] Reference to the plain text part of EPID blob.
 * @param p_basename[in] The pointer to basename.
 * @param emp_sig_rl_entries[in] The pointer to SIG-RL entries.
 * @param p_sig_rl_header[in] The header of SIG-RL, within EPC.
 * @param p_sig_rl_signature[in] The ecdsa signature of SIG-RL, within EPC.
 * @param p_enclave_report[in] The input isv report.
 * @param p_nonce[in] The input nonce.
 * @param p_qe_report[out] The output buffer for qe_report.
 * @param emp_quote[out] The output buffer for quote.
 * @param p_quote_body[in] The quote body in EPC.
 * @param sign_size[in] size of the signature.
 * @return ae_error_t AE_SUCCESS for success, otherwise for errors.
 */
static ae_error_t qe_epid_sign(
    MemberCtx *p_epid_context,
    const se_plaintext_epid_data_sdk_t& plaintext,
    const sgx_basename_t *p_basename,
    const SigRlEntry *emp_sig_rl_entries,
    se_sig_rl_t *p_sig_rl_header,
    sgx_ec256_signature_t *p_sig_rl_signature,
    const sgx_report_t *p_enclave_report,
    const sgx_quote_nonce_t *p_nonce,
    sgx_report_t *p_qe_report,
    uint8_t *emp_quote,
    const sgx_quote_t *p_quote_body,
    uint32_t sign_size)
{
    ae_error_t ret = AE_SUCCESS;
    IppStatus ipp_ret = ippStsNoErr;
    sgx_status_t se_ret = SGX_SUCCESS;
    EpidStatus epid_ret = kEpidNoErr;

    se_wrap_key_t wrap_key;
    BasicSignature basic_sig;
    BasicSignature encrypted_basic_sig;
    uint8_t aes_iv[QUOTE_IV_SIZE] = {0};
    uint8_t aes_key[QE_AES_KEY_SIZE] = {0};
    uint8_t aes_tag[SGX_SEAL_TAG_SIZE] = {0};
    Ipp8u seeds[QE_OAEP_SEED_SIZE] = {0};
    sgx_report_data_t qe_report_data = {{0}};
    sgx_target_info_t report_target;
    sgx_ec256_public_t ec_pub_key;
    se_ae_ecdsa_hash_t sig_rl_hash = {{0}};
    IppECResult ec_result = ippECValid ;

    int aes_context_size = 0;
    sgx_sha_state_handle_t sha_context = NULL;
    sgx_sha_state_handle_t sha_quote_context = NULL;
    IppsAES_GCMState *aes_context = NULL;
    IppsRSAPublicKeyState *pub_key = NULL;
    int pub_key_size = 0;
    uint8_t* pub_key_buffer = NULL;
    IppsECCPState *p_ecp = NULL;

    memset(&wrap_key, 0, sizeof(wrap_key));
    memset(&basic_sig, 0, sizeof(basic_sig));
    memset(&encrypted_basic_sig, 0, sizeof(encrypted_basic_sig));
    memset(&report_target, 0, sizeof(report_target));
    memset(&ec_pub_key, 0, sizeof(ec_pub_key));

    se_encrypted_sign_t *emp_p = (se_encrypted_sign_t *)
                                (((sgx_quote_t *)emp_quote)->signature);

    uint8_t* emp_nr = NULL;
    uint32_t match = FALSE;

    /* Sign the quote body and get the basic signature*/
    epid_ret = EpidSignBasic(p_epid_context,
               (uint8_t *)const_cast<sgx_quote_t *>(p_quote_body),
               (uint32_t)QE_QUOTE_BODY_SIZE,
               (uint8_t *)const_cast<sgx_basename_t *>(p_basename),
               sizeof(*p_basename),
               &basic_sig);
    if(kEpidNoErr != epid_ret)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    /* Prepare the context for SHA256 of quote. */
    if(p_qe_report)
    {
        se_ret = sgx_sha256_init(&sha_quote_context);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }

        // Update hash for nonce.
        se_ret = sgx_sha256_update((uint8_t *)const_cast<sgx_quote_nonce_t *>(p_nonce),
                                   sizeof(*p_nonce),
                                   sha_quote_context);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
        // Update hash for the first part of quote.
        se_ret = sgx_sha256_update((uint8_t *)const_cast<sgx_quote_t *>(p_quote_body),
                                   sizeof(*p_quote_body),
                                   sha_quote_context);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
    }

    /* Prepare the context for SHA256 and start calculate the hash of header
     * of SIG-RL. */
    if(emp_sig_rl_entries)
    {
        se_ret = sgx_sha256_init(&sha_context);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }

        /* Calculate the hash of SIG-RL header. */
        se_ret = sgx_sha256_update((Ipp8u *)p_sig_rl_header,
                                   (uint32_t)(sizeof(se_sig_rl_t) - sizeof(SigRlEntry)),
                                   sha_context);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
    }

    // Start encrypt the signature.
    ipp_ret = ippsAES_GCMGetSize(&aes_context_size);
    if(ipp_ret != ippStsNoErr){
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    aes_context = (IppsAES_GCMState *)malloc(aes_context_size);
    if(NULL == aes_context)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    /* Get the random wrap key */
    se_ret = sgx_read_rand(aes_key, sizeof(aes_key));
    if(SGX_SUCCESS != se_ret)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    /* Copy the hash of wrap key into output buffer. */
    se_static_assert(sizeof(wrap_key.key_hash) == sizeof(sgx_sha256_hash_t));
    se_ret = sgx_sha256_msg(aes_key, sizeof(aes_key),
                            (sgx_sha256_hash_t *)wrap_key.key_hash);
    if(SGX_SUCCESS != se_ret)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    //Start encrypt the wrap key by RSA IPP algorithm.
    ipp_ret = create_rsa_pub_key(sizeof(g_qsdk_pub_key_n),
                                 sizeof(g_qsdk_pub_key_e),
                                 g_qsdk_pub_key_n,
                                 g_qsdk_pub_key_e,
                                 &pub_key);
    if(ipp_ret != ippStsNoErr)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    se_ret = sgx_read_rand(seeds, sizeof(seeds));
    if(SGX_SUCCESS != se_ret)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    ipp_ret = ippsRSA_GetBufferSizePublicKey(&pub_key_size, pub_key);
    if (ipp_ret != ippStsNoErr)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    pub_key_buffer = (uint8_t*)malloc(pub_key_size);
    if (pub_key_buffer == NULL)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    ipp_ret = ippsRSAEncrypt_OAEP(aes_key, sizeof(aes_key),
                                        NULL, 0, seeds,
                                        wrap_key.encrypted_key,
                                        pub_key, IPP_ALG_HASH_SHA256,
                                        pub_key_buffer);
    if(ipp_ret != ippStsNoErr)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    ipp_ret = ippsAES_GCMInit(aes_key,
                              sizeof(aes_key),
                              aes_context,
                              aes_context_size);
    memset_s(aes_key, sizeof(aes_key), 0, sizeof(aes_key));
    if(ipp_ret != ippStsNoErr)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    /* Create the random AES IV. */
    se_ret = sgx_read_rand(aes_iv, sizeof(aes_iv));
    if(SGX_SUCCESS != se_ret)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    /* Copy the wrap_key_t into output buffer. */
    memcpy(&emp_p->wrap_key, &wrap_key, sizeof(wrap_key));
    /* Copy the AES IV into output buffer. */
    memcpy(&emp_p->iv, aes_iv, sizeof(aes_iv));
    /* Copy the AES Blob payload size into output buffer. */
    memcpy(&emp_p->payload_size, &sign_size, sizeof(sign_size));

    ipp_ret = ippsAES_GCMStart(aes_iv, sizeof(aes_iv), NULL, 0,
                                      aes_context);
    if(ipp_ret != ippStsNoErr)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    /* Encrypt the basic signature. */
    ipp_ret = ippsAES_GCMEncrypt((Ipp8u *)&basic_sig,
                                        (uint8_t *)&encrypted_basic_sig,
                                        sizeof(encrypted_basic_sig),
                                        aes_context);
    if(ipp_ret != ippStsNoErr)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }
    /* Copy the encrypted basic signature into output buffer. */
    memcpy(&emp_p->basic_sign, &encrypted_basic_sig,
           sizeof(encrypted_basic_sig));

    if(p_qe_report)
    {
        se_ret = sgx_sha256_update((uint8_t *)&wrap_key,
                                   sizeof(wrap_key),
                                   sha_quote_context);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
        se_ret = sgx_sha256_update(aes_iv,
                                   sizeof(aes_iv),
                                   sha_quote_context);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
        se_ret = sgx_sha256_update((uint8_t *)&sign_size,
                                   sizeof(sign_size),
                                   sha_quote_context);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
        se_ret = sgx_sha256_update((uint8_t *)&encrypted_basic_sig,
                                   sizeof(encrypted_basic_sig),
                                   sha_quote_context);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
    }

    /* Start process the SIG-RL. */
    if(emp_sig_rl_entries)
    {
        unsigned int entry_count = 0;
        unsigned int i = 0;
        RLver_t encrypted_rl_ver = {{0}};
        RLCount encrypted_n2 = {{0}};
        entry_count = lv_ntohl(p_sig_rl_header->sig_rl.n2);//entry count for big endian to little endian

        // Continue encrypt the output
        ipp_ret = ippsAES_GCMEncrypt((Ipp8u *)&(p_sig_rl_header->sig_rl.version),
                    (Ipp8u *)&encrypted_rl_ver,
                    sizeof(encrypted_rl_ver),
                    aes_context);
        if(ipp_ret != ippStsNoErr)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
        ipp_ret = ippsAES_GCMEncrypt((Ipp8u *)&(p_sig_rl_header->sig_rl.n2),
                    (Ipp8u *)&encrypted_n2,
                    sizeof(encrypted_n2),
                    aes_context);
        if(ipp_ret != ippStsNoErr)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
        memcpy(&(emp_p->rl_ver), &encrypted_rl_ver,
               sizeof(encrypted_rl_ver));
        memcpy(&(emp_p->rl_num), &encrypted_n2,
               sizeof(encrypted_n2));
        if(p_qe_report)
        {
            se_ret = sgx_sha256_update((uint8_t *)&encrypted_rl_ver,
                                       sizeof(encrypted_rl_ver),
                                       sha_quote_context);
            if(SGX_SUCCESS != se_ret)
            {
                ret = QE_UNEXPECTED_ERROR;
                goto CLEANUP;
            }
            se_ret = sgx_sha256_update((uint8_t *)&encrypted_n2,
                                       sizeof(encrypted_n2),
                                       sha_quote_context);
            if(SGX_SUCCESS != se_ret)
            {
                ret = QE_UNEXPECTED_ERROR;
                goto CLEANUP;
            }
        }

        /* Start process the SIG-RL entries one by one. */
        emp_nr = emp_p->nrp_mac;
        for (i = 0; i < entry_count; i++, emp_nr += sizeof(NrProof))
        {
            /* Generate non-revoke prove one by one. */
            SigRlEntry entry;
            NrProof temp_nr;
            NrProof encrypted_temp_nr;
            memcpy(&entry, emp_sig_rl_entries + i, sizeof(entry));
            memset_s(&temp_nr, sizeof(temp_nr), 0, sizeof(temp_nr));
            memset_s(&encrypted_temp_nr, sizeof(encrypted_temp_nr), 0, sizeof(encrypted_temp_nr));
            epid_ret = EpidNrProve(p_epid_context,
                (uint8_t *)const_cast<sgx_quote_t *>(p_quote_body),
                (uint32_t)QE_QUOTE_BODY_SIZE,
                &basic_sig, // Basic signature with 'b' and 'k' in it
                &entry, //Single entry in SigRl composed of 'b' and 'k'
                &temp_nr); // The generated non-revoked proof
            if(kEpidNoErr != epid_ret)
            {
                if(kEpidSigRevokedInSigRl == epid_ret)
                    match = TRUE;
                else
                {
                    ret = QE_UNEXPECTED_ERROR;
                    goto CLEANUP;
                }
            }

            /* Update the hash of SIG-RL */
            se_ret = sgx_sha256_update((Ipp8u *)&entry,
                                       sizeof(entry), sha_context);
            if(SGX_SUCCESS != se_ret)
            {
                ret = QE_UNEXPECTED_ERROR;
                goto CLEANUP;
            }

            ipp_ret = ippsAES_GCMEncrypt((Ipp8u *)&temp_nr,
                                                (Ipp8u *)&encrypted_temp_nr,
                                                sizeof(NrProof),
                                                aes_context);
            if(ipp_ret != ippStsNoErr)
            {
                ret = QE_UNEXPECTED_ERROR;
                goto CLEANUP;
            }
            memcpy(emp_nr, &encrypted_temp_nr, sizeof(encrypted_temp_nr));

            if(p_qe_report)
            {
                se_ret = sgx_sha256_update((uint8_t *)&encrypted_temp_nr,
                                           sizeof(encrypted_temp_nr),
                                           sha_quote_context);
                if(SGX_SUCCESS != se_ret)
                {
                    ret = QE_UNEXPECTED_ERROR;
                    goto CLEANUP;
                }
            }
        }

        /* Get the final hash of the whole SIG-RL. */
        se_ret =  sgx_sha256_get_hash(sha_context,
                                      (sgx_sha256_hash_t *)&sig_rl_hash.hash);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }

        /* Verify the integraty of SIG-RL by check ECDSA signature. */
        ipp_ret = new_std_256_ecp(&p_ecp);
        if(ipp_ret != ippStsNoErr)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }

        se_static_assert(sizeof(ec_pub_key) == sizeof(plaintext.epid_sk));
        // Both plaintext.epid_sk and ec_pub_key are little endian
        memcpy(&ec_pub_key, plaintext.epid_sk, sizeof(ec_pub_key));

        // se_ecdsa_verify_internal will take ec_pub_key as little endian
        se_ret = se_ecdsa_verify_internal(p_ecp,
                                          &ec_pub_key,
                                          p_sig_rl_signature,
                                          &sig_rl_hash,
                                          &ec_result);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
        else if(ippECValid != ec_result)
        {
            ret = QE_SIGRL_ERROR;
            goto CLEANUP;
        }
        else if(match)
        {
            ret = QE_REVOKED_ERROR;
            goto CLEANUP;
        }
    }
    else
    {
        se_static_assert(sizeof(emp_p->rl_ver) == sizeof(RLver_t));
        se_static_assert(sizeof(emp_p->rl_num) == sizeof(RLCount));
        uint8_t temp_buf[sizeof(RLver_t) + sizeof(RLCount)] = {0};
        uint8_t encrypted_temp_buf[sizeof(temp_buf)] = {0};
        ipp_ret = ippsAES_GCMEncrypt(temp_buf,
                                            (Ipp8u *)&encrypted_temp_buf,
                                            sizeof(encrypted_temp_buf),
                                            aes_context);
        if(ipp_ret != ippStsNoErr)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
        /* This will copy both encrypted rl_ver and encrypted rl_num into
           Output buffer. */
        memcpy(&emp_p->rl_ver, &encrypted_temp_buf,
               sizeof(encrypted_temp_buf));

        if(p_qe_report)
        {
            se_ret = sgx_sha256_update((uint8_t *)&encrypted_temp_buf,
                                       sizeof(encrypted_temp_buf),
                                       sha_quote_context);
            if(SGX_SUCCESS != se_ret)
            {
                ret = QE_UNEXPECTED_ERROR;
                goto CLEANUP;
            }
        }
    }

    ipp_ret = ippsAES_GCMGetTag(aes_tag, sizeof(aes_tag), aes_context);
    if(ipp_ret != ippStsNoErr)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }
    memcpy((uint8_t *)&(emp_p->basic_sign) + sign_size, &aes_tag,
           sizeof(aes_tag));

    if(p_qe_report)
    {
        se_ret = sgx_sha256_update(aes_tag, sizeof(aes_tag),
                                   sha_quote_context);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
        se_ret =  sgx_sha256_get_hash(sha_quote_context,
                                      (sgx_sha256_hash_t *)&qe_report_data);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
        memcpy(&(report_target.attributes),
               &(((const sgx_report_t *)p_enclave_report)->body.attributes),
               sizeof(report_target.attributes));
        memcpy(&(report_target.mr_enclave),
               &(((const sgx_report_t *)p_enclave_report)->body.mr_enclave),
               sizeof(report_target.mr_enclave));
        memcpy(&(report_target.misc_select),
            &(((const sgx_report_t *)p_enclave_report)->body.misc_select),
            sizeof(report_target.misc_select));
        se_ret = sgx_create_report(&report_target, &qe_report_data, p_qe_report);
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_PARAMETER_ERROR;
            goto CLEANUP;
        }
    }

CLEANUP:
    memset_s(aes_key, sizeof(aes_key), 0, sizeof(aes_key));
    sgx_sha256_close(sha_context);
    sgx_sha256_close(sha_quote_context);
    if(aes_context)
        free(aes_context);
    if(pub_key)
        secure_free_rsa_pub_key(sizeof(g_qsdk_pub_key_n), sizeof(g_qsdk_pub_key_e), pub_key);
    if(pub_key_buffer)
        free(pub_key_buffer);
    secure_free_std_256_ecp(p_ecp);
    return ret;
}


/*
 * External function used to get quote. Prefix "emp_" means it is a pointer
 * points memory outside enclave.
 *
 * @param p_blob[in, out] Pointer to the EPID Blob.
 * @param blob_size[in] The size of EPID Blob, in bytes.
 * @param p_enclave_report[in] The application enclave's report.
 * @param quote_type[in] The type of quote, random based or name based.
 * @param p_spid[in] Pointer to SPID.
 * @param p_nonce[in] Pointer to nonce.
 * @param emp_sig_rl[in] Pointer to SIG-RL.
 * @param sig_rl_size[in] The size of SIG-RL, in bytes.
 * @param p_qe_report[out] Pointer to QE report, which reportdata is
 *                         sha256(nonce || quote)
 * @param emp_quote[out] Pointer to the output buffer for quote.
 * @param quote_size[in] The size of emp_quote, in bytes.
 * @param pce_isvsvn[in] The ISVSVN of PCE.
 * @return ae_error_t AE_SUCCESS for success, otherwise for errors.
 */
uint32_t get_quote(
    uint8_t *p_blob,
    uint32_t blob_size,
    const sgx_report_t *p_enclave_report,
    sgx_quote_sign_type_t quote_type,
    const sgx_spid_t *p_spid,
    const sgx_quote_nonce_t *p_nonce,
    const uint8_t *emp_sig_rl,
    uint32_t sig_rl_size,
    sgx_report_t *p_qe_report,
    uint8_t *emp_quote,
    uint32_t quote_size,
    sgx_isv_svn_t pce_isvsvn)
{
    ae_error_t ret = AE_SUCCESS;
    EpidStatus epid_ret = kEpidNoErr;
    MemberCtx *p_epid_context = NULL;
    sgx_quote_t quote_body;
    uint8_t is_resealed = 0;
    sgx_basename_t basename = {{0}};
    uint64_t sign_size = 0;
    sgx_status_t se_ret = SGX_SUCCESS;
    sgx_report_t qe_report;
    uint64_t required_buffer_size = 0;
    se_sig_rl_t sig_rl_header;
    se_plaintext_epid_data_sdk_t plaintext;
    sgx_ec256_signature_t ec_signature;

    memset(&quote_body, 0, sizeof(quote_body));
    memset(&sig_rl_header, 0, sizeof(sig_rl_header));
    memset(&plaintext, 0, sizeof(plaintext));
    memset(&ec_signature, 0, sizeof(ec_signature));


    /* Actually, some cases here will be checked with code generated by
       edger8r. Here we just want to defend in depth. */
    if((NULL == p_blob)
       || (NULL == p_enclave_report)
       || (NULL == p_spid)
       || (NULL == emp_quote)
       || (!quote_size)
       || ((NULL != emp_sig_rl) && (sig_rl_size < sizeof(se_sig_rl_t)
                                                  + 2 * SE_ECDSA_SIGN_SIZE))
       || ((NULL == emp_sig_rl) && (sig_rl_size != 0)))
        return QE_PARAMETER_ERROR;
    if(SGX_TRUSTED_EPID_BLOB_SIZE_SDK != blob_size)
        return QE_PARAMETER_ERROR;
    if(SGX_LINKABLE_SIGNATURE != quote_type
       && SGX_UNLINKABLE_SIGNATURE != quote_type)
        return QE_PARAMETER_ERROR;
    if(!p_nonce && p_qe_report)
        return QE_PARAMETER_ERROR;
    if(p_nonce && !p_qe_report)
        return QE_PARAMETER_ERROR;

    /* To reduce the memory footprint of QE, we should leave sig_rl and
       quote buffer outside enclave. */
    if(!sgx_is_outside_enclave(emp_sig_rl, sig_rl_size))
        return QE_PARAMETER_ERROR;
    if(!sgx_is_outside_enclave(emp_quote, quote_size))
        return QE_PARAMETER_ERROR;

    /* Check whether p_blob is copied into EPC. If we want to reduce the
       memory usage, maybe we can leave the p_blob outside EPC. */
    if(!sgx_is_within_enclave(p_blob, blob_size))
        return QE_PARAMETER_ERROR;
    if(!sgx_is_within_enclave(p_enclave_report, sizeof(*p_enclave_report)))
        return QE_PARAMETER_ERROR;
    if(!sgx_is_within_enclave(p_spid, sizeof(*p_spid)))
        return QE_PARAMETER_ERROR;
    /* If the code reach here, if p_nonce is NULL, then p_qe_report will be
       NULL also. So we only check p_nonce here.*/
    if(p_nonce)
    {
        /* Actually Edger8r will alloc the buffer within EPC, this is just kind
           of defense in depth. */
        if(!sgx_is_within_enclave(p_nonce, sizeof(*p_nonce)))
            return QE_PARAMETER_ERROR;
        if(!sgx_is_within_enclave(p_qe_report, sizeof(*p_qe_report)))
            return QE_PARAMETER_ERROR;
    }

    /* Verify the input report. */
    if(SGX_SUCCESS != sgx_verify_report(p_enclave_report))
        return QE_PARAMETER_ERROR;

    /* Verify EPID p_blob and create the context */
    ret = verify_blob_internal(p_blob,
        blob_size,
        &is_resealed,        
        TRUE,
        plaintext,
        &p_epid_context);
    if(AE_SUCCESS != ret)
        goto CLEANUP;

    /* If SIG-RL is provided, we should check its size. */
    if(emp_sig_rl)
    {
        uint64_t temp_size = 0;
        uint64_t n2 = 0;

        memcpy(&sig_rl_header, emp_sig_rl, sizeof(sig_rl_header));
        if(sig_rl_header.protocol_version != SE_EPID_SIG_RL_VERSION)
        {
            ret = QE_PARAMETER_ERROR;
            goto CLEANUP;
        }

        if(sig_rl_header.epid_identifier != SE_EPID_SIG_RL_ID)
        {
            ret = QE_PARAMETER_ERROR;
            goto CLEANUP;
        }

        if(memcmp(&sig_rl_header.sig_rl.gid, &plaintext.epid_group_cert.gid,
                   sizeof(sig_rl_header.sig_rl.gid)))
        {
            ret = QE_PARAMETER_ERROR;
            goto CLEANUP;
        }
        temp_size = se_get_sig_rl_size(&sig_rl_header);
        if(temp_size != sig_rl_size)
        {
            ret = QE_PARAMETER_ERROR;
            goto CLEANUP;
        }

        se_static_assert(sizeof(ec_signature.x) == SE_ECDSA_SIGN_SIZE);
        se_static_assert(sizeof(ec_signature.y) == SE_ECDSA_SIGN_SIZE);
        memcpy(ec_signature.x,
               emp_sig_rl + sig_rl_size - (SE_ECDSA_SIGN_SIZE * 2),
               sizeof(ec_signature.x));
        SWAP_ENDIAN_32B(ec_signature.x);
        memcpy(ec_signature.y,
               emp_sig_rl + sig_rl_size - (SE_ECDSA_SIGN_SIZE * 1),
               sizeof(ec_signature.y));
        SWAP_ENDIAN_32B(ec_signature.y);

        n2 = SWAP_4BYTES(sig_rl_header.sig_rl.n2);
        temp_size = sizeof(EpidSignature) - sizeof(NrProof)
                    + n2 * sizeof(NrProof);
        if(temp_size > UINT32_MAX)
        {
            ret = QE_PARAMETER_ERROR;
            goto CLEANUP;
        }
        sign_size = temp_size;
    }
    else
    {
        sign_size = sizeof(BasicSignature)
                    + sizeof(uint32_t) // rl_ver
                    + sizeof(uint32_t); // rl_num
    }

    /* Verify sizeof basename is large enough and it should always be true*/
    se_static_assert(sizeof(basename) > sizeof(*p_spid));
    /* Because basename has already been zeroed,
       so we don't need to concatenating with 0s.*/
    memcpy(&basename, p_spid, sizeof(*p_spid));
    if(SGX_UNLINKABLE_SIGNATURE == quote_type)
    {
        uint8_t *p = (uint8_t *)&basename + sizeof(*p_spid);
        se_ret = sgx_read_rand(p, sizeof(basename) - sizeof(*p_spid));
        if(SGX_SUCCESS != se_ret)
        {
            ret = QE_UNEXPECTED_ERROR;
            goto CLEANUP;
        }
    }

    epid_ret = EpidRegisterBaseName(p_epid_context, (uint8_t *)&basename,
        sizeof(basename));
    if(kEpidNoErr != epid_ret)
    {
        ret = QE_UNEXPECTED_ERROR;
        goto CLEANUP;
    }

    required_buffer_size = SE_QUOTE_LENGTH_WITHOUT_SIG + sign_size;

    /* We should make sure the buffer size is big enough. */
    if(quote_size < required_buffer_size)
    {
        ret = QE_PARAMETER_ERROR;
        goto CLEANUP;
    }

    /* Copy the data in the report into quote body. */
    memset(emp_quote, 0, quote_size);
    quote_body.version = QE_QUOTE_VERSION;
    quote_body.sign_type = (uint16_t)quote_type;
    quote_body.pce_svn = pce_isvsvn; // Both are little endian
    quote_body.xeid = plaintext.xeid; // Both are little endian
    se_static_assert(sizeof(plaintext.epid_group_cert.gid) == sizeof(uint32_t));
    se_static_assert(sizeof(quote_body.epid_group_id) == sizeof(uint32_t));
    ((uint8_t *)(&quote_body.epid_group_id))[0] = plaintext.epid_group_cert.gid.data[3];
    ((uint8_t *)(&quote_body.epid_group_id))[1] = plaintext.epid_group_cert.gid.data[2];
    ((uint8_t *)(&quote_body.epid_group_id))[2] = plaintext.epid_group_cert.gid.data[1];
    ((uint8_t *)(&quote_body.epid_group_id))[3] = plaintext.epid_group_cert.gid.data[0];
    memcpy(&quote_body.basename, &basename, sizeof(quote_body.basename));

    // Get the QE's report.
    se_ret = sgx_create_report(NULL, NULL, &qe_report);
    if(SGX_SUCCESS != se_ret)
    {
        ret = QE_PARAMETER_ERROR;
        goto CLEANUP;
    }

    // Copy QE's security version in to Quote body.
    quote_body.qe_svn = qe_report.body.isv_svn;

    // Copy the incoming report into Quote body.
    memcpy(&quote_body.report_body, &(p_enclave_report->body),
           sizeof(quote_body.report_body));
    /* Because required_buffer_size is larger than signature_len, so if we
       get here, then no integer overflow will ocur. */
    quote_body.signature_len = (uint32_t)(sizeof(se_wrap_key_t)
                               + QUOTE_IV_SIZE
                               + sizeof(uint32_t)
                               + sign_size
                               + sizeof(sgx_mac_t));

    /* Make the signature. */
    ret = qe_epid_sign(p_epid_context,
                       plaintext,
                       &basename,
                       emp_sig_rl ? ((const se_sig_rl_t *)emp_sig_rl)->sig_rl.bk
                                    : NULL,
                       &sig_rl_header,
                       &ec_signature,
                       p_enclave_report,
                       p_nonce,
                       p_qe_report,
                       emp_quote,
                       &quote_body,
                       (uint32_t)sign_size);
    if(AE_SUCCESS != ret)
    {
        // Only need to clean the buffer after the fixed length part.
        memset_s(emp_quote + sizeof(sgx_quote_t), quote_size - sizeof(sgx_quote_t),
                 0, quote_size - sizeof(sgx_quote_t));
        goto CLEANUP;
    }

    memcpy(emp_quote, &quote_body, sizeof(sgx_quote_t));

CLEANUP:
    if(p_epid_context)
        EpidMemberDelete(&p_epid_context);
    return ret;
}

