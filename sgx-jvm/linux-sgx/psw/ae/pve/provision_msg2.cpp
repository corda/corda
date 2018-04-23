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

#include "msg3_parm.h"
#include "se_sig_rl.h"
#include "cipher.h"
#include "helper.h"
#include "pve_qe_common.h"
#include "pek_pub_key.h"
#include "byte_order.h"
#include <string.h>
#include <stdlib.h>

/**
  * File: provision_msg2.cpp
  * Description: Provide the implementation of code to decrypt TIK from decoded ProvMsg2
  *
  * Core Code of Provision Encla
  * sigrl will be processed in ProvMsg3 generation
  */


///Function to verify that EPID cert type and version is correct for sigrl
static pve_status_t verify_sigrl_cert_type_version(const se_sig_rl_t *sigrl_cert)
{
    if(SE_EPID_SIG_RL_ID!=sigrl_cert->epid_identifier||
        SE_EPID_SIG_RL_VERSION!=sigrl_cert->protocol_version)
        return PVEC_SIGRL_INTEGRITY_CHECK_ERROR;
    return PVEC_SUCCESS;
}

//The function assumed that the SigRL is available in the ProvMsg2
//It will copy the SigRL Header to EPC memory: msg3_parm->sigrl_header
//The function will partially update the SHA256 hash value (only SigRL Header) for piece-meal ECDSA signature generation
//And it also calculates number of sigRL entries and verifies the size of the SigRL matches it exactly
//The format of SigRL is assumed to be
//   (SigRLCertHeader:SigRlEntry1:SigRlEntry2:...:SigRlEntryn:ECDSASig)
//Where SigRLCertHeader including EPIDVersion,CertType and SigRLHeader
static pve_status_t prov_msg2_proc_sigrl_header(const external_memory_byte_t* emp_sigrl,
                                                uint32_t sigrl_size,
                                                prov_msg3_parm_t *msg3_parm)
{
    sgx_status_t sgx_status = SGX_SUCCESS;
    pve_status_t pve_status = PVEC_SUCCESS;
    const uint32_t sigrl_header_size = static_cast<uint32_t>(sizeof(se_sig_rl_t) - sizeof(SigRlEntry));
    if(sigrl_size<sigrl_header_size+2*SE_ECDSA_SIGN_SIZE){
        //sigrl with too small size, it should contains at least sigrl header and the ECDSA Signature
        return PVEC_SIGRL_INTEGRITY_CHECK_ERROR;//signature not checked so integrity error
    }
    pve_memcpy_in(&msg3_parm->sigrl_header, emp_sigrl, sigrl_header_size);//copy in sigrl header
    msg3_parm->emp_sigrl_sig_entries = emp_sigrl+sigrl_header_size;
    pve_status = verify_sigrl_cert_type_version(&msg3_parm->sigrl_header);
    if( PVEC_SUCCESS!=pve_status )
        return pve_status;
    sgx_status = sgx_sha256_init(&msg3_parm->sha_state);//init sigrl hash
    if(SGX_SUCCESS!=sgx_status)
        return sgx_error_to_pve_error(sgx_status);
    //update hash for SigRL header saved in EPC memory previously
    sgx_status = sgx_sha256_update(reinterpret_cast<const uint8_t *>(&msg3_parm->sigrl_header), sigrl_header_size, msg3_parm->sha_state);
    if(SGX_SUCCESS!=sgx_status)
        return sgx_error_to_pve_error(sgx_status);

    uint32_t entry_count = msg3_parm->sigrl_count = lv_ntohl(msg3_parm->sigrl_header.sig_rl.n2);//get the sigrl_count safely
    //now check whether the sigrl count matches the total size of the sigrl inside the msg
    uint32_t safe_sigrl_size = sigrl_header_size + 2*ECDSA_SIGN_SIZE;//constant size which will not overflow
    if( (UINT32_MAX-safe_sigrl_size)/sizeof(SigRlEntry)<entry_count){//check for integer overflow
        return PVEC_INTEGER_OVERFLOW_ERROR;
    }
    safe_sigrl_size += entry_count * static_cast<uint32_t>(sizeof(SigRlEntry));//calculate size safely now
    if(safe_sigrl_size != sigrl_size){//SigRL size must exactly match the expectation
        return PVEC_SIGRL_INTEGRITY_CHECK_ERROR;
    }
    return PVEC_SUCCESS;
}

//Function to unseal old epid blob, verify it and use it to prepare epid library state
//   so that later we could use it to generate EPID Signature
//It will be used only when PreviousPSVN is avaiable in ProvMsg2
static pve_status_t prepare_epid_member(const proc_prov_msg2_blob_input_t *msg2_blob_input, prov_msg3_parm_t *msg3_parm)
{
    pve_status_t ret_status = PVEC_SUCCESS;
    sgx_status_t sgx_status = SGX_SUCCESS;

    if(!msg2_blob_input->is_previous_pi_provided)
        return PVEC_UNEXPECTED_ERROR;

    const sgx_sealed_data_t *old_epid_data_blob = reinterpret_cast<const sgx_sealed_data_t *>(msg2_blob_input->old_epid_data_blob);

    //try to unseal the old epid blob
    if(sgx_get_encrypt_txt_len(old_epid_data_blob)!=sizeof(se_secret_epid_data_sdk_t)||
        sgx_get_add_mac_txt_len(old_epid_data_blob)!=sizeof(se_plaintext_epid_data_sdk_t))
            return PVEC_EPID_BLOB_ERROR; //return PVEC_EPID_BLOB_ERROR to tell AESM to backup retrial of old epid blob

    se_plaintext_epid_data_sdk_t epid_cert;
    se_secret_epid_data_sdk_t epid_data;
    uint32_t epid_data_len = sizeof(epid_data);
    uint32_t epid_cert_len = sizeof(epid_cert);
    BitSupplier epid_prng = (BitSupplier)epid_random_func;
    EpidStatus epid_ret = kEpidNoErr;
    memset(&epid_cert, 0 ,sizeof(epid_cert));
    memset(&epid_data, 0, sizeof(epid_data));    //now start unseal epid blob
    if((sgx_status=sgx_unseal_data(const_cast<sgx_sealed_data_t *>(old_epid_data_blob),
        reinterpret_cast<uint8_t *>(&epid_cert), &epid_cert_len,
        reinterpret_cast<uint8_t *>(&epid_data),&epid_data_len)) != SGX_SUCCESS){
            if(sgx_status == SGX_ERROR_MAC_MISMATCH){
                ret_status = PVEC_EPID_BLOB_ERROR;//return PVEC_EPID_BLOB_ERROR to tell AESM to backup retrial of old epid blob
            }else{
                ret_status = sgx_error_to_pve_error(sgx_status);
                if(ret_status == PVEC_INVALID_CPU_ISV_SVN){
                    ret_status = PVEC_PARAMETER_ERROR;//The input epid blob is too new so that it is not supported
                }
            }
            goto ret_point;
    }

    //check whether sigrl previous psvn matches psvn in secret part of old epid blob
    if(0!=memcmp(&msg2_blob_input->previous_pi.cpu_svn, &epid_cert.equiv_cpu_svn,sizeof(sgx_cpu_svn_t))||
        0 != memcmp(&msg2_blob_input->previous_pi.pve_svn, &epid_cert.equiv_pve_isv_svn, sizeof(sgx_isv_svn_t))||
        epid_cert.xeid != msg3_parm->local_xegb.xeid){
            ret_status = PVEC_EPID_BLOB_ERROR;//return PVEC_EPID_BLOB_ERROR to tell AESM to backup retrial of old epid blob
            goto ret_point;
    }

    if(epid_cert.seal_blob_type != PVE_SEAL_EPID_KEY_BLOB||
        epid_cert.epid_key_version != EPID_KEY_BLOB_VERSION_SDK){
        ret_status = PVEC_EPID_BLOB_ERROR;//if the epid blob version does not match, which means the data is not an epid blob sealed by current version of PvE/QE
        goto ret_point;
    }

    //Previous gid is provided since this function assumes that Previous PSVN is provided
    //And the previous gid must be same as the gid in old epid blob cert
    if(memcmp(&epid_cert.epid_group_cert.gid, &msg2_blob_input->previous_gid, sizeof(GroupId))!=0||
        memcmp(&epid_data.epid_private_key.gid, &msg2_blob_input->previous_gid, sizeof(GroupId))!=0){
        ret_status = PVEC_EPID_BLOB_ERROR;
        goto ret_point;
    }
    //start preparing epid state for EPID signature generation
    epid_ret = EpidMemberCreate(
        &epid_cert.epid_group_cert,//group cert from old epid blob
        &epid_data.epid_private_key,//group private key from old epid blob
        &epid_data.member_precomp_data,
        epid_prng,
        NULL,
        &msg3_parm->epid_member);
    if(kEpidNoErr != epid_ret){
        ret_status = epid_error_to_pve_error(epid_ret);
        goto ret_point;
    }
    epid_ret = EpidMemberSetHashAlg(msg3_parm->epid_member, kSha256);
    if(kEpidNoErr != epid_ret){
        ret_status = epid_error_to_pve_error(epid_ret);
    }
ret_point:
    (void)memset_s(&epid_data, sizeof(epid_data), 0, sizeof(epid_data));//clear secret data from stack
    return ret_status;
}

//Function to process data from ProvMsg2 and generate data for ProvMsg3 on success
//Both emp_sigrl and emp_epid_sig are in external memory where emp_ prefix represents "external memory pointer"
//@msg2_blob_input, input the decoded data of ProvMsg2
//@performance_rekey_used, 1 if performance rekey used, 0 if not
//@emp_sigrl, the optional input sigrl in external memory (where emp_ prefix stands for "external memory pointer")
//@sigrl_size, size in bytes of emp_sigrl
//@msg3_output, output buffer to hold data to create ProvMsg3
//@emp_epid_sig, output the EPID Signature which is in external memory if required
//@epid_sig_buffer_size: input the size of buffer emp_epid_sig
//@return PVEC_SUCCESS on success and error code on failure
//   PVEC_EPID_BLOB_ERROR is returned if msg2_blob_input.old_epid_data_blob is required but it is invalid and
//   msg2_blob_input.previous_pi should be filled in by a Previous platform information from ProvMsg2
pve_status_t proc_prov_msg2_data(const proc_prov_msg2_blob_input_t *msg2_blob_input,    //Input data of the ProvMsg2
                            uint8_t performance_rekey_used,             // if in performance rekey mode
                            const external_memory_byte_t *emp_sigrl,  //optional sigrl inside external memory
                            uint32_t sigrl_size,
                            gen_prov_msg3_output_t *msg3_output,     //output data for msg3 generation
                            external_memory_byte_t *emp_epid_sig,    //optional buffer to output EPID Signature
                            uint32_t epid_sig_buffer_size)
{
    uint8_t tcb[SK_SIZE];
    pve_status_t ret = PVEC_SUCCESS;
    uint8_t pek_result = SGX_EC_INVALID_SIGNATURE;
    prov_msg3_parm_t msg3_parm;
    //initialize buffers to 0 according to coding style
    memset(tcb, 0, sizeof(tcb));
    memset(&msg3_parm, 0, sizeof(msg3_parm));

    sgx_status_t sgx_status = verify_xegb_with_default(msg2_blob_input->xegb, &pek_result, msg3_parm.local_xegb);
    if(SGX_SUCCESS != sgx_status){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }else if(pek_result != SGX_EC_VALID){
        ret = PVEC_XEGDSK_SIGN_ERROR;
        goto ret_point;
    }

    sgx_status = check_pek_signature(msg2_blob_input->pek, (sgx_ec256_public_t*)msg3_parm.local_xegb.pek_sk, &pek_result);
    if(SGX_SUCCESS != sgx_status){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }else if(pek_result != SGX_EC_VALID){
        ret = PVEC_PEK_SIGN_ERROR; //use a special error code to indicate PEK Signature error
        goto ret_point;
    }

    ret = check_signature_of_group_pub_cert(&msg2_blob_input->group_cert, msg3_parm.local_xegb.epid_sk);
    if(PVEC_SUCCESS != ret){
        goto ret_point;
    }

    // we must parse SigRL header to find count of sigrl entries
    if( msg2_blob_input->is_previous_pi_provided ){//We need to generate Basic Signature if sigrl_psvn present even if sigrl is not available
        //Initialize for EPID library function to prepare for piece meal processing
        ret = prepare_epid_member(msg2_blob_input, &msg3_parm);//old epid data blob is required
        if( PVEC_SUCCESS!=ret )
            goto ret_point;
        if(NULL!=emp_sigrl){
            //process sigrl_header for hash value generation (used by ECDSA signature)
            ret = prov_msg2_proc_sigrl_header( emp_sigrl, sigrl_size, &msg3_parm);
            if( PVEC_SUCCESS!=ret )
                goto ret_point;
        }
    }else if(NULL!=emp_sigrl){//sigrl provided but sigrl_psvn not available
        ret = PVEC_MSG_ERROR;
        goto ret_point;
    }
    //Now we could generate the ProvMsg3. SigRL of ProvMsg2 will be parsed in this function too if available
    ret = gen_prov_msg3_data(msg2_blob_input, msg3_parm, performance_rekey_used, msg3_output, emp_epid_sig,
        epid_sig_buffer_size);
ret_point:
    (void)memset_s(tcb, sizeof(tcb), 0, sizeof(tcb));
    if(NULL!=msg3_parm.p_msg3_state)
        pve_aes_gcm_encrypt_fini(msg3_parm.p_msg3_state, msg3_parm.msg3_state_size);
    if(NULL!=msg3_parm.sha_state)
        sgx_sha256_close(msg3_parm.sha_state);
    if(NULL!=msg3_parm.epid_member){
        EpidMemberDelete(&msg3_parm.epid_member);
    }
    return ret;
}
