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

#include "type_length_value.h"
#include "epid_utility.h"
#include "aesm_xegd_blob.h"
#include "aeerror.h"
#include "PVEClass.h"
#include "PCEClass.h"
#include "aesm_rand.h"

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
#define MSG2_FIELD1_MAX_COUNT   6
#define MSG2_FIELD1_MIN_COUNT   4
#define MSG2_FIELD1_GROUP_CERT  tlvs_field1[0]
#define MSG2_FIELD1_NONCE       tlvs_field1[2]
#define MSG2_FIELD1_PSID        tlvs_field1[1]
#define MSG2_FIELD1_PREV_PI     tlvs_field1[alt_index+1]
#define PREV_GID_INDEX          (alt_index+2)
#define MSG2_FIELD1_PREV_GID    tlvs_field1[PREV_GID_INDEX]
#define MSG2_FIELD1_PLAT_INFO   tlvs_field1[alt_index+3]


//Function to verify that EPID SigRl type and version is correct for sigrl
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
    if(MSG2_TOP_FIELD_NONCE.header_size != SMALL_TLV_HEADER_SIZE)//Requires NONCE to be small header size
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

//check the format of msg2_field1 and copy correspondent data to msg2_blob_input
static ae_error_t msg2_field1_msg_check_copy(const TLVsMsg& tlvs_field1, proc_prov_msg2_blob_input_t& msg2_blob_input, const signed_pek_t& pek)
{
    uint32_t tlv_count = tlvs_field1.get_tlv_count();
    uint32_t alt_index = 2;
    msg2_blob_input.is_previous_pi_provided = false;

    if(tlv_count == MSG2_FIELD1_MAX_COUNT){//EPID_PSVN TLV is available
        msg2_blob_input.is_previous_pi_provided = true;
        if(MSG2_FIELD1_PREV_PI.type != TLV_PLATFORM_INFO ||
            MSG2_FIELD1_PREV_PI.size != sizeof(bk_platform_info_t)){
            return PVE_MSG_ERROR;
        }
        if(MSG2_FIELD1_PREV_PI.version != TLV_VERSION_1){
            return PVE_MSG_ERROR;
        }
        if(MSG2_FIELD1_PREV_PI.header_size != SMALL_TLV_HEADER_SIZE){
            return PVE_MSG_ERROR;
        }
        if(MSG2_FIELD1_PREV_GID.type != TLV_EPID_GID ||
            MSG2_FIELD1_PREV_GID.size != sizeof(GroupId)){
            return PVE_MSG_ERROR;
        }
        if(MSG2_FIELD1_PREV_GID.version != TLV_VERSION_1){
            return PVE_MSG_ERROR;
        }
        if(MSG2_FIELD1_PREV_GID.header_size != SMALL_TLV_HEADER_SIZE){
            return PVE_MSG_ERROR;
        }
        if(0!=memcpy_s(&msg2_blob_input.previous_gid, sizeof(msg2_blob_input.previous_gid), MSG2_FIELD1_PREV_GID.payload, MSG2_FIELD1_PREV_GID.size)){
            return PVE_UNEXPECTED_ERROR;
        }
        if(0!=memcpy_s(&msg2_blob_input.previous_pi, sizeof(msg2_blob_input.previous_pi), MSG2_FIELD1_PREV_PI.payload, MSG2_FIELD1_PREV_PI.size)){
            return PVE_UNEXPECTED_ERROR;
        }
    }else if(tlv_count!=MSG2_FIELD1_MIN_COUNT){
        return PVE_MSG_ERROR;//make sure number of TLVs are correct
    }else{
        alt_index=0;
    }

    if(MSG2_FIELD1_GROUP_CERT.type != TLV_EPID_GROUP_CERT||
        MSG2_FIELD1_GROUP_CERT.version != TLV_VERSION_1 ||
        MSG2_FIELD1_GROUP_CERT.size != sizeof(signed_epid_group_cert_t)||
        MSG2_FIELD1_GROUP_CERT.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_MSG_ERROR;
    if(MSG2_FIELD1_PSID.type != TLV_PS_ID ||
        MSG2_FIELD1_PSID.version != TLV_VERSION_1 ||
        MSG2_FIELD1_PSID.size != sizeof(psid_t)||
        MSG2_FIELD1_PSID.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_MSG_ERROR;
    if(MSG2_FIELD1_NONCE.type != TLV_NONCE||
        MSG2_FIELD1_NONCE.version != TLV_VERSION_1 ||
        MSG2_FIELD1_NONCE.size != CHALLENGE_NONCE_SIZE||
        MSG2_FIELD1_NONCE.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_MSG_ERROR;
    if(MSG2_FIELD1_PLAT_INFO.type != TLV_PLATFORM_INFO ||
        MSG2_FIELD1_PLAT_INFO.version != TLV_VERSION_1 ||
        MSG2_FIELD1_PLAT_INFO.size != sizeof(bk_platform_info_t)||
        MSG2_FIELD1_PLAT_INFO.header_size != SMALL_TLV_HEADER_SIZE)
        return PVE_MSG_ERROR;
    sgx_sha256_hash_t psid_hash;
    ae_error_t ret = sgx_error_to_ae_error(sgx_sha256_msg(reinterpret_cast<const uint8_t *>(&pek.n), static_cast<uint32_t>(sizeof(pek.n)+sizeof(pek.e)), &psid_hash));
    if(AE_SUCCESS != ret)
        return ret;
    if(0!=memcmp(&psid_hash, MSG2_FIELD1_PSID.payload, sizeof(psid_hash)))//PSID does not match
        return PVE_MSG_ERROR;
    bk_platform_info_t *d2 = (bk_platform_info_t *)MSG2_FIELD1_PLAT_INFO.payload;

    if(0!=memcpy_s(&msg2_blob_input.group_cert, sizeof(msg2_blob_input.group_cert), MSG2_FIELD1_GROUP_CERT.payload, MSG2_FIELD1_GROUP_CERT.size)||
        0!=memcpy_s(&msg2_blob_input.challenge_nonce, sizeof(msg2_blob_input.challenge_nonce), MSG2_FIELD1_NONCE.payload, MSG2_FIELD1_NONCE.size)||
        0!=memcpy_s(&msg2_blob_input.equiv_pi, sizeof(msg2_blob_input.equiv_pi), d2,sizeof(*d2))){
            return PVE_UNEXPECTED_ERROR;
    }
    return AE_SUCCESS;
}

//Function to check message header of ProvMsg2 to determine whether it is valid
//@msg2_header, input the message header of ProvMsg2
//@msg2_size, size of ProvMsg2, in bytes
//@return AE_SUCCESS if the message header is valid ProvMsg2 or error code if there're any problems
static ae_error_t check_prov_msg2_header(const provision_response_header_t *msg2_header, uint32_t msg2_size)
{
    if(msg2_header->protocol != SE_EPID_PROVISIONING || msg2_header->type != TYPE_PROV_MSG2 ||
        msg2_header->version != TLV_VERSION_2){
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
    uint32_t sigrl_extra_size = static_cast<uint32_t>(sizeof(se_sig_rl_t)-sizeof(SigRlEntry)+2*ECDSA_SIGN_SIZE);
    if(sigrl_size ==sigrl_extra_size || sigrl_size == 0){//sigrl_size==0 is special cases that no sigrl provided
        //Add the TLV Header size
        return static_cast<uint32_t>(sizeof(EpidSignature)-sizeof(NrProof)+MAX_TLV_HEADER_SIZE);
    }else if (sigrl_size < sigrl_extra_size){
        //Invalid sigrl size
        return 0;
    }else{
        sigrl_body_size = sigrl_size - sigrl_extra_size;
        uint64_t entry_count = sigrl_body_size/sizeof(SigRlEntry);
        uint64_t total_size = sizeof(EpidSignature)-sizeof(NrProof)+sizeof(NrProof)*entry_count+MAX_TLV_HEADER_SIZE;
        if(total_size > UINT32_MAX){
            return 0;
        }
        return static_cast<uint32_t>(total_size);
    }
}

static ae_error_t gen_msg3_header(const gen_prov_msg3_output_t& msg3_output, const uint8_t xid[XID_SIZE], provision_request_header_t *msg3_header, uint32_t& msg3_size)
{
    msg3_header->protocol = SE_EPID_PROVISIONING;
    msg3_header->version = TLV_VERSION_2;
    msg3_header->type = TYPE_PROV_MSG3;
    size_t field1_size = 0;
    if(msg3_output.is_join_proof_generated){
        field1_size = BLOCK_CIPHER_TEXT_TLV_SIZE(HARD_CODED_JOIN_PROOF_WITH_ESCROW_TLV_SIZE)+MAC_TLV_SIZE(MAC_SIZE);
    }else{
        // BLOCK_CIPHER_TEXT_TLV_SIZE(0) is needed because IV need to be included for the following MAC tlv
        field1_size = BLOCK_CIPHER_TEXT_TLV_SIZE(0)+MAC_TLV_SIZE(MAC_SIZE);
    }
    field1_size+=NONCE_TLV_SIZE(NONCE_2_SIZE)+CIPHER_TEXT_TLV_SIZE(RSA_3072_KEY_BYTES)+SE_REPORT_TLV_SIZE();
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
    if(0!=memcpy_s(msg3_header->xid, sizeof(msg3_header->xid), xid, XID_SIZE))
        return PVE_UNEXPECTED_ERROR; //copy transaction id of ProvMsg2
    msg3_size = static_cast<uint32_t>(total_body_size + PROVISION_REQUEST_HEADER_SIZE);
    return AE_SUCCESS;
}

//Function to decode ProvMsg2 and generate ProvMsg3
//@data: global structure used to store pve relative data
//@msg2: ProvMsg2
//@msg2_size: size of ProvMsg2
//@epid_blob: input an optional old epid data blob used to generate non-revoke proof if required
//@blob_size: size of the epid blob
//@msg3: output buffer for ProvMsg3
//@msg3_size: input the size of buffer msg3, in byte
//@return AE_SUCCESS on success and error code on failure
uint32_t CPVEClass::proc_prov_msg2(
        pve_data_t &data,
        const uint8_t *msg2,
        uint32_t msg2_size,
        const uint8_t *epid_blob,
        uint32_t blob_size,
        uint8_t *msg3,
        uint32_t msg3_size)
{
    ae_error_t ret = AE_SUCCESS;
    const se_sig_rl_t *sigrl = NULL;
    uint32_t sigrl_size = 0;
    uint8_t *epid_sig = NULL;
    uint8_t *decoded_msg2 = NULL;
    uint8_t *encrypted_field1 = NULL;
    sgx_status_t sgx_status;
    uint8_t aad[PROVISION_RESPONSE_HEADER_SIZE+sizeof(RLver_t)+sizeof(GroupId)];
    size_t aad_size = PROVISION_RESPONSE_HEADER_SIZE;
    const provision_response_header_t *msg2_header = reinterpret_cast<const provision_response_header_t *>(msg2);
    provision_request_header_t *msg3_header = reinterpret_cast<provision_request_header_t *>(msg3);
    if(msg2_size < PROVISION_RESPONSE_HEADER_SIZE){
        AESM_DBG_ERROR("ProvMsg2 size too small");
        return PVE_MSG_ERROR;
    }
    if (epid_blob != NULL && blob_size != SGX_TRUSTED_EPID_BLOB_SIZE_SDK){
        AESM_DBG_FATAL("epid blob size error");
        return PVE_UNEXPECTED_ERROR;
    }
    if(msg3_size <PROVISION_REQUEST_HEADER_SIZE){
        AESM_DBG_ERROR("Input ProvMsg3 buffer too small");
        return PVE_INSUFFICIENT_MEMORY_ERROR;
    }

    ret = check_prov_msg2_header(msg2_header, msg2_size);//process message header
    if( AE_SUCCESS != ret){
        AESM_DBG_ERROR("Fail to decode ProvMsg2:(ae%d)",ret);
        return ret;
    }
    if ( 0!=memcmp(msg2_header->xid, data.xid, XID_SIZE) ){
        AESM_DBG_ERROR("unmatched xid in ProvMsg2 header");
        return AE_FAILURE;
    }
    ret = check_epid_pve_pg_status_before_mac_verification(msg2_header);
    if( AE_SUCCESS != ret){
        AESM_DBG_ERROR("Backend server reported error in ProvMsg2:(ae%d)",ret);
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
            AESM_DBG_ERROR("Fail to decode ProvMsg2:(ae%d)",ret);
            break;
        }
        ret = msg2_integrity_checking(tlvs_msg2);//checking and verifying that TLV structure is correct and version is supported
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("ProvMsg2 integrity checking error:(ae%d)",ret);
            break;
        }
        sgx_aes_gcm_128bit_key_t ek2;
        uint8_t temp[NONCE_SIZE+XID_SIZE];
        if(0!=memcpy_s(temp, sizeof(temp), data.xid, sizeof(data.xid))||
            0!=memcpy_s(temp+XID_SIZE, sizeof(temp)-XID_SIZE, MSG2_TOP_FIELD_NONCE.payload, MSG2_TOP_FIELD_NONCE.size)){
                AESM_DBG_ERROR("memcpy error");
                ret = AE_FAILURE;
                break;
        }
        se_static_assert(sizeof(sgx_cmac_128bit_key_t)==SK_SIZE);
        if((sgx_status=sgx_rijndael128_cmac_msg(reinterpret_cast<const sgx_cmac_128bit_key_t *>(data.sk),
            temp, XID_SIZE+NONCE_SIZE, &ek2))!=SGX_SUCCESS){
                AESM_DBG_ERROR("Fail to generate ek2:(sgx 0x%x)",sgx_status);
                ret = AE_FAILURE;
                break;
        }

        if(tlvs_msg2.get_tlv_count() == MSG2_TOP_FIELDS_COUNT_WITH_SIGRL){//sigrl version and gid is added as part of AAD if available
            sigrl = reinterpret_cast<const se_sig_rl_t *>(MSG2_TOP_FIELD_SIGRL.payload);
            if(0!=memcpy_s(aad+PROVISION_RESPONSE_HEADER_SIZE, sizeof(RLver_t), &sigrl->sig_rl.version, sizeof(RLver_t))){
                AESM_DBG_FATAL("memcpy error");
                ret = PVE_UNEXPECTED_ERROR;
                break;
            }
            if(0!=memcpy_s(aad+PROVISION_RESPONSE_HEADER_SIZE+sizeof(RLver_t), sizeof(aad)- PROVISION_RESPONSE_HEADER_SIZE-sizeof(RLver_t), &sigrl->sig_rl.gid, sizeof(sigrl->sig_rl.gid))){
                AESM_DBG_FATAL("memcpy error");
                ret = PVE_UNEXPECTED_ERROR;
                break;
            }
            aad_size += sizeof(RLver_t)+sizeof(GroupId);
            sigrl_size = MSG2_TOP_FIELD_SIGRL.size;
        }
        se_static_assert(SK_SIZE==sizeof(sgx_aes_gcm_128bit_key_t));
        tlv_msg_t field1 = block_cipher_tlv_get_encrypted_text(MSG2_TOP_FIELD_DATA);
        decoded_msg2 = static_cast<uint8_t *>(malloc(field1.msg_size));
        if(NULL == decoded_msg2){
            AESM_DBG_ERROR("malloc error");
            ret= AE_OUT_OF_MEMORY_ERROR;
            break;
        }
        //decrypt ProvMsg2 by EK2
        sgx_status = sgx_rijndael128GCM_decrypt(&ek2,
            field1.msg_buf, field1.msg_size, decoded_msg2,
            reinterpret_cast<uint8_t *>(block_cipher_tlv_get_iv(MSG2_TOP_FIELD_DATA)), IV_SIZE,
            aad, static_cast<uint32_t>(aad_size), reinterpret_cast<const sgx_aes_gcm_128bit_tag_t *>(MSG2_TOP_FIELD_MAC.payload));
        if(SGX_ERROR_MAC_MISMATCH == sgx_status){
            AESM_DBG_ERROR("Fail to decrypt ProvMsg2 body by EK2 (sgx0x%x)",sgx_status);
            ret = PVE_INTEGRITY_CHECK_ERROR;
            break;
        }
        if( AE_SUCCESS != (ret = sgx_error_to_ae_error(sgx_status))){
            AESM_DBG_ERROR("error in decrypting ProvMsg2 body:(sgx0x%x)",sgx_status);
            break;
        }

        ret = check_epid_pve_pg_status_after_mac_verification(msg2_header);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Backend server reported error in ProvMsg2 passed MAC verification:(ae%d)",ret);
            break;
        }
        TLVsMsg tlvs_field1;
        tlv_status = tlvs_field1.init_from_buffer(decoded_msg2, field1.msg_size);//decode TLV structure of field1 of ProvMsg2
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("Fail to decode field1 of ProvMsg2:(ae%d)",ret);
            break;
        }
        proc_prov_msg2_blob_input_t msg2_blob_input;
        memset(&msg2_blob_input, 0, sizeof(msg2_blob_input));
        ret = CPCEClass::instance().load_enclave();//Load PCE enclave now
        if( ret != AE_SUCCESS){
            AESM_DBG_ERROR("Fail to load PCE enclave:(ae%d)\n",ret);
            break;
        }
        ret = (ae_error_t)CPCEClass::instance().get_pce_target(&msg2_blob_input.pce_target_info);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("fail to get PCE target info:(ae%d)\n",ret);
            break;
        }
        ret = msg2_field1_msg_check_copy(tlvs_field1, msg2_blob_input, data.pek);//version/type checking to verify message format
        if( AE_SUCCESS != ret){
            AESM_DBG_ERROR("field1 of ProvMsg2 checking error:( ae%d)",ret);
            break;
        }
        gen_prov_msg3_output_t msg3_fixed_output;
        memset(&msg3_fixed_output, 0, sizeof(msg3_fixed_output));
        //collect old epid blob
        if(epid_blob==NULL){
            memset(msg2_blob_input.old_epid_data_blob, 0, SGX_TRUSTED_EPID_BLOB_SIZE_SDK);
        }else{
#ifdef DBG_LOG
            {
                char dbg_str[256];
                aesm_dbg_format_hex(epid_blob, blob_size, dbg_str, 256);
                AESM_DBG_TRACE("old epid blob=%s",dbg_str);
            }
#endif
            if (0 != memcpy_s(msg2_blob_input.old_epid_data_blob, sizeof(msg2_blob_input.old_epid_data_blob), epid_blob, blob_size)){
                AESM_DBG_FATAL("memcpy error");
                ret = PVE_UNEXPECTED_ERROR;
                break;
            }
        }
        if(0!=memcpy_s(&msg2_blob_input.pek,sizeof(msg2_blob_input.pek), &data.pek, sizeof(data.pek))){
            AESM_DBG_ERROR("memcpy error");
            ret = AE_FAILURE;
            break;
        }
        if (AE_SUCCESS != (ret = XEGDBlob::instance().read(msg2_blob_input.xegb))){
            AESM_DBG_ERROR("Fail to read extend epid group blob info ");
            return ret;
        }

        uint32_t epid_sig_output_size = estimate_epid_sig_size(sigrl_size);
        //estimate_epid_sig_size(sigrl_size)=0, which means the sigrl is invalid.
        if(epid_sig_output_size == 0){
            AESM_DBG_ERROR("Invalid SIGRL size %d", sigrl_size);
            ret = PVE_MSG_ERROR;
            break;
        }
        epid_sig = static_cast<uint8_t *>(malloc(epid_sig_output_size));
        if(NULL == epid_sig){
            AESM_DBG_ERROR("malloc error");
            ret = AE_OUT_OF_MEMORY_ERROR;
            break;
        }
        ret = CPVEClass::instance().load_enclave();//Load PvE enclave now
        if( ret != AE_SUCCESS){
            AESM_DBG_ERROR("Fail to load PvE enclave:(ae%d)\n",ret);
            break;
        }
        ret = (ae_error_t)proc_prov_msg2_data(&msg2_blob_input, data.is_performance_rekey, reinterpret_cast<const uint8_t *>(sigrl), sigrl_size,
            &msg3_fixed_output, epid_sig, epid_sig_output_size);//ecall to process msg2 data and generate msg3 data in PvE
        if( PVE_EPIDBLOB_ERROR == ret){
            if(0!=memcpy_s(&data.bpi, sizeof(data.bpi), &msg2_blob_input.previous_pi, sizeof(msg2_blob_input.previous_pi))){
                AESM_DBG_FATAL("memcpy error");
                ret = PVE_UNEXPECTED_ERROR;
                break;

            }
        }
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("PvE report error (ae%d) in processing ProvMsg2",ret);
            break;
        }
        uint8_t ecdsa_sign[64];
        psvn_t psvn;
        memset(&psvn, 0, sizeof(psvn));
        if(0!=memcpy_s(&psvn.cpu_svn,sizeof(psvn.cpu_svn), &msg2_blob_input.equiv_pi.cpu_svn, sizeof(msg2_blob_input.equiv_pi.cpu_svn))||
            0!=memcpy_s(&psvn.isv_svn, sizeof(psvn.isv_svn), &msg2_blob_input.equiv_pi.pce_svn, sizeof(msg2_blob_input.equiv_pi.pce_svn))){
                ret = PVE_UNEXPECTED_ERROR;
                break;
        }
        ret = CPCEClass::instance().load_enclave();//Load PCE enclave now
        if( ret != AE_SUCCESS){
            AESM_DBG_ERROR("Fail to load PCE enclave:(ae%d)\n",ret);
            break;
        }
        ret = (ae_error_t)CPCEClass::instance().sign_report(psvn, msg3_fixed_output.pwk2_report, ecdsa_sign);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("PCE report error (ae%d) in sign report",ret);
            break;
        }
        CPCEClass::instance().unload_enclave();
        uint8_t iv[IV_SIZE];
        uint8_t mac[MAC_SIZE];
        uint8_t *payload_data=NULL;
        uint32_t payload_size = 0;
        ret = aesm_read_rand(iv, IV_SIZE);
        if(AE_SUCCESS != ret){

            AESM_DBG_ERROR("fail to generate random number:(ae%d)",ret);
            break;
        }
        //Now start to generate ProvMsg3
        ret = gen_msg3_header(msg3_fixed_output, data.xid, msg3_header,msg3_size);//first generate header
        if( AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to generate ProvMsg3 Header:(ae%d)",ret);
            break;
        }
        TLVsMsg tlvs_msg3;
        tlv_status = tlvs_msg3.add_nonce(MSG2_TOP_FIELD_NONCE.payload, NONCE_SIZE);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS!=ret){
            AESM_DBG_ERROR("Fail to generate Nonce TLV in ProvMsg3:(ae%d)",ret);
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
            AESM_DBG_ERROR("Fail to generate Field3.1 TLV in ProvMsg3:(ae%d)", ret);
            break;
        }
        tlv_status = tlvs_m3field1.add_mac(msg3_fixed_output.field1_mac);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to generate Field3.2 TLV in ProvMsg3:(ae%d)",ret);
            break;
        }
        tlv_status = tlvs_m3field1.add_nonce(msg3_fixed_output.n2, NONCE_2_SIZE);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to generate Field3.3 NONCE TLV  N2 in ProvMsg3:(ae %d)",ret);
            break;
        }
        tlv_status = tlvs_m3field1.add_cipher_text(msg3_fixed_output.encrypted_pwk2, RSA_3072_KEY_BYTES, PEK_3072_PUB);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to generate Field3.4 SE Report TLV  in ProvMsg3:(ae %d)",ret);
            break;
        }
        tlv_status = tlvs_m3field1.add_pce_report_sign(msg3_fixed_output.pwk2_report.body, ecdsa_sign);
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to generate Field3.5 PCE Report Sign TLV  in ProvMsg3:(ae %d)",ret);
            break;
        }
        encrypted_field1 = static_cast<uint8_t *>(malloc(tlvs_m3field1.get_tlv_msg_size()));
        if( NULL == encrypted_field1){
            AESM_DBG_ERROR("malloc error");
            ret = AE_OUT_OF_MEMORY_ERROR;
            break;
        }
        //encrypt field1 using ek2 as key

        sgx_status = sgx_rijndael128GCM_encrypt(&ek2,
            tlvs_m3field1.get_tlv_msg(), tlvs_m3field1.get_tlv_msg_size(), encrypted_field1,
            iv, IV_SIZE, reinterpret_cast<uint8_t *>(msg3_header), PROVISION_REQUEST_HEADER_SIZE,
            reinterpret_cast<sgx_aes_gcm_128bit_tag_t *>(mac));
        if(AE_SUCCESS != (ret = sgx_error_to_ae_error(sgx_status))){
            AESM_DBG_ERROR("fail to encrypt ProvMsg3 body by ek2:(sgx0x%x)",sgx_status);
            break;
        }
        tlv_status = tlvs_msg3.add_block_cipher_text(iv, encrypted_field1, tlvs_m3field1.get_tlv_msg_size());
        ret = tlv_error_2_pve_error(tlv_status);
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to create Field1 TLV of ProvMsg3:(ae%d)",ret);
            break;
        }
        ret = tlv_error_2_pve_error(tlvs_msg3.add_mac(mac));
        if(AE_SUCCESS != ret){
            AESM_DBG_ERROR("Fail to create Field2 TLV of ProvMsg3:(ae%d)",ret);
            break;
        }
        if(msg3_fixed_output.is_epid_sig_generated){
            tlv_status = tlvs_msg3.add_block_cipher_text(msg3_fixed_output.epid_sig_iv, epid_sig, msg3_fixed_output.epid_sig_output_size);
            ret = tlv_error_2_pve_error(tlv_status);
            if(AE_SUCCESS != ret){
                AESM_DBG_ERROR("Fail to create Field3 TLV of ProvMsg3:(ae%d)",ret);
                break;
            }
            tlv_status = tlvs_msg3.add_mac(msg3_fixed_output.epid_sig_mac);
            ret = tlv_error_2_pve_error(tlv_status);
            if(AE_SUCCESS != ret){
                AESM_DBG_ERROR("Fail to create Field4 TLV of ProvMsg3:(ae%d)",ret);
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
