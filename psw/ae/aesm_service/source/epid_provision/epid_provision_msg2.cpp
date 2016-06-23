/*
 * Copyright (C) 2011-2016 Intel Corporation. All rights reserved.
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

#include "type_length_value.h"
#include "sgx_tcrypto_internal.h"
#include "epid_utility.h"
#include "aeerror.h"
#include "PVEClass.h"
#include "aesm_rand.h"
#include "se_wrapper.h"
#include <assert.h>

/**
* File: epid_provision_msg2.cpp 
* Description: Provide the untrusted implementation of code to process ProvMsg2
*
* Untrusted Code for EPID Provision
*/
#define MSG2_TOP_FIELDS_COUNT_WITH_SIGRL    4
#define MSG2_TOP_FIELDS_COUNT_WITHOUT_SIGRL 3
#define MSG2_TOP_FIELD_NONCE tlvs_msg2[0]
#define MSG2_TOP_FIELD_DATA  tlvs_msg2[1]
#define MSG2_TOP_FIELD_MAC   tlvs_msg2[2]
#define MSG2_TOP_FIELD_SIGRL tlvs_msg2[3]
#define MSG2_FIELD1_MAX_COUNT   8
#define MSG2_FIELD1_MIN_COUNT   6
#define MSG2_FIELD1_GROUP_CERT  tlvs_field1[0]
#define MSG2_FIELD1_NONCE       tlvs_field1[1]
#define MSG2_FIELD1_PREV_PSVN   tlvs_field1[2]//optional field
#define MSG2_FIELD1_PSID        tlvs_field1[psid_index]//psid_index is 2 if optional PREV_PSVN is not present or 3 if it is present
#define MSG2_FIELD1_ENC_TCB     tlvs_field1[psid_index+1]
#define MSG2_FIELD1_MAC_TCB     tlvs_field1[psid_index+2]
#define MSG2_FIELD1_DEVICE_ID   tlvs_field1[psid_index+3]
#define PREV_GID_INDEX          (psid_index+4)
#define MSG2_FIELD1_PREV_GID    tlvs_field1[PREV_GID_INDEX]


//Function to verify that EPID SigRL type and version is correct for sigrl
static ae_error_t verify_sigrl_cert_type_version(const se_sig_rl_t *sigrl_cert)
{
    if(sigrl_cert->epid_identifier!=SE_EPID_SIG_RL_ID||
        sigrl_cert->protocol_version!=SE_EPID_SIG_RL_VERSION)
        return PVE_INTEGRITY_CHECK_ERROR;
    return AE_SUCCESS;
}

static ae_error_t msg2_integrity_checking(const TLVsMsg& tlvs_msg2)
{
    uint32_t tlv_count = tlvs_msg2.get_tlv_count();
    if(tlv_count != MSG2_TOP_FIELDS_COUNT_WITH_SIGRL && tlv_count != MSG2_TOP_FIELDS_COUNT_WITHOUT_SIGRL)
        return PVE_INTEGRITY_CHECK_ERROR;
    if(MSG2_TOP_FIELD_NONCE.type != TLV_NONCE || MSG2_TOP_FIELD_NONCE.size != NONCE_SIZE || MSG2_TOP_FIELD_NONCE.version != TLV_VERSION_1)
        return PVE_INTEGRITY_CHECK_ERROR;
    if(MSG2_TOP_FIELD_NONCE.header_size != SMALL_TLV_HEADER_SIZE)//NONCE TLV has small header size
        return PVE_INTEGRITY_CHECK_ERROR;
    if(MSG2_TOP_FIELD_DATA.type != TLV_BLOCK_CIPHER_TEXT || MSG2_TOP_FIELD_DATA.version != TLV_VERSION_1)
        return PVE_INTEGRITY_CHECK_ERROR;
    if(MSG2_TOP_FIELD_MAC.type != TLV_MESSAGE_AUTHENTICATION_CODE || MSG2_TOP_FIELD_MAC.version != TLV_VERSION_1 || MSG2_TOP_FIELD_MAC.size != MAC_SIZE)
        return PVE_INTEGRITY_CHECK_ERROR;
    if(MSG2_TOP_FIELD_MAC.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_INTEGRITY_CHECK_ERROR;
    if(tlv_count == MSG2_TOP_FIELDS_COUNT_WITH_SIGRL){
        if(MSG2_TOP_FIELD_SIGRL.type != TLV_EPID_SIG_RL || MSG2_TOP_FIELD_SIGRL.version != TLV_VERSION_1)
            return PVE_INTEGRITY_CHECK_ERROR;
        if(MSG2_TOP_FIELD_SIGRL.size < 2*SE_ECDSA_SIGN_SIZE + sizeof(se_sig_rl_t))
            return PVE_INTEGRITY_CHECK_ERROR;
        if(MSG2_TOP_FIELD_SIGRL.header_size != LARGE_TLV_HEADER_SIZE)
            return PVE_INTEGRITY_CHECK_ERROR;
        return verify_sigrl_cert_type_version(reinterpret_cast<const se_sig_rl_t *>(MSG2_TOP_FIELD_SIGRL.payload));
    }
    return AE_SUCCESS;
}

//check the format of msg2_field1 and copy correspondent data input msg2_blob_input
static ae_error_t msg2_field1_msg_check_copy(const TLVsMsg& tlvs_field1, proc_prov_msg2_blob_input_t& msg2_blob_input, const signed_pek_t& pek)
{
    uint32_t tlv_count = tlvs_field1.get_tlv_count();
    uint32_t psid_index = 2;
    msg2_blob_input.is_previous_psvn_provided = false;
    if(tlv_count<MSG2_FIELD1_MIN_COUNT||tlv_count>MSG2_FIELD1_MAX_COUNT){
        return PVE_MSG_ERROR;
    }
    uint32_t i;
    for(i=0;i<tlv_count;++i)
        if(tlvs_field1[i].version != TLV_VERSION_1)
            return PVE_MSG_ERROR;

    if(MSG2_FIELD1_PREV_PSVN.type == TLV_EPID_PSVN){//EPID_PSVN TLV is available
        psid_index++;
        msg2_blob_input.is_previous_psvn_provided = true;
        if(tlv_count!=MSG2_FIELD1_MAX_COUNT){
            return PVE_MSG_ERROR;//make sure the number of TLVs are correct
        }
        if(MSG2_FIELD1_PREV_PSVN.size != sizeof(psvn_t)){
            return PVE_MSG_ERROR;
        }
        if(MSG2_FIELD1_PREV_PSVN.header_size != SMALL_TLV_HEADER_SIZE){
            return PVE_MSG_ERROR;
        }
        if(MSG2_FIELD1_PREV_GID.type != TLV_EPID_GID ||
            MSG2_FIELD1_PREV_GID.size != sizeof(GroupID)){
            return PVE_MSG_ERROR;
        }
        if(MSG2_FIELD1_PREV_GID.header_size != SMALL_TLV_HEADER_SIZE){
            return PVE_MSG_ERROR;
        }
        if(0!=memcpy_s(&msg2_blob_input.previous_gid, sizeof(GroupID), MSG2_FIELD1_PREV_GID.payload, MSG2_FIELD1_PREV_GID.size)){
            return PVE_UNEXPECTED_ERROR;
        }
        if(0!=memcpy_s(&msg2_blob_input.previous_psvn, sizeof(psvn_t), MSG2_FIELD1_PREV_PSVN.payload, MSG2_FIELD1_PREV_PSVN.size)){
            return PVE_UNEXPECTED_ERROR;
        }
    }else if(tlv_count!=MSG2_FIELD1_MIN_COUNT){
        return PVE_MSG_ERROR;//make sure number of TLVs are correct
    }

    if(MSG2_FIELD1_GROUP_CERT.type != TLV_EPID_GROUP_CERT||
        MSG2_FIELD1_GROUP_CERT.size != sizeof(signed_epid_group_cert_t)||
        MSG2_FIELD1_GROUP_CERT.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_MSG_ERROR;
    if(MSG2_FIELD1_NONCE.type != TLV_NONCE||
        MSG2_FIELD1_NONCE.size != CHALLENGE_NONCE_SIZE||
        MSG2_FIELD1_NONCE.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_MSG_ERROR;
    if(MSG2_FIELD1_DEVICE_ID.type != TLV_DEVICE_ID ||
        MSG2_FIELD1_DEVICE_ID.size != sizeof(device_id_t)||
        MSG2_FIELD1_DEVICE_ID.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_MSG_ERROR;
    if(MSG2_FIELD1_ENC_TCB.type != TLV_BLOCK_CIPHER_TEXT||
        MSG2_FIELD1_ENC_TCB.size != BLOCK_CIPHER_TEXT_TLV_PAYLOAD_SIZE(SK_SIZE))
        return PVE_MSG_ERROR;
    if(MSG2_FIELD1_MAC_TCB.type != TLV_MESSAGE_AUTHENTICATION_CODE||
        MSG2_FIELD1_MAC_TCB.size != MAC_SIZE||
        MSG2_FIELD1_MAC_TCB.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_MSG_ERROR;
    if(MSG2_FIELD1_PSID.type != TLV_PS_ID ||
        MSG2_FIELD1_PSID.size != sizeof(psid_t)||
        MSG2_FIELD1_PSID.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_MSG_ERROR;
    sgx_sha256_hash_t psid_hash;
    ae_error_t ret = sgx_error_to_ae_error(sgx_sha256_msg(reinterpret_cast<const uint8_t *>(&pek.n), static_cast<uint32_t>(sizeof(pek.n)+sizeof(pek.e)), &psid_hash));
    if(AE_SUCCESS != ret)
        return ret;
    if(0!=memcmp(&psid_hash, MSG2_FIELD1_PSID.payload, sizeof(psid_hash)))//PSID does not match
        return PVE_MSG_ERROR;

    tlv_msg_t tcb_data = block_cipher_tlv_get_encrypted_text(MSG2_FIELD1_ENC_TCB);
    if(0!=memcpy_s(&msg2_blob_input.group_cert, sizeof(msg2_blob_input.group_cert), MSG2_FIELD1_GROUP_CERT.payload, MSG2_FIELD1_GROUP_CERT.size)||
        0!=memcpy_s(&msg2_blob_input.challenge_nonce, CHALLENGE_NONCE_SIZE, MSG2_FIELD1_NONCE.payload, MSG2_FIELD1_NONCE.size)||
        0!=memcpy_s(&msg2_blob_input.equivalent_psvn, sizeof(psvn_t), device_id_tlv_get_psvn(MSG2_FIELD1_DEVICE_ID),sizeof(psvn_t))||
        0!=memcpy_s(&msg2_blob_input.fmsp, sizeof(fmsp_t), device_id_tlv_get_fmsp(MSG2_FIELD1_DEVICE_ID), sizeof(fmsp_t))||
        0!=memcpy_s(&msg2_blob_input.tcb_iv, IV_SIZE, block_cipher_tlv_get_iv(MSG2_FIELD1_ENC_TCB), IV_SIZE)||
        0!=memcpy_s(&msg2_blob_input.encrypted_tcb, SK_SIZE, tcb_data.msg_buf, tcb_data.msg_size)||
        0!=memcpy_s(&msg2_blob_input.tcb_mac, MAC_SIZE, MSG2_FIELD1_MAC_TCB.payload, MSG2_FIELD1_MAC_TCB.size)){
            return PVE_UNEXPECTED_ERROR;
    }
    return AE_SUCCESS;
}

//Function to check message header of ProvMsg2 to determine whether it is valid
//@msg2_header, input the message header of ProvMsg2
//@return AE_SUCCESS if the message header is valid ProvMsg2 or error code if there're any problems
static ae_error_t check_prov_msg2_header(const provision_response_header_t *msg2_header, uint32_t msg2_size)
{
    if(msg2_header->protocol != SE_EPID_PROVISIONING || msg2_header->type != TYPE_PROV_MSG2 ||
        msg2_header->version != TLV_VERSION_1){
            return PVE_INTEGRITY_CHECK_ERROR;
    }
    uint32_t size_in_header = lv_ntohl(msg2_header->size);
    if(size_in_header + PROVISION_RESPONSE_HEADER_SIZE != msg2_size)
        return PVE_INTEGRITY_CHECK_ERROR;
    return AE_SUCCESS;
}

static uint32_t estimate_epid_sig_size(uint32_t sigrl_size)
{
    uint32_t sigrl_body_size = 0;
    uint32_t sigrl_extra_size = static_cast<uint32_t>(sizeof(se_sig_rl_t)-sizeof(SigRLEntry)+2*ECDSA_SIGN_SIZE);
    if(sigrl_size<=sigrl_extra_size){
        return static_cast<uint32_t>(sizeof(EPIDSignature)-sizeof(NRProof));
    }else{
        sigrl_body_size = sigrl_size - sigrl_extra_size;
        size_t entry_count = sigrl_body_size/sizeof(SigRLEntry);
        return static_cast<uint32_t>(sizeof(EPIDSignature)-sizeof(NRProof)+sizeof(NRProof)*entry_count);
    }
}

static ae_error_t gen_msg3_header(const gen_prov_msg3_output_t& msg3_output, const uint8_t xid[XID_SIZE], provision_request_header_t *msg3_header, uint32_t& msg3_size)
{
    msg3_header->protocol = SE_EPID_PROVISIONING;
    msg3_header->version = TLV_VERSION_1;
    msg3_header->type = TYPE_PROV_MSG3;
    size_t field1_size = 0;
    if(msg3_output.is_join_proof_generated){
        field1_size = BLOCK_CIPHER_TEXT_TLV_SIZE(HARD_CODED_JOIN_PROOF_WITH_ESCROW_TLV_SIZE)+MAC_TLV_SIZE(MAC_SIZE);
    }else{
        field1_size = BLOCK_CIPHER_TEXT_TLV_SIZE(0)+MAC_TLV_SIZE(MAC_SIZE);
    }
    size_t total_body_size = NONCE_TLV_SIZE(NONCE_SIZE) + BLOCK_CIPHER_TEXT_TLV_SIZE(field1_size)+MAC_TLV_SIZE(MAC_SIZE);
    if(msg3_output.is_epid_sig_generated){
        total_body_size += BLOCK_CIPHER_TEXT_TLV_SIZE(msg3_output.epid_sig_output_size)+MAC_TLV_SIZE(MAC_SIZE);
    }
    uint32_t size_in_net = _htonl(total_body_size);
    if(0!=memcpy_s(&msg3_header->size, sizeof(msg3_header->size), &size_in_net, sizeof(size_in_net)))
        return PVE_UNEXPECTED_ERROR;//size in Big Endian in message header of ProvMsg3
    if(total_body_size>msg3_size - PROVISION_REQUEST_HEADER_SIZE ){//make sure buffer size provided by user is large enough
        return PVE_INSUFFICIENT_MEMORY_ERROR;
    }
    if(0!=memcpy_s(msg3_header->xid, XID_SIZE, xid, XID_SIZE))
        return PVE_UNEXPECTED_ERROR; //copy transaction id of ProvMsg2
    msg3_size = static_cast<uint32_t>(total_body_size + PROVISION_REQUEST_HEADER_SIZE);
    return AE_SUCCESS;
}

//Function to decode ProvMsg2 and generate ProvMsg3 on success
//ProvMsg2 Format:
//R, E+MAC(**), [SigRL with ECDSA Sig]
//@msg2, the input buffer for ProvMsg2 in TLV encoded format
//@msg2_size, size of buffer msg2
//@pek, the input pek got from endpoint selection protocol
//@epid_blob, input an optional old epid data blob used to generate non-revoke proof if required
//@blob_size, the buffer size of epid_blob
//@ek2,  an output parameter for EK2 used in provision protocol which could be reused in proc_prov_msg4
//@previous_psvn, an optional output buffer for Previous SigRL PSVN if old epid blob is missing or invalid
//       so that caller could repeat ProvMsg1 and ProvMsg4 to retrieve backuped old epid data blob
//@msg3, output the ProvMsg3 in TLV encoded format
//@msg3_size: input the size of buffer msg3
//@return AE_SUCCESS on success and error code on failure
//   PVE_EPID_BLOB_ERROR is returned if old_epid_blob is required but it is invalid or not provided and
//   previous_psvn will be filled in by a Previous SigRL PSVN
uint32_t CPVEClass::proc_prov_msg2(
        const uint8_t*  msg2,
        uint32_t msg2_size,
        const signed_pek_t& pek,
        const uint8_t*  epid_blob,
        uint32_t  blob_size,
        uint8_t ek2[SK_SIZE],
        psvn_t*  previous_psvn,
        uint8_t*  msg3,
        uint32_t msg3_size)
{
    ae_error_t ret = AE_SUCCESS;
    const se_sig_rl_t *sigrl = NULL;
    uint32_t sigrl_size = 0;
    uint8_t *epid_sig = NULL;
    uint8_t *decoded_msg2 = NULL;
    uint8_t *encrypted_field1 = NULL;
    uint8_t aad[PROVISION_RESPONSE_HEADER_SIZE+sizeof(RLver_t)+sizeof(GroupID)];
    size_t aad_size = PROVISION_RESPONSE_HEADER_SIZE;
    const provision_response_header_t *msg2_header = reinterpret_cast<const provision_response_header_t *>(msg2);
    provision_request_header_t *msg3_header = reinterpret_cast<provision_request_header_t *>(msg3);
    if(msg2_size < PROVISION_RESPONSE_HEADER_SIZE){
        AESM_DBG_ERROR("ProvMsg2 size too small");
        return PVE_MSG_ERROR;
    }
    if(msg3_size <PROVISION_REQUEST_HEADER_SIZE){
        AESM_DBG_ERROR("Input ProvMsg3 buffer too small");
        return PVE_INSUFFICIENT_MEMORY_ERROR;
    }

    ret = check_prov_msg2_header(msg2_header, msg2_size);//process message header
    if( AE_SUCCESS != ret){
        AESM_DBG_ERROR("Fail to decode ProvMsg2:%d",ret);
        return ret;
    }
    ret = check_epid_pve_pg_status_before_mac_verification(msg2_header);
    if( AE_SUCCESS != ret){
        AESM_DBG_ERROR("Backend server reported error in ProvMsg2:%d",ret);
        return ret;
    }

    if( 0!=memcpy_s(aad, sizeof(aad), msg2_header, PROVISION_RESPONSE_HEADER_SIZE)){
        AESM_DBG_FATAL("memcpy error");
        return PVE_UNEXPECTED_ERROR;
    }

    do{
        TLVsMsg tlvs_msg2;
        tlv_status_t tlv_status;
        //decode TLV structure of message body
        tlv_status= tlvs_msg2.init_from_buffer(msg2+static_cast<uint32_t>(PROVISION_RESPONSE_HEADER_SIZE), msg2_size - static_cast<uint32_t>(PROVISION_RESPONSE_HEADER_SIZE));
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("Fail to decode ProvMsg2:%d",ret);
            break;
        }
        ret = msg2_integrity_checking(tlvs_msg2);//checking and verifying that TLV structure is correct and version is supported
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("ProvMsg2 integrity checking error:%d",ret);
            break;
        }
        prov_get_ek2_input_t ek2_input;
        if(memcpy_s(ek2_input.nonce, NONCE_SIZE, MSG2_TOP_FIELD_NONCE.payload, NONCE_SIZE)!=0){
            AESM_DBG_FATAL("memcpy error");
            ret = PVE_UNEXPECTED_ERROR;
            break;
        }
        if(memcpy_s(ek2_input.xid, XID_SIZE, msg2_header->xid, XID_SIZE)!=0){
            AESM_DBG_FATAL("memcpy error");
            ret = PVE_UNEXPECTED_ERROR;
            break;
        }
        //call PvE to get EK2
        se_static_assert(SK_SIZE == sizeof(prov_get_ek2_output_t));
        ret = static_cast<ae_error_t>(get_ek2(&ek2_input, reinterpret_cast<prov_get_ek2_output_t *>(ek2)));
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to get EK2:%d",ret);
            break;
        }
        if(tlvs_msg2.get_tlv_count() == MSG2_TOP_FIELDS_COUNT_WITH_SIGRL){//RLver and gid is added as part of AAD if available
            sigrl = reinterpret_cast<const se_sig_rl_t *>(MSG2_TOP_FIELD_SIGRL.payload);
            if(0!=memcpy_s(aad+PROVISION_RESPONSE_HEADER_SIZE, sizeof(RLver_t), &sigrl->sig_rl.RLver, sizeof(RLver_t))){
                AESM_DBG_FATAL("memcpy error");
                ret = PVE_UNEXPECTED_ERROR;
                break;
            }
            if(0!=memcpy_s(aad+PROVISION_RESPONSE_HEADER_SIZE+sizeof(RLver_t), sizeof(GroupID), &sigrl->sig_rl.gid, sizeof(GroupID))){
                AESM_DBG_FATAL("memcpy error");
                ret = PVE_UNEXPECTED_ERROR;
                break;
            }
            aad_size += sizeof(RLver_t)+sizeof(GroupID);
            sigrl_size = MSG2_TOP_FIELD_SIGRL.size;
        }
        se_static_assert(SK_SIZE==sizeof(sgx_aes_gcm_128bit_key_t));
        tlv_msg_t field1 = block_cipher_tlv_get_encrypted_text(MSG2_TOP_FIELD_DATA);
        decoded_msg2 = reinterpret_cast<uint8_t *>(malloc(field1.msg_size));
        if(NULL == decoded_msg2){
            AESM_DBG_ERROR("malloc error");
            ret= AE_OUT_OF_MEMORY_ERROR;
            break;
        }
        //decrypt ProvMsg2 by EK2
        sgx_status_t sgx_status = sgx_rijndael128GCM_decrypt(reinterpret_cast<const sgx_aes_gcm_128bit_key_t *>(ek2),
            field1.msg_buf, field1.msg_size, decoded_msg2, 
            reinterpret_cast<uint8_t *>(block_cipher_tlv_get_iv(MSG2_TOP_FIELD_DATA)), IV_SIZE, 
            aad, static_cast<uint32_t>(aad_size), reinterpret_cast<const sgx_aes_gcm_128bit_tag_t *>(MSG2_TOP_FIELD_MAC.payload));
        if(SGX_ERROR_MAC_MISMATCH == sgx_status){
            AESM_DBG_ERROR("Fail to decrypt ProvMsg2 body by EK2");
            ret = PVE_INTEGRITY_CHECK_ERROR;
            break;
        }
        if( AE_SUCCESS != (ret = sgx_error_to_ae_error(sgx_status))){
            AESM_DBG_ERROR("error in decrypting ProvMsg2 body:%d",sgx_status);
            break;
        }

        ret = check_epid_pve_pg_status_after_mac_verification(msg2_header);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Backend server reported error in ProvMsg2 passed MAC verification:%d",ret);
            break;
        }
        TLVsMsg tlvs_field1;
        tlv_status = tlvs_field1.init_from_buffer(decoded_msg2, field1.msg_size);//decode TLV structure of field1 of ProvMsg2
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("Fail to decode field1 of ProvMsg2:%d",tlv_status);
            break;
        }
        proc_prov_msg2_blob_input_t msg2_blob_input;
        ret = msg2_field1_msg_check_copy(tlvs_field1, msg2_blob_input, pek);//version/type checking to verify message format
        if( AE_SUCCESS != ret){
            AESM_DBG_ERROR("field1 of ProvMsg2 checking error:%d",ret);
            break;
        }
        gen_prov_msg3_output_t msg3_fixed_output;
        memset(&msg3_fixed_output, 0, sizeof(msg3_fixed_output));
        //collect old epid blob
        if(epid_blob==NULL){
            memset(msg2_blob_input.old_epid_data_blob, 0, HARD_CODED_EPID_BLOB_SIZE);
        }else if(blob_size!=HARD_CODED_EPID_BLOB_SIZE){
            AESM_DBG_FATAL("epid blob internal size error");
            ret = PVE_UNEXPECTED_ERROR;
            break;
        }else{
#ifdef DBG_LOG
            {
                char dbg_str[256];
                aesm_dbg_format_hex(reinterpret_cast<const uint8_t *>(&epid_blob), blob_size, dbg_str, 256);
                AESM_DBG_TRACE("old epid blob=%s",dbg_str);
            }
#endif
            if(0!=memcpy_s(msg2_blob_input.old_epid_data_blob, HARD_CODED_EPID_BLOB_SIZE, epid_blob, blob_size)){
                AESM_DBG_FATAL("memcpy error");
                ret = PVE_UNEXPECTED_ERROR;
                break;
            }
        }
        uint32_t epid_sig_output_size = estimate_epid_sig_size(sigrl_size) + MAX_TLV_HEADER_SIZE;
        epid_sig = reinterpret_cast<uint8_t *>(malloc(epid_sig_output_size));
        if(NULL == epid_sig){
            AESM_DBG_ERROR("malloc error");
            ret = AE_OUT_OF_MEMORY_ERROR;
            break;
        }
        ret = (ae_error_t)proc_prov_msg2_data(&msg2_blob_input, reinterpret_cast<const uint8_t *>(sigrl), sigrl_size, 
            &msg3_fixed_output, epid_sig, epid_sig_output_size);//ecall to process msg2 data and generate msg3 data in PvE
        if( PVE_EPIDBLOB_ERROR == ret){
            if(previous_psvn == NULL){
                AESM_DBG_ERROR("PvE requires previous PSVN but it is not provided");
                ret = PVE_PARAMETER_ERROR;
                break;
            }else{//output previous svn in correspondent to sigrl in epid blob error
                if(0!=memcpy_s(previous_psvn, sizeof(psvn_t), &msg2_blob_input.previous_psvn, sizeof(psvn_t))){
                    AESM_DBG_FATAL("memcpy error");
                    ret = PVE_UNEXPECTED_ERROR;
                    break;
                }
            }
        }
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("PvE report error %d in processing ProvMsg2",ret);
            break;
        }
        uint8_t iv[IV_SIZE];
        uint8_t mac[MAC_SIZE];
        uint8_t *payload_data=NULL;
        uint32_t payload_size = 0;
        ret = aesm_read_rand(iv, IV_SIZE);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("fail to generate random number:%d",ret);
            break;
        }
        //Now start to generate ProvMsg3
        ret = gen_msg3_header(msg3_fixed_output, ek2_input.xid, msg3_header,msg3_size);//first generate header
        if( AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to generate ProvMsg3 Header:%d",ret);
            break;
        }
        TLVsMsg tlvs_msg3;
        tlv_status = tlvs_msg3.add_nonce(ek2_input.nonce, NONCE_SIZE);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("Fail to generate Nonce TLV in ProvMsg3:%d",tlv_status);
            break;
        }
        if(msg3_fixed_output.is_join_proof_generated){
            payload_data = msg3_fixed_output.field1_data;
            payload_size = static_cast<uint32_t>(HARD_CODED_JOIN_PROOF_WITH_ESCROW_TLV_SIZE);
        }
        TLVsMsg tlvs_m3field1;
        tlv_status = tlvs_m3field1.add_block_cipher_text(msg3_fixed_output.field1_iv, payload_data, payload_size);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to generate Field3.1 TLV in ProvMsg3:%d",tlv_status);
            break;
        }
        tlv_status = tlvs_m3field1.add_mac(msg3_fixed_output.field1_mac);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to generate Field3.2 TLV in ProvMsg3:%d",tlv_status);
            break;
        }
        encrypted_field1 = reinterpret_cast<uint8_t *>(malloc(tlvs_m3field1.get_tlv_msg_size()));
        if( NULL == encrypted_field1){
            AESM_DBG_ERROR("malloc error");
            ret = AE_OUT_OF_MEMORY_ERROR;
            break;
        }
        //encrypt field1 using ek2 as key
        sgx_status = sgx_rijndael128GCM_encrypt(reinterpret_cast<const sgx_aes_gcm_128bit_key_t *>(ek2),
            tlvs_m3field1.get_tlv_msg(), tlvs_m3field1.get_tlv_msg_size(), encrypted_field1, 
            iv, IV_SIZE, reinterpret_cast<uint8_t *>(msg3_header), PROVISION_REQUEST_HEADER_SIZE,
            reinterpret_cast<sgx_aes_gcm_128bit_tag_t *>(mac));
        if(AE_SUCCESS != (ret = sgx_error_to_ae_error(sgx_status))){
            AESM_DBG_ERROR("fail to encrypting ProvMsg3 body by ek2:%d",sgx_status);
            break;
        }
        tlv_status = tlvs_msg3.add_block_cipher_text(iv, encrypted_field1, tlvs_m3field1.get_tlv_msg_size());
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to create Field1 TLV of ProvMsg3:%d",tlv_status);
            break;
        }
        ret = tlv_error_2_pve_error(tlvs_msg3.add_mac(mac));
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to create Field2 TLV of ProvMsg3:%d",ret);
            break;
        }
        if(msg3_fixed_output.is_epid_sig_generated){
            tlv_status = tlvs_msg3.add_block_cipher_text(msg3_fixed_output.epid_sig_iv, epid_sig, msg3_fixed_output.epid_sig_output_size);
            ret = tlv_error_2_pve_error(tlv_status);
            if(AE_SUCCESS != ret){
                AESM_DBG_ERROR("Fail to create Field3 TLV of ProvMsg3:%d",tlv_status);
                break;
            }
            tlv_status = tlvs_msg3.add_mac(msg3_fixed_output.epid_sig_mac);
            ret = tlv_error_2_pve_error(tlv_status);
            if(AE_SUCCESS != ret){
                AESM_DBG_ERROR("Fail to create Field4 TLV of ProvMsg3:%d",tlv_status);
                break;
            }
        }
        assert( tlvs_msg3.get_tlv_msg_size() <= msg3_size - PROVISION_REQUEST_HEADER_SIZE);//The checking should have been done in header generation

        if(0!=memcpy_s(msg3+PROVISION_REQUEST_HEADER_SIZE, msg3_size-PROVISION_REQUEST_HEADER_SIZE,
            tlvs_msg3.get_tlv_msg(), tlvs_msg3.get_tlv_msg_size())){
                AESM_DBG_FATAL("memcpy error");
                ret = PVE_UNEXPECTED_ERROR;
                break;
        }
        AESM_DBG_TRACE("ProvMsg3 generated successfully");
        ret = AE_SUCCESS;
    }while(0);
    if(decoded_msg2)free(decoded_msg2);
    if(encrypted_field1) free(encrypted_field1);
    if(epid_sig)free(epid_sig);
    return ret;
}
