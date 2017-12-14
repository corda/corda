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
#include "sgx_trts.h"
#include "pve_qe_common.h"
#include "pve_hardcoded_tlv_data.h"
#include "sgx_utils.h"
#include "byte_order.h"
#include "ipp_wrapper.h"
#include <string.h>
#include <stdlib.h>
#include "pek_pub_key.h"
#include "util.h"

 /**
  * File: provision_msg3.cpp 
  * Description: Provide the implementation of code to generate ProvMsg3
  *
  * Core Code for Provision Enclave
  * Piece-meal processing used to process SigRl of ProvMsg2 and generate EpidSignature of ProvMsg3
  */

//initialize the epid signature header according to sigrl_header
static pve_status_t gen_epid_signature_header(const SigRl *sigrl_header,
                                              EPIDMember *epid_member,
                                              const uint8_t *nonce_challenge,
                                              EpidSignature *epid_header)
{
    if(NULL!=sigrl_header){
        memcpy(&epid_header->n2, &sigrl_header->n2, sizeof(sigrl_header->n2));//copy size into header in BigEndian
        memcpy(&epid_header->rl_ver, &sigrl_header->version, sizeof(sigrl_header->version)); //Copy rl_ver in BigEndian
    }else{
        memset(&epid_header->n2, 0, sizeof(epid_header->n2));  //set n2 and rl_ver to 0 if no sigrl provided
        memset(&epid_header->rl_ver, 0, sizeof(epid_header->rl_ver));
    }
    //challenge nonce value is used as sign message
    uint32_t msg_len = CHALLENGE_NONCE_SIZE;
    EpidStatus epid_ret = EpidSignBasic(epid_member, 
        const_cast<uint8_t *>(reinterpret_cast<const uint8_t *>(nonce_challenge)), 
        msg_len, NULL, 0,  &epid_header->sigma0);//generate EpidSignature Header inside EPC memory
    if(kEpidNoErr != epid_ret){
        return epid_error_to_pve_error(epid_ret);
    }
    return PVEC_SUCCESS;
}

static uint32_t pve_htonl(uint32_t x)
{
    uint32_t l0=x&0xFF;
    uint32_t l1=(x>>8)&0xFF;
    uint32_t l2=(x>>16)&0xFF;
    uint32_t l3=(x>>24)&0xFF;
    return l3|(l2<<8)|(l1<<16)|(l0<<24);
}

//This function will first generate EPIDSig Header according to sigrl_header
//After that, piece meal algorithm is used to
//   decode SigRl Entry in msg2 and update hash value 
//   generate EPIDSigEntry in msg3 and encrypt it
// The memory of msg2 for SigRl and  msg3 for EPIDSigEntry are all outside enclave
//     So that we need first copy each SigRl Entry into EPC memory, generate EPIDSigEntry inside EPC memory 
//            and copy it out after it is generated
//   The function assumes the size of SigRl has been verfied and it is not checked again here. 
// Finally it checks whether the hash value is valid according to ECDHA Sign in the end of SigRl to verify data is not modified
// A TLV Header for the EpidSignature should have been prepared in EPC memory signature_tlv_header
//It is assumed that the parm->sigrl_count>0 when the function is called and the size of sigrl has been checked
//EpidSignature TLV format: TLVHeader:EpidSignatureHeader:NrProof1:NrProof2:...:NrProofn
static pve_status_t gen_msg3_signature(const proc_prov_msg2_blob_input_t *msg2_blob_input,
                                       prov_msg3_parm_t *parm, 
                                       external_memory_byte_t *emp_signature,//pointer to external memory to write the EPID Signature
                                       uint32_t& signature_size) 
{
    pve_status_t ret = PVEC_SUCCESS;
    uint32_t cur_size = static_cast<uint32_t>(EPID_SIGNATURE_TLV_HEADER_SIZE+sizeof(EpidSignature)-sizeof(NrProof));
    //emp_proof_entry is pointer to external memory to each entry of the epid signature body in external memory
    external_memory_byte_t *emp_proof_entry = emp_signature + cur_size; 
    //emp_sigrl_entry is pointer to external memory to each entry of the sigrl_body in external memory
    const external_memory_byte_t *emp_sigrl_entry = parm->emp_sigrl_sig_entries;
    uint32_t i,entry_count = parm->sigrl_count;
    bool revoked = false;
    uint8_t sigrl_sign[2*ECDSA_SIGN_SIZE];//temp buffer in EPC to hold ECDSA signature
    //declare a buffer to hold encrypted data of TLV Header and EpidSignature Header
    uint8_t signature_header_to_encrypt[EPID_SIGNATURE_TLV_HEADER_SIZE + sizeof(EpidSignature)-sizeof(NrProof)];
    SigRlEntry temp1;
    NrProof temp3;
    uint32_t tlv_payload_size = 0;
    const SigRl *sigrl_header = NULL;
    sgx_status_t sgx_status = SGX_SUCCESS;

    memset(sigrl_sign, 0, sizeof(sigrl_sign));
    memset(&temp1, 0, sizeof(temp1));
    memset(&temp3, 0, sizeof(temp3));
    memset(signature_header_to_encrypt, 0, sizeof(signature_header_to_encrypt));

    if(entry_count>0){
        sigrl_header = &parm->sigrl_header.sig_rl;//use the sigrl_header only when sigrl is available
        if(signature_size <  cur_size){//size of output buffer at least to hold currently generated data
            ret = PVEC_INSUFFICIENT_MEMORY_ERROR;
            goto ret_point;
        }
        if((signature_size-cur_size)/entry_count<sizeof(NrProof)){//safe way to check buffer overflow of output buffer to avoid integer overflow
            ret = PVEC_INSUFFICIENT_MEMORY_ERROR;
            goto ret_point;
        }
        tlv_payload_size = static_cast<uint32_t>(sizeof(EpidSignature)-sizeof(NrProof) + entry_count * sizeof(NrProof));
    }else{
        tlv_payload_size = static_cast<uint32_t>(sizeof(EpidSignature)-sizeof(NrProof)); //payload size for 0 entry, only basic signature with n2 and rl_ver to be 0
        if(signature_size <  cur_size){//size of output buffer at least to hold currently generated data
            ret = PVEC_INSUFFICIENT_MEMORY_ERROR;
            goto ret_point;
        }
    }

    memcpy(signature_header_to_encrypt, EPID_SIGNATURE_TLV_HEADER, EPID_SIGNATURE_TLV_HEADER_SIZE); //copy in the hard coded EPID Signature TLV Header
    tlv_payload_size = pve_htonl(tlv_payload_size);
    //overwritten the bigendian size in TLV Header. It is assumed that the size in TLV Header is always 4 bytes//Long format
    memcpy(signature_header_to_encrypt+EPID_SIGNATURE_TLV_SIZE_OFFSET, &tlv_payload_size, sizeof(tlv_payload_size));

    ret = gen_epid_signature_header(sigrl_header, parm->epid_member, msg2_blob_input->challenge_nonce, &parm->signature_header);//Now generate EpidSignatureHeader 
    if( PVEC_SUCCESS != ret )
        goto ret_point;
    //Now encrypt the TLV Header and signature header including basic signature while the parm->signature_header is kept since piece-meal processing will use it
    memcpy(signature_header_to_encrypt+EPID_SIGNATURE_TLV_HEADER_SIZE, &parm->signature_header, cur_size-EPID_SIGNATURE_TLV_HEADER_SIZE);
    ret =pve_aes_gcm_encrypt_inplace_update(parm->p_msg3_state, signature_header_to_encrypt, cur_size);
    if( PVEC_SUCCESS != ret )
        goto ret_point;

    pve_memcpy_out(emp_signature, signature_header_to_encrypt, cur_size);//copy out tlv header, basic signature and other epid signature header info if required

    if(NULL==parm->emp_sigrl_sig_entries){//finish if no sigrl avaiable
        signature_size = cur_size;
        goto ret_point;
    }

    //copy the ECDSA Signature of the SigRl in ProvMsg2 into EPC memory in advance to defense in depth
    pve_memcpy_in(sigrl_sign, emp_sigrl_entry + entry_count *sizeof(SigRlEntry), 2*ECDSA_SIGN_SIZE);

    //piece-meal processing
    //The pointer calculation will never overflow as soon as size of sigrl and epid signature have been checked in advance
    //TO BE CLARIFY:We assume that the ecdsa signature follows entry array of SigRl directly 
    //  If later we change the format of sigrl to include extra data which should be ecdsa signed too,
    //       we need do the modification here: change the sigrl_sign and do more sha update
    signature_size = static_cast<uint32_t>(cur_size+entry_count *sizeof(NrProof));//recalculate output buffer
    //Start piece meal processing for each entry
    for(i=0;i<entry_count; i++){
        pve_memcpy_in(&temp1, emp_sigrl_entry, sizeof(temp1));//copy the data into trusted memory
        //update hash for the SigRl Entry
        sgx_status = sgx_sha256_update(reinterpret_cast<uint8_t *>(&temp1), sizeof(SigRlEntry), parm->sha_state);
        if(sgx_status != SGX_SUCCESS){
            ret = sgx_error_to_pve_error(sgx_status);
            goto ret_point;
        }
        //generate NrProof for the SigRl Entry in trusted memory
        EpidStatus epid_ret = EpidNrProve(parm->epid_member,
            const_cast<uint8_t *>(msg2_blob_input->challenge_nonce),//msg to sign
            CHALLENGE_NONCE_SIZE,
            &parm->signature_header.sigma0, //B and K in BasicSignature
            &temp1,  //B and K in sigrl entry
            &temp3); //output one NrProof
        if(kEpidNoErr != epid_ret){
            if(kEpidSigRevokedInSigRl == epid_ret){
                revoked = true;//if revoked, we could not return revoked status immediately until integrity checking passed
            }else{
                ret = epid_error_to_pve_error(epid_ret);
                goto ret_point;
            }
        }
        //encrypt the NrProof in EPC
        ret = pve_aes_gcm_encrypt_inplace_update(parm->p_msg3_state, reinterpret_cast<uint8_t *>(&temp3), sizeof(temp3));
        if(ret != PVEC_SUCCESS){
            goto ret_point;
        }
        pve_memcpy_out(emp_proof_entry, &temp3, sizeof(temp3));//copy encrypted NrProof out of enclave
        emp_sigrl_entry += sizeof(SigRlEntry);//pointer to next SigRlEntry in external memory
        emp_proof_entry += sizeof(NrProof);//pointer to next NrProof in external memory
    }

    se_ae_ecdsa_hash_t out;
    //generate SHA256 hash value of the whole SigRl
    if((sgx_status=sgx_sha256_get_hash(parm->sha_state,
        reinterpret_cast<sgx_sha256_hash_t *>(&out))) !=
        SGX_SUCCESS){
            ret = sgx_error_to_pve_error(sgx_status);
            goto ret_point;
    }
    //Verify the signature is signed by EPIDSK 
    ret = verify_epid_ecdsa_signature(sigrl_sign, parm->local_xegb, &out);
    if(ret == PVEC_MSG_ERROR){
        ret = PVEC_SIGRL_INTEGRITY_CHECK_ERROR;//If sigrl signature checking failed, someone must has modified the message
    }

ret_point:
    //clear unsealed NrProof to defense in depth for potential attack to match attacker created sigrl entry with key 
    //While we need not clear BasicSignature
    (void)memset_s(&temp3, sizeof(temp3), 0, sizeof(temp3));
    if(ret == PVEC_SUCCESS &&revoked){
        ret = PVEC_REVOKED_ERROR;
    }
    return ret;
}

//The function will try to do some preparation for piece meal encryption of field1 in ProvMsg3
//  It prepares the encryption state in msg3
//@parm: structure to provide some input data to generate ProvMsg3 and also some states for piece meal processing
//@return PVEC_SUCCESS on success and error code if failed
static pve_status_t proc_msg3_state_init(prov_msg3_parm_t *parm, const sgx_key_128bit_t *pwk2)
{
    pve_status_t ret = PVEC_SUCCESS;
    sgx_status_t se_ret = SGX_SUCCESS;

    if((se_ret=sgx_read_rand(parm->iv, IV_SIZE))!=SGX_SUCCESS){//randomly generate the IV
        ret = se_read_rand_error_to_pve_error(se_ret);
        goto ret_point;
    }
    se_static_assert(SK_SIZE==sizeof(sgx_cmac_128bit_tag_t)); /*size of sgx_cmac_128bit_tag_t should same as value of SK_SIZE*/

    //initialize state for piece-meal encryption of field of ProvMsg3
    ret = pve_aes_gcm_encrypt_init((const uint8_t *)pwk2,  parm->iv, IV_SIZE,//pwk2 as the key 
        NULL, 0,//no AAD used for the encryption of EpidSignature
        &parm->p_msg3_state, &parm->msg3_state_size);
ret_point:
    return ret;
}

//Function to generate Field1_0 of ProvMsg3
//@msg2_blob_input, input decoded ProvMsg2 info
//@join_proof, output the join proof and the escrow data which is encrypted f of Private Key
//@return PVEC_SUCCESS on success and error code on failure
//The function assume all required inputs have been prepared in msg2_blob_input
static pve_status_t gen_msg3_join_proof_escrow_data(const proc_prov_msg2_blob_input_t *msg2_blob_input,
                                                    join_proof_with_escrow_t& join_proof)
{
    pve_status_t ret = PVEC_SUCCESS;
    BitSupplier epid_prng = (BitSupplier) epid_random_func;
    FpElemStr temp_f;
    //first generate private key f randomly before sealing it by PSK
    FpElemStr *f = &temp_f;
    sgx_status_t sgx_status = SGX_SUCCESS;
    JoinRequest *join_r = &join_proof.jr;
    EpidStatus epid_ret  = kEpidNoErr;
    psvn_t psvn;
    memset(&temp_f, 0, sizeof(temp_f));

    //randomly generate the private EPID key f, host to network transformation not required since server will not decode it
    if(PVEC_SUCCESS != (ret=gen_epid_priv_f(f))){
        goto ret_point;
    }

    //generate JoinP using f before encryption by calling EPID library
    memset(join_r, 0, sizeof(JoinRequest));//first clear to 0
    //generate JoinP to fill it in field1_0_0 by EPID library

    epid_ret = EpidRequestJoin(
        &msg2_blob_input->group_cert.key, //EPID Group Cert from ProvMsgs2 used
        reinterpret_cast<const IssuerNonce *>(msg2_blob_input->challenge_nonce),
        f, epid_prng,
        NULL, kSha256, join_r);
    if(kEpidNoErr != epid_ret){
        ret = epid_error_to_pve_error(epid_ret);
        goto ret_point;
    }

    //get PSK
    sgx_key_128bit_t psk;
    memcpy(&psvn.cpu_svn, &msg2_blob_input->equiv_pi.cpu_svn, sizeof(psvn.cpu_svn));
    memcpy(&psvn.isv_svn, &msg2_blob_input->equiv_pi.pve_svn, sizeof(psvn.isv_svn));
    ret = get_pve_psk(&psvn, &psk);
    if(PVEC_SUCCESS != ret){
        goto ret_point;
    }

    join_proof.escrow.version = 0;//version 0 used for escrow data
    //now we could seal f by PSK
    ret = se_read_rand_error_to_pve_error(sgx_read_rand(join_proof.escrow.iv, IV_SIZE));
    if(PVEC_SUCCESS != ret){
        goto ret_point;
    }

    se_static_assert(sizeof(psk)==sizeof(sgx_aes_gcm_128bit_key_t)); /*sizeof sgx_aes_gcm_128bit_key_t tshould be same as size of psk*/
    se_static_assert(sizeof(sgx_aes_gcm_128bit_tag_t)==sizeof(join_proof.escrow.mac)); /*sizeof sgx_aes_gcm_128bit_tag_t should be same as MAC_SIZE*/
    sgx_status = sgx_rijndael128GCM_encrypt(reinterpret_cast<const sgx_aes_gcm_128bit_key_t *>(&psk),
        reinterpret_cast<uint8_t *>(f), sizeof(*f), reinterpret_cast<uint8_t *>(&join_proof.escrow.f),
        join_proof.escrow.iv, IV_SIZE, NULL, 0, 
        reinterpret_cast<sgx_aes_gcm_128bit_tag_t *>(join_proof.escrow.mac));
    if(SGX_SUCCESS != sgx_status){
        ret = sgx_error_to_pve_error(sgx_status);
    }
ret_point:
    (void)memset_s(&psk, sizeof(psk), 0, sizeof(psk));//clear the key
    (void)memset_s(&temp_f, sizeof(temp_f), 0, sizeof(temp_f));//clear temp f in stack
    if(PVEC_SUCCESS != ret){
        (void)memset_s(&join_proof, sizeof(join_proof), 0, sizeof(join_proof));
    }
    return ret;
}

//Function to create data for ProvMsg3 generation 
// The sigrl of ProvMsg2 will processed in this function in piece-meal method
//@msg2_blob_input: structure to hold decoded data of ProvMsg2
//@performance_rekey_used[in]: 1 if performance rekey used or 0 if not
//@msg3_parm: structure to hold most information to generate ProvMsg3
//@msg3_output: structure to hold output data to create ProvMsg3
//@emp_epid_sig: output buffer to external memory for variable length EpidSignature
//@epid_sig_buffer_size: size in bytes of buffer emp_epid_sig
//@return PVEC_SUCCESS on success and error code if failed
pve_status_t gen_prov_msg3_data(const proc_prov_msg2_blob_input_t *msg2_blob_input,
                                prov_msg3_parm_t& msg3_parm,
                                uint8_t performance_rekey_used,
                                gen_prov_msg3_output_t *msg3_output,
                                external_memory_byte_t *emp_epid_sig, 
                                uint32_t epid_sig_buffer_size)
{
    pve_status_t ret = PVEC_SUCCESS;
    sgx_status_t sgx_status = SGX_SUCCESS;
    uint8_t temp_buf[JOIN_PROOF_TLV_TOTAL_SIZE];
    uint8_t *data_to_encrypt = NULL;
    uint8_t  size_to_encrypt = 0;
    uint8_t  pwk2_tlv_buffer[PWK2_TLV_TOTAL_SIZE];
    sgx_key_128bit_t *pwk2=reinterpret_cast<sgx_key_128bit_t *>(pwk2_tlv_buffer+PWK2_TLV_HEADER_SIZE);
    uint8_t report_data_payload[MAC_SIZE + HARD_CODED_JOIN_PROOF_WITH_ESCROW_TLV_SIZE + NONCE_2_SIZE + PEK_MOD_SIZE];
    uint8_t* pdata = &report_data_payload[0];
    sgx_report_data_t report_data = { 0 };
    uint8_t aad[sizeof(GroupId)+sizeof(device_id_t)+CHALLENGE_NONCE_SIZE];
    IppsRSAPublicKeyState *pub_key = NULL;
    uint8_t *pub_key_buffer = NULL;
    IppStatus ipp_status;
    int pub_key_size;
    Ipp8u seeds[PVE_RSA_SEED_SIZE]={0};
    const signed_pek_t& pek = msg2_blob_input->pek;
    uint32_t le_e;
    int i;
    uint8_t le_n[sizeof(pek.n)];
    static_assert(sizeof(pek.n)==384, "pek.n should be 384 bytes");
    device_id_t *device_id_in_aad= (device_id_t *)(aad+sizeof(GroupId));
    join_proof_with_escrow_t* join_proof_with_escrow=reinterpret_cast<join_proof_with_escrow_t *>(temp_buf+JOIN_PROOF_TLV_HEADER_SIZE);
    se_static_assert(sizeof(join_proof_with_escrow_t)+JOIN_PROOF_TLV_HEADER_SIZE==JOIN_PROOF_TLV_TOTAL_SIZE); /*unmatched hardcoded size*/
    se_static_assert(sizeof(sgx_key_128bit_t)==PWK2_TLV_TOTAL_SIZE-PWK2_TLV_HEADER_SIZE); /*unmatched PWK2 size*/
    memset(temp_buf, 0 ,sizeof(temp_buf));
    memset(aad, 0, sizeof(aad));
    memset(pwk2, 0, sizeof(sgx_key_128bit_t));
    memcpy(pwk2_tlv_buffer, PWK2_TLV_HEADER, PWK2_TLV_HEADER_SIZE);
    msg3_output->is_join_proof_generated=false;
    msg3_output->is_epid_sig_generated=false;

    if ((msg2_blob_input->pce_target_info.attributes.flags & SGX_FLAGS_PROVISION_KEY) != SGX_FLAGS_PROVISION_KEY ||
        (msg2_blob_input->pce_target_info.attributes.flags & SGX_FLAGS_DEBUG) != 0){
        //PCE must have access to provisioning key
        //Can't be debug PCE
        ret = PVEC_PARAMETER_ERROR;
        goto ret_point;
    }

    if(!performance_rekey_used){
        //the temp_buf used for join_proof_with_escrow tlv
        memcpy(temp_buf, JOIN_PROOF_TLV_HEADER, JOIN_PROOF_TLV_HEADER_SIZE);//first copy in tlv header
        ret = gen_msg3_join_proof_escrow_data(msg2_blob_input, *join_proof_with_escrow);//generate the tlv payload
        if( PVEC_SUCCESS != ret )
            goto ret_point;
        msg3_output->is_join_proof_generated = true;
        data_to_encrypt = temp_buf;
        size_to_encrypt = JOIN_PROOF_TLV_TOTAL_SIZE;
    }
    //now encrypt field1
    ret = se_read_rand_error_to_pve_error(sgx_read_rand(msg3_output->field1_iv, IV_SIZE));//randomly generate IV
    if( PVEC_SUCCESS != ret)
        goto ret_point;
    memcpy(aad, &msg2_blob_input->group_cert.key.gid,sizeof(GroupId));//start to prepare AAD
    memcpy(&device_id_in_aad->fmsp, &msg2_blob_input->equiv_pi.fmsp, sizeof(fmsp_t));
    memcpy(&device_id_in_aad->psvn.cpu_svn, &msg2_blob_input->equiv_pi.cpu_svn, sizeof(sgx_cpu_svn_t));
    memcpy(&device_id_in_aad->psvn.isv_svn, &msg2_blob_input->equiv_pi.pve_svn, sizeof(sgx_isv_svn_t));
    memset(&device_id_in_aad->ppid, 0, sizeof(device_id_in_aad->ppid));
    ret = pve_rng_generate(NONCE_2_SIZE*8, msg3_output->n2);
    if(PVEC_SUCCESS !=ret){
        goto ret_point;
    }
    ret = get_pwk2(&device_id_in_aad->psvn, msg3_output->n2, pwk2);
    if( PVEC_SUCCESS != ret )
        goto ret_point;

    memcpy(aad+sizeof(GroupId)+sizeof(device_id_t), msg2_blob_input->challenge_nonce, CHALLENGE_NONCE_SIZE);
    se_static_assert(sizeof(sgx_aes_gcm_128bit_key_t)==SK_SIZE); /*sizeof sgx_aes_gcm_128bit_key_t should be same as TCB size*/
    se_static_assert(sizeof(sgx_aes_gcm_128bit_tag_t)==MAC_SIZE); /*sizeof sgx_aes_gcm_128bit_tag_t should be same as MAC_SIZE*/
    sgx_status = sgx_rijndael128GCM_encrypt(reinterpret_cast<const sgx_aes_gcm_128bit_key_t *>(pwk2),
        data_to_encrypt, size_to_encrypt, msg3_output->field1_data,
        msg3_output->field1_iv, IV_SIZE, aad, static_cast<uint32_t>(sizeof(GroupId)+sizeof(device_id_t)+CHALLENGE_NONCE_SIZE),
        reinterpret_cast<sgx_aes_gcm_128bit_tag_t *>(msg3_output->field1_mac));//encrypt field1
    if(SGX_SUCCESS != sgx_status){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }
    if( msg2_blob_input->is_previous_pi_provided ){
        //preparing the encryption state of ProvMsg3 and encrypt inplace of msg3_inside enclave (field1_0 and field1_1)
        //The function will randomly set the iv value too
        ret = proc_msg3_state_init(&msg3_parm, pwk2);
        if( PVEC_SUCCESS!=ret )
            goto ret_point; 
        //Now start piece-meal generation of EPIDsign 
        ret = gen_msg3_signature(msg2_blob_input, &msg3_parm, emp_epid_sig, epid_sig_buffer_size);
        if( PVEC_SUCCESS!=ret )
            goto ret_point;
        msg3_output->is_epid_sig_generated = true;
        msg3_output->epid_sig_output_size = epid_sig_buffer_size;
        memcpy(msg3_output->epid_sig_iv, msg3_parm.iv, IV_SIZE);
        //generate MAC in EPC
        ret = pve_aes_gcm_get_mac(msg3_parm.p_msg3_state, msg3_output->epid_sig_mac);
        if (PVEC_SUCCESS != ret)
            goto ret_point;
    }

    le_e = lv_ntohl(pek.e);
    se_static_assert(sizeof(pek.n)==sizeof(le_n));  /*unmatched size of pek.n*/
    //endian swap
    for(i=0;i<(int)(sizeof(pek.n)/sizeof(pek.n[0]));i++){
        le_n[i]=pek.n[sizeof(pek.n)/sizeof(pek.n[0])-i-1];
    }

    ipp_status = create_rsa_pub_key(sizeof(pek.n), sizeof(pek.e),
        reinterpret_cast<const Ipp32u *>(le_n), &le_e, &pub_key);
    if(ippStsNoErr != ipp_status){
        ret = ipp_error_to_pve_error(ipp_status);
        goto ret_point;
    }

    ipp_status = ippsRSA_GetBufferSizePublicKey(&pub_key_size, pub_key);
    if(ippStsNoErr != ipp_status){
        ret = ipp_error_to_pve_error(ipp_status);
        goto ret_point;
    }
    if(SGX_SUCCESS != (sgx_status =sgx_read_rand(seeds, PVE_RSA_SEED_SIZE))){
        ret = se_read_rand_error_to_pve_error(sgx_status);
        goto ret_point;
    }
    pub_key_buffer = (uint8_t *)malloc(pub_key_size);
    if(NULL ==pub_key_buffer){ 
        ret = PVEC_INSUFFICIENT_MEMORY_ERROR;
        goto ret_point;
    }
    ipp_status = ippsRSAEncrypt_OAEP(reinterpret_cast<const Ipp8u *>(pwk2_tlv_buffer), PWK2_TLV_TOTAL_SIZE, NULL, 0, seeds, 
        msg3_output->encrypted_pwk2, pub_key, IPP_ALG_HASH_SHA256, pub_key_buffer);
    if(ippStsNoErr != ipp_status){
        ret = ipp_error_to_pve_error(ipp_status);
        goto ret_point;
    }

    // X = (NT)MAC_PWK2(... (NT)E_PWK2((T)(JoinP, f)) ...) | (NT)E_PWK2((T)(JoinP, f)) | (NT)PWK2N | (NT)E_PEK((T)PWK2)
    // REPORT.ReportData == SHA256[X] 
    memcpy(pdata, msg3_output->field1_mac, MAC_SIZE);
    pdata += MAC_SIZE;
    if (!performance_rekey_used){
        memcpy(pdata, msg3_output->field1_data, HARD_CODED_JOIN_PROOF_WITH_ESCROW_TLV_SIZE);
        pdata += HARD_CODED_JOIN_PROOF_WITH_ESCROW_TLV_SIZE;
    }
    memcpy(pdata, msg3_output->n2, NONCE_2_SIZE);
    pdata += NONCE_2_SIZE;
    memcpy(pdata, msg3_output->encrypted_pwk2, PEK_MOD_SIZE);
    pdata += PEK_MOD_SIZE;
    se_static_assert(sizeof(report_data) >= sizeof(sgx_sha256_hash_t)); /*report data is no large enough*/
    sgx_status = sgx_sha256_msg(report_data_payload, (uint32_t)(pdata - &report_data_payload[0]), reinterpret_cast<sgx_sha256_hash_t *>(&report_data));
    if (SGX_SUCCESS != sgx_status){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }
    sgx_status = sgx_create_report(&msg2_blob_input->pce_target_info, &report_data, &msg3_output->pwk2_report);
    if (SGX_SUCCESS != sgx_status){
        ret = sgx_error_to_pve_error(sgx_status);
        goto ret_point;
    }

ret_point:
    (void)memset_s(aad, sizeof(aad), 0, sizeof(aad));
    (void)memset_s(temp_buf, sizeof(temp_buf), 0, sizeof(temp_buf));
    (void)memset_s(pwk2_tlv_buffer, sizeof(pwk2_tlv_buffer),0,sizeof(pwk2_tlv_buffer));
    if(pub_key){
        secure_free_rsa_pub_key(sizeof(pek.n), sizeof(pek.e), pub_key);
    }
    if(pub_key_buffer){
        free(pub_key_buffer);
    }
    return ret;
}
